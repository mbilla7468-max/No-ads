package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.NoAdsDashboardScreen
import com.example.ui.NoAdsViewModel
import com.example.ui.SplashScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: NoAdsViewModel = viewModel()
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()

      val systemDark = isSystemInDarkTheme()
      val isDarkTheme = when (uiState.darkModePref) {
        1 -> false
        2 -> true
        else -> true // Default to Dark Theme as requested
      }

      var showSplash by rememberSaveable { mutableStateOf(true) }

      MyApplicationTheme(darkTheme = isDarkTheme) {
        Crossfade(
          targetState = showSplash,
          animationSpec = tween(durationMillis = 400),
          label = "splash_to_dashboard"
        ) { isSplashVisible ->
          if (isSplashVisible) {
            SplashScreen(
              onFinishSplash = { showSplash = false }
            )
          } else {
            NoAdsDashboardScreen(viewModel = viewModel)
          }
        }
      }
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  // Retained for test compatibility
  androidx.compose.material3.Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme { Greeting("Android") }
}


