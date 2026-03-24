package com.gpproject.smartpetitiongenerator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.gpproject.smartpetitiongenerator.ui.theme.SmartPetitionGeneratorTheme

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

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SmartPetitionGeneratorTheme {
        Greeting("Android")
    }
}