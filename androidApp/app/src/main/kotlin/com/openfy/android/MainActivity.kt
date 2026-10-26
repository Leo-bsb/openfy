package com.openfy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.openfy.android.ui.OpenfyApp
import com.openfy.android.ui.theme.OpenfyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenfyTheme {
                Surface(Modifier.fillMaxSize()) {
                    OpenfyApp()
                }
            }
        }
    }
}
