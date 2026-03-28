package ai.openclaw.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ai.openclaw.app.MainViewModel

@Composable
fun RootScreen(viewModel: MainViewModel) {
  val onboardingCompleted by viewModel.onboardingCompleted.collectAsState()
  val appLanguage by viewModel.appLanguage.collectAsState()

  CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
    if (!onboardingCompleted) {
      OnboardingFlow(viewModel = viewModel, modifier = Modifier.fillMaxSize())
      return@CompositionLocalProvider
    }

    PostOnboardingTabs(viewModel = viewModel, modifier = Modifier.fillMaxSize())
  }
}
