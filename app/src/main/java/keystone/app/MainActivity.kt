// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class)
package keystone.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import keystone.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startRoute = intent?.getStringExtra("route")
        setContent {
            tick()
            val e = Ks.engine
            KeystoneTheme(mode = e.setting("theme"), dynamic = e.setting("dynamic_color") == "on") {
                AppRoot(startRoute)
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Outlined.Dashboard),
    Tab("today", "Today", Icons.Outlined.Today),
    Tab("para", "PARA", Icons.Outlined.Layers),
    Tab("hubs", "Hubs", Icons.Outlined.GridView),
    Tab("search", "Search", Icons.Outlined.Search),
)

@Composable
fun AppRoot(startRoute: String? = null) {
    val nav = rememberNavController()
    val start = remember { if (Ks.engine.setting("onboarded") == "true") (startRoute ?: "home") else "onboarding" }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    CompositionLocalProvider(LocalNav provides nav) {
        Scaffold(
            bottomBar = {
                if (current in tabs.map { it.route }) NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route,
                            onClick = { nav.navigate(t.route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } },
                            icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { pad ->
            NavHost(nav, startDestination = start, modifier = Modifier.padding(bottom = pad.calculateBottomPadding())) {
                composable("home") { HomeScreen() }
                composable("today") { TodayScreen() }
                composable("para") { ParaScreen() }
                composable("hubs") { HubsScreen() }
                composable("search") { SearchScreen() }
                composable("hub/{id}") { HubScreen(it.arguments?.getString("id") ?: "") }
                composable("col/{id}?view={view}", arguments = listOf(navArgument("view") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                    CollectionScreen(it.arguments?.getString("id") ?: "", it.arguments?.getString("view"))
                }
                composable("entry/{id}?new={new}", arguments = listOf(navArgument("new") { type = NavType.BoolType; defaultValue = false })) {
                    EntryScreen(it.arguments?.getString("id") ?: "", it.arguments?.getBoolean("new") ?: false)
                }
                composable("schema/{id}") { SchemaScreen(it.arguments?.getString("id") ?: "") }
                composable("setup") { LifeSetupScreen() }
                composable("onboarding") { OnboardingScreen() }
                composable("settings") { SettingsScreen() }
                composable("about") { AboutScreen() }
                composable("archive") { ArchiveScreen() }
                composable("cv") { CvScreen() }
                composable("flashcards") { FlashcardsScreen() }
                composable("guide") { GuideScreen() }
            }
        }
    }
}
