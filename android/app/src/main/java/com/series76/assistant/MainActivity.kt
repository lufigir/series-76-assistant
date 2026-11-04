package com.series76.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.series76.assistant.ui.navigation.AppRoot
import com.series76.assistant.ui.theme.Series76Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as Series76Application).container
        setContent {
            Series76Theme {
                AppRoot(container)
            }
        }
    }
}
