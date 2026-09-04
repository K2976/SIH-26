package com.fitforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fitforge.app.core.navigation.FitForgeApp
import com.fitforge.app.core.theme.FitForgeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitForgeTheme {
                FitForgeApp()
            }
        }
    }
}
