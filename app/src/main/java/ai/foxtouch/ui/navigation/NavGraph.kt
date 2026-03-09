package ai.foxtouch.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ai.foxtouch.ui.screens.chat.ChatScreen
import ai.foxtouch.ui.screens.settings.AgentDocsScreen
import ai.foxtouch.ui.screens.settings.SettingsScreen
import ai.foxtouch.ui.screens.setup.SetupWizardScreen
import ai.foxtouch.ui.screens.tasks.TaskScreen

object Routes {
    const val SETUP = "setup"
    const val CHAT = "chat"
    const val TASKS = "tasks"
    const val SETTINGS = "settings"
    const val AGENT_DOCS = "agent_docs"
}

@Composable
fun FoxTouchNavHost(
    navViewModel: NavViewModel = hiltViewModel(),
) {
    val isSetupComplete by navViewModel.isSetupComplete.collectAsState()
    val navController = rememberNavController()

    val startDestination = if (isSetupComplete) Routes.CHAT else Routes.SETUP

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Routes.SETUP) {
            SetupWizardScreen(
                onSetupComplete = {
                    navController.navigate(Routes.CHAT) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.CHAT) {
            ChatScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.TASKS) {
            TaskScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAgentDocs = { navController.navigate(Routes.AGENT_DOCS) },
            )
        }
        composable(Routes.AGENT_DOCS) {
            AgentDocsScreen(onBack = { navController.popBackStack() })
        }
    }
}
