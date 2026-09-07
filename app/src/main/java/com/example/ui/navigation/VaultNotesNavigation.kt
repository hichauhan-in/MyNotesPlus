package com.example.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.editor.EditorScreen
import com.example.ui.editor.EditorViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.reminders.ReminderScreen
import com.example.ui.reminders.ReminderViewModel
import com.example.ui.settings.SettingsViewModel

private object Routes {
    const val HOME = "home"
    const val EDITOR = "editor?noteId={noteId}&template={template}&folderId={folderId}&templateId={templateId}"
    const val SETTINGS = "settings"
    const val REMINDERS = "reminders"

    fun editor(
        noteId: String? = null,
        template: String? = null,
        folderId: String? = null,
        templateId: String? = null,
    ): String {
        val params = buildList {
            if (noteId != null) add("noteId=$noteId")
            if (template != null) add("template=$template")
            if (folderId != null) add("folderId=$folderId")
            if (templateId != null) add("templateId=$templateId")
        }
        return if (params.isEmpty()) "editor" else "editor?" + params.joinToString("&")
    }
}

@Composable
fun MyNotesNavigation(
    pendingQuickAction: String? = null,
    onQuickActionHandled: () -> Unit = {},
    pendingOpenNoteId: String? = null,
    onOpenNoteHandled: () -> Unit = {},
    pendingOpenReminders: Boolean = false,
    onOpenRemindersHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val dur = 300

    // A home-screen widget can ask us to open the editor with a specific note type on launch.
    LaunchedEffect(pendingQuickAction) {
        if (pendingQuickAction != null) {
            val template = when (pendingQuickAction) {
                "checklist" -> "checklist"
                "expense" -> "expense"
                "board" -> "scribble"
                else -> null // plain note
            }
            navController.navigate(Routes.editor(template = template))
            onQuickActionHandled()
        }
    }

    // A reminder notification can ask us to open a specific note.
    LaunchedEffect(pendingOpenNoteId) {
        if (pendingOpenNoteId != null) {
            navController.navigate(Routes.editor(noteId = pendingOpenNoteId))
            onOpenNoteHandled()
        }
    }

    // The reminders widget can ask us to open the Reminders screen.
    LaunchedEffect(pendingOpenReminders) {
        if (pendingOpenReminders) {
            navController.navigate(Routes.REMINDERS)
            onOpenRemindersHandled()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        // A gentle cross-fade with a hint of directional slide. Enter and exit share one duration so
        // the outgoing and incoming screens dissolve into each other smoothly, instead of the new
        // screen snapping into place before the old one has faded.
        enterTransition = {
            fadeIn(tween(dur, easing = FastOutSlowInEasing)) +
                slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { it / 12 }
        },
        exitTransition = {
            fadeOut(tween(dur, easing = FastOutSlowInEasing)) +
                slideOutHorizontally(tween(dur, easing = FastOutSlowInEasing)) { -it / 24 }
        },
        popEnterTransition = {
            fadeIn(tween(dur, easing = FastOutSlowInEasing)) +
                slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { -it / 24 }
        },
        popExitTransition = {
            fadeOut(tween(dur, easing = FastOutSlowInEasing)) +
                slideOutHorizontally(tween(dur, easing = FastOutSlowInEasing)) { it / 12 }
        },
    ) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = viewModel,
                onNoteClick = { noteId -> navController.navigate(Routes.editor(noteId = noteId)) },
                onCreateNote = { template, folderId ->
                    navController.navigate(Routes.editor(template = template, folderId = folderId))
                },
                onEditTemplate = { templateId ->
                    navController.navigate(Routes.editor(templateId = templateId))
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenReminders = { navController.navigate(Routes.REMINDERS) },
            )
        }

        composable(Routes.REMINDERS) {
            val viewModel: ReminderViewModel = viewModel()
            ReminderScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenNote = { noteId -> navController.navigate(Routes.editor(noteId = noteId)) },
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("noteId") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
                navArgument("template") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
                navArgument("folderId") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
                navArgument("templateId") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId")
            val template = backStackEntry.arguments?.getString("template")
            val folderId = backStackEntry.arguments?.getString("folderId")
            val templateId = backStackEntry.arguments?.getString("templateId")
            val viewModel: EditorViewModel = viewModel()
            EditorScreen(
                viewModel = viewModel,
                noteId = noteId,
                template = template,
                folderId = folderId,
                templateId = templateId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
