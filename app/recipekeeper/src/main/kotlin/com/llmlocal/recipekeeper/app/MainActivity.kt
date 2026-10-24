package com.llmlocal.recipekeeper.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.feature.webclipper.domain.input.ClipInputInbox
import com.llmlocal.recipekeeper.designsystem.RecipeKeeperTokens
import org.koin.android.ext.android.inject

/**
 * Single Activity for Recipe Keeper; the navigation graph lives in [RecipeKeeperNavHost].
 * `singleTop`: a share forwarded by [ShareReceiverActivity] arrives in [onNewIntent]
 * and is handed to the [ClipInputInbox], which the NavHost drains.
 */
class MainActivity : ComponentActivity() {

    private val inbox: ClipInputInbox by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A restored instance already offered its share before it was recreated.
        if (savedInstanceState == null) offerShare(intent)
        setContent {
            AppTheme(tokens = RecipeKeeperTokens) {
                RecipeKeeperNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerShare(intent)
    }

    private fun offerShare(intent: Intent) {
        ShareIntentParser.parse(intent)?.let(inbox::offer)
    }
}
