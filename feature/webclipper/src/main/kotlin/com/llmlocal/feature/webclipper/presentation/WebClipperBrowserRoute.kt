package com.llmlocal.feature.webclipper.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llmlocal.feature.webclipper.data.WebViewHolder
import com.llmlocal.feature.webclipper.domain.ClipSink
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.feature.webclipper.presentation.mvi.BrowserEffect
import com.llmlocal.feature.webclipper.presentation.mvi.BrowserIntent
import com.llmlocal.feature.webclipper.presentation.ui.WebClipperBrowserScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Entry point for the in-app browser: URL bar, WebView, clip button and the
 * candidate / nothing-detected sheets. Owns its own `Scaffold`.
 *
 * Creates [BrowserViewModel] via `koinViewModel { parametersOf(config, labels, initialUrl) }`,
 * attaches the shared [WebViewHolder] WebView (events → intents, effects →
 * WebView commands) and forwards navigation effects to the callbacks.
 * WebView state is saved into a `rememberSaveable` bundle on stop so a
 * process death restores the page.
 *
 * @param initialUrl page to open on first composition, or `null` to keep
 *   whatever the shared WebView already shows; on a blank first launch the
 *   ViewModel opens the saved start page (Google unless changed in settings)
 * @param onNavigateToExtraction called with the picked candidate id
 * @param onBackToLibrary leading back arrow (WebView history is popped first by system back)
 * @param onShowSnackbar when set, snackbar messages go here (the host launches them
 *   in its own scope) instead of suspending this route's effect collector
 * @param snackbarHostState the host's single snackbar state
 */
@Suppress("UNUSED_PARAMETER") // `sink` is used by the extraction route; kept here for signature parity
@Composable
fun <T : Any> WebClipperBrowserRoute(
    config: WebClipConfig<T>,
    sink: ClipSink<T>,
    labels: WebClipperLabels,
    initialUrl: String?,
    onNavigateToModelManagement: () -> Unit,
    onNavigateToExtraction: (candidateId: String) -> Unit,
    onBackToLibrary: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onShowSnackbar: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val holder: WebViewHolder = koinInject()
    val viewModel: BrowserViewModel = koinViewModel { parametersOf(config, labels, initialUrl) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val generation by holder.generation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedWebState = rememberSaveable { Bundle() }
    // True only on a genuinely blank start: no page in the ViewModel, none
    // requested, nothing in the shared WebView and nothing to restore. Then
    // the ViewModel opens the saved start page (or focuses the address bar
    // for a blank one); otherwise the page below is synced.
    val blankStart = remember(holder, viewModel) {
        viewModel.state.value.url.isBlank() && initialUrl.isNullOrBlank() &&
            holder.currentUrl == null && savedWebState.isEmpty
    }

    DisposableEffect(holder, lifecycleOwner) {
        holder.listener = object : WebViewHolder.Listener {
            override fun onPageStarted(url: String) = viewModel.onIntent(BrowserIntent.PageStarted(url))
            override fun onProgress(progress: Int) = viewModel.onIntent(BrowserIntent.PageProgress(progress))
            override fun onPageFinished(url: String, title: String?) =
                viewModel.onIntent(BrowserIntent.PageFinished(url, title))
            override fun onHistoryChanged(canGoBack: Boolean, canGoForward: Boolean) =
                viewModel.onIntent(BrowserIntent.HistoryChanged(canGoBack, canGoForward))
            override fun onScrolled(deltaY: Int) = viewModel.onIntent(BrowserIntent.Scrolled(deltaY))
            override fun onLoadError() = viewModel.onIntent(BrowserIntent.PageLoadFailed)
        }
        holder.bindLifecycle(lifecycleOwner)
        val saver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) holder.saveState(savedWebState)
        }
        lifecycleOwner.lifecycle.addObserver(saver)
        onDispose {
            holder.saveState(savedWebState)
            lifecycleOwner.lifecycle.removeObserver(saver)
            holder.unbindLifecycle(lifecycleOwner)
            holder.listener = null
        }
    }

    // First attach: restore after process death, or sync the ViewModel with
    // a page the shared WebView already shows (back from extraction with a
    // fresh ViewModel). Skipped when the ViewModel already knows a page.
    LaunchedEffect(holder, viewModel) {
        if (viewModel.state.value.url.isNotBlank()) return@LaunchedEffect
        if (holder.currentUrl == null && initialUrl.isNullOrBlank()) holder.restoreState(savedWebState)
        val url = holder.currentUrl
        if (url == null) {
            if (blankStart) viewModel.onIntent(BrowserIntent.OpenStartPage)
            return@LaunchedEffect
        }
        if (initialUrl.isNullOrBlank()) {
            viewModel.onIntent(BrowserIntent.PageStarted(url))
            viewModel.onIntent(BrowserIntent.PageFinished(url, holder.currentTitle))
            viewModel.onIntent(BrowserIntent.HistoryChanged(holder.canGoBack, holder.canGoForward))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BrowserEffect.NavigateToExtraction -> onNavigateToExtraction(effect.candidateId)
                BrowserEffect.NavigateToModelManagement -> onNavigateToModelManagement()
                is BrowserEffect.ShowSnackbar ->
                    if (onShowSnackbar != null) onShowSnackbar(effect.message)
                    else snackbarHostState.showSnackbar(effect.message)
                is BrowserEffect.LoadUrl -> holder.loadUrl(effect.url)
                BrowserEffect.ReloadPage -> holder.reload()
                BrowserEffect.StopLoading -> holder.stopLoading()
                BrowserEffect.GoForward -> holder.goForward()
                is BrowserEffect.ShareText -> shareText(context, effect.text)
                is BrowserEffect.OpenExternal -> openExternal(context, effect.url)
                is BrowserEffect.CopyToClipboard -> copyToClipboard(context, effect.text)
            }
        }
    }

    BackHandler(enabled = state.canGoBack) { holder.goBack() }

    WebClipperBrowserScreen(
        state = state,
        labels = labels,
        onIntent = viewModel::onIntent,
        onBackToLibrary = onBackToLibrary,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        // The ViewModel focuses the bar itself when the start page is blank.
        autoFocusAddressBar = false,
        webView = { slotModifier ->
            key(generation) {
                AndroidView(
                    factory = { holder.acquireDetached() },
                    modifier = slotModifier,
                )
            }
        },
    )
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}

private fun openExternal(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("url", text))
}
