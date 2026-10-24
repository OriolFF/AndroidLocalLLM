package com.llmlocal.recipekeeper.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import com.llmlocal.feature.webclipper.domain.input.PendingClipInput

/**
 * Turns a share intent into the [PendingClipInput] the NavHost acts on.
 *
 *  - pictures (an image MIME type, or any share that carries stream URIs when the type
 *    is missing) → [PendingClipInput.Images], in the order the sender gave
 *    them; no cap here, the images use case applies the policy cap;
 *  - `text/plain` whose text contains a link → [PendingClipInput.Url] with the
 *    first `http(s)` link, trailing punctuation trimmed;
 *  - anything else → `null`.
 *
 * The decision logic works on plain values ([parse] with explicit arguments) so
 * it is unit-testable on the JVM; the [Intent] overload only reads the extras.
 */
object ShareIntentParser {

    private val link = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}>\"'"

    fun parse(intent: Intent): PendingClipInput? {
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
        val clip = intent.clipData
        val clipUris = buildList {
            if (clip != null) for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { add(it.toString()) }
        }
        return parse(
            action = action,
            mimeType = intent.type,
            streamUris = streamUris(intent).map(Uri::toString),
            clipUris = clipUris,
            text = intent.getStringExtra(Intent.EXTRA_TEXT),
        )
    }

    fun parse(
        action: String?,
        mimeType: String?,
        streamUris: List<String>,
        clipUris: List<String>,
        text: String?,
    ): PendingClipInput? {
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
        val uris = (streamUris + clipUris).distinct()
        val isText = mimeType?.startsWith("text/") == true
        if (uris.isNotEmpty() && !isText) return PendingClipInput.Images(uris)
        return text?.let(::firstLink)?.let(PendingClipInput::Url)
    }

    /** The first `http(s)` link in [text] without trailing punctuation, or `null`. */
    fun firstLink(text: String): String? =
        link.find(text)?.value?.trimEnd { it in TRAILING_PUNCTUATION }?.takeIf { it.length > "https://".length }

    /**
     * `EXTRA_STREAM` as URIs. `SEND_MULTIPLE` normally carries an `ArrayList`,
     * but some senders put a single `Uri` under it (I4 QA P3-7): the list read
     * then returns null, so fall back to the single-value read instead of
     * dropping the share.
     */
    @Suppress("DEPRECATION")
    internal fun streamUris(intent: Intent): List<Uri> {
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val list = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
            if (list != null) return list
        }
        val uri = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        return listOfNotNull(uri)
    }
}
