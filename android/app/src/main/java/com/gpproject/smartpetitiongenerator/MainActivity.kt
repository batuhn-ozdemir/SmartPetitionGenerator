package com.gpproject.smartpetitiongenerator

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gpproject.smartpetitiongenerator.data.local.AppDatabase
import com.gpproject.smartpetitiongenerator.data.remote.NetworkModule
import com.gpproject.smartpetitiongenerator.data.repository.MainRepository
import com.gpproject.smartpetitiongenerator.ui.screens.CreatePetitionScreen
import com.gpproject.smartpetitiongenerator.ui.screens.HistoryScreen
import com.gpproject.smartpetitiongenerator.ui.screens.HomeScreen
import com.gpproject.smartpetitiongenerator.ui.screens.PreviewScreen
import com.gpproject.smartpetitiongenerator.ui.screens.ProfileScreen
import com.gpproject.smartpetitiongenerator.ui.screens.ScanToPreviewScreen
import com.gpproject.smartpetitiongenerator.ui.theme.SmartPetitionGeneratorDemoTheme
import com.gpproject.smartpetitiongenerator.ui.theme.ThemeMode
import com.gpproject.smartpetitiongenerator.ui.viewmodel.PetitionViewModel

private const val THEME_PREFS = "theme_preferences"
private const val THEME_MODE_KEY = "theme_mode"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var themeMode by rememberSaveable {
                mutableStateOf(loadThemeMode(context))
            }

            SmartPetitionGeneratorDemoTheme(themeMode = themeMode) {
                // Veritabanı
                val database = remember { AppDatabase.getDatabase(context) }

                val repository = remember {
                    MainRepository(context, database.petitionDao(), NetworkModule.apiService)
                }

                val viewModel = remember { PetitionViewModel(repository) }

                MainScreen(
                    viewModel = viewModel,
                    themeMode = themeMode,
                    onThemeModeChange = { newThemeMode ->
                        themeMode = newThemeMode
                        saveThemeMode(context, newThemeMode)
                    }
                )
            }
        }
    }
}

private fun loadThemeMode(context: Context): ThemeMode {
    val prefs = context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
    return ThemeMode.fromStorageValue(prefs.getString(THEME_MODE_KEY, null))
}

private fun saveThemeMode(context: Context, themeMode: ThemeMode) {
    context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(THEME_MODE_KEY, themeMode.storageValue)
        .apply()
}

@Composable
fun MainScreen(
    viewModel: PetitionViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                // Menü Elemanları
                val items = listOf("Ana Sayfa", "Oluştur", "Tara", "Geçmiş", "Profil")
                val icons = listOf(Icons.Default.Home, Icons.Default.Add, Icons.Default.CameraAlt, Icons.Default.Refresh, Icons.Default.Person)
                val routes = listOf("home", "create", "scan", "history", "profile")

                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(icons[index], contentDescription = item) },
                        label = { Text(item) },
                        selected = if (routes[index] == "create") currentRoute?.startsWith("create") == true else currentRoute == routes[index],
                        onClick = {
                            navController.navigate(routes[index]) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {

            composable("home") {
                HomeScreen(
                    navController = navController,
                    templates = viewModel.readyTemplates.value,
                    viewModel = viewModel
                )
            }

            composable("create") {
                CreatePetitionScreen(
                    navController = navController,
                    viewModel = viewModel,
                    readyTemplateId = null,
                    showPromptInput = true
                )
            }

            composable(
                route = "template_form/{readyTemplateId}",
                arguments = listOf(navArgument("readyTemplateId") { type = NavType.StringType })
            ) { backStackEntry ->
                val readyTemplateId = backStackEntry.arguments?.getString("readyTemplateId")
                CreatePetitionScreen(
                    navController = navController,
                    viewModel = viewModel,
                    readyTemplateId = readyTemplateId,
                    showPromptInput = false
                )
            }

            composable("history") {
                HistoryScreen(navController = navController, viewModel = viewModel)
            }

            composable("scan") {
                ScanToPreviewScreen(navController = navController, viewModel = viewModel)
            }

            composable("profile") {
                ProfileScreen(
                    navController = navController,
                    viewModel = viewModel,
                    selectedThemeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )            }

            composable(
                route = "preview_screen/{petitionId}?mode={mode}",
                arguments = listOf(
                    navArgument("petitionId") { type = NavType.IntType },
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = "edit"
                    }
                )
            ) { backStackEntry ->
                val petitionId = backStackEntry.arguments?.getInt("petitionId") ?: -1
                val mode = backStackEntry.arguments?.getString("mode") ?: "edit"
                PreviewScreen(
                    navController = navController,
                    viewModel = viewModel,
                    petitionId = petitionId,
                    openShareOnLaunch = mode == "share"
                )
            }

            composable("preview_screen/new") {
                PreviewScreen(navController, viewModel, null)
            }
        }
    }
}