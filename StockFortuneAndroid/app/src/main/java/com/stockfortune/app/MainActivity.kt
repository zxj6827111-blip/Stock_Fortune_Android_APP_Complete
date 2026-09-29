package com.stockfortune.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.stockfortune.app.ui.navigation.SfApp
import com.stockfortune.app.ui.theme.StockFortuneTheme
import com.stockfortune.app.ui.theme.SfColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StockFortuneTheme {
                Surface(Modifier.fillMaxSize(), color = SfColors.PageBg) { SfApp() }
            }
        }
    }
}
