package com.oneclaw.shadow.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Route.Chat.path,
        modifier = modifier
    ) {
        composable(Route.Chat.path) {
            PlaceholderScreen("Chat (New)")
        }

        composable(Route.ChatSession.PATH) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            PlaceholderScreen("Chat: $sessionId")
        }

        composable(Route.AgentList.path) {
            PlaceholderScreen("Agent List")
        }

        composable(Route.AgentDetail.PATH) { backStackEntry ->
            val agentId = backStackEntry.arguments?.getString("agentId") ?: ""
            PlaceholderScreen("Agent Detail: $agentId")
        }

        composable(Route.AgentCreate.path) {
            PlaceholderScreen("Create Agent")
        }

        composable(Route.ProviderList.path) {
            PlaceholderScreen("Provider List")
        }

        composable(Route.ProviderDetail.PATH) { backStackEntry ->
            val providerId = backStackEntry.arguments?.getString("providerId") ?: ""
            PlaceholderScreen("Provider Detail: $providerId")
        }

        composable(Route.Setup.path) {
            PlaceholderScreen("Welcome / Setup")
        }

        composable(Route.Settings.path) {
            PlaceholderScreen("Settings")
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title)
    }
}
