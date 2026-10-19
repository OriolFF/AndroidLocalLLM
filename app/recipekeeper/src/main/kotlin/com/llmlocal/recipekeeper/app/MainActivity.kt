package com.llmlocal.recipekeeper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.recipekeeper.designsystem.RecipeKeeperTokens

/** Single Activity for Recipe Keeper; the navigation graph lives in [RecipeKeeperNavHost]. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme(tokens = RecipeKeeperTokens) {
                RecipeKeeperNavHost()
            }
        }
    }
}
