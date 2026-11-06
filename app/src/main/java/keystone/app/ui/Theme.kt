// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Amber = Color(0xFFFCA311)
val Navy = Color(0xFF14213D)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB84D), onPrimary = Color(0xFF2A1800),
    primaryContainer = Color(0xFF5C3B00), onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFF9DB4E0), onSecondary = Color(0xFF0B1D3D),
    secondaryContainer = Color(0xFF26375A), onSecondaryContainer = Color(0xFFD8E2FF),
    tertiary = Color(0xFF7FD6B5),
    background = Color(0xFF101418), onBackground = Color(0xFFE2E6EC),
    surface = Color(0xFF101418), onSurface = Color(0xFFE2E6EC),
    surfaceVariant = Color(0xFF2A3038), onSurfaceVariant = Color(0xFFBFC6D0),
    surfaceContainerLowest = Color(0xFF0B0F13), surfaceContainerLow = Color(0xFF161B21),
    surfaceContainer = Color(0xFF1A2027), surfaceContainerHigh = Color(0xFF222932), surfaceContainerHighest = Color(0xFF2C343E),
    outline = Color(0xFF59626E), outlineVariant = Color(0xFF353C45),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5300), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB3), onPrimaryContainer = Color(0xFF2C1700),
    secondary = Color(0xFF3A5A92), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E2FF), onSecondaryContainer = Color(0xFF001A41),
    tertiary = Color(0xFF00694F),
    background = Color(0xFFF8F9FB), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FB), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E3E9), onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F4F7),
    surfaceContainer = Color(0xFFECEEF2), surfaceContainerHigh = Color(0xFFE6E8EC), surfaceContainerHighest = Color(0xFFE0E2E6),
    outline = Color(0xFF74777F), outlineVariant = Color(0xFFC4C6CF),
)

private val Type = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

@Composable
fun KeystoneTheme(mode: String?, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, typography = Type, content = content)
}

/** Ten option colours (select chips, boards, charts). */
val Palette = listOf(
    Color(0xFF8E99A8), Color(0xFFE5484D), Color(0xFFF76B15), Color(0xFFFFC53D), Color(0xFF30A46C),
    Color(0xFF0090FF), Color(0xFF8E4EC6), Color(0xFFD6409F), Color(0xFF12A594), Color(0xFFA18072),
)

fun paletteColor(i: Int) = Palette[((i % Palette.size) + Palette.size) % Palette.size]

/** Icon names used by presets mapped to Material Symbols (Apache-2.0). */
fun iconFor(name: String?): ImageVector = when (name) {
    "dashboard" -> Icons.Outlined.Dashboard
    "explore" -> Icons.Outlined.Explore
    "fitness", "barbell" -> Icons.Outlined.FitnessCenter
    "restaurant" -> Icons.Outlined.Restaurant
    "work" -> Icons.Outlined.Work
    "wallet" -> Icons.Outlined.AccountBalanceWallet
    "school" -> Icons.Outlined.School
    "home" -> Icons.Outlined.Home
    "favorite" -> Icons.Outlined.FavoriteBorder
    "flight" -> Icons.Outlined.Flight
    "check" -> Icons.Outlined.CheckCircle
    "rocket" -> Icons.Outlined.RocketLaunch
    "layers" -> Icons.Outlined.Layers
    "library" -> Icons.Outlined.LocalLibrary
    "bookmark" -> Icons.Outlined.BookmarkBorder
    "tag" -> Icons.AutoMirrored.Outlined.Label
    "repeat" -> Icons.Outlined.Repeat
    "today" -> Icons.Outlined.Today
    "calendar_month" -> Icons.Outlined.CalendarMonth
    "event" -> Icons.Outlined.Event
    "book" -> Icons.Outlined.Book
    "bedtime" -> Icons.Outlined.Bedtime
    "donut" -> Icons.Outlined.DonutLarge
    "diamond" -> Icons.Outlined.Diamond
    "flag" -> Icons.Outlined.Flag
    "monitor_weight" -> Icons.Outlined.MonitorWeight
    "menu_book" -> Icons.AutoMirrored.Outlined.MenuBook
    "shopping_cart" -> Icons.Outlined.ShoppingCart
    "badge" -> Icons.Outlined.Badge
    "forum" -> Icons.Outlined.Forum
    "history_edu" -> Icons.Outlined.HistoryEdu
    "construction" -> Icons.Outlined.Construction
    "receipt" -> Icons.Outlined.Receipt
    "savings" -> Icons.Outlined.Savings
    "paid" -> Icons.Outlined.Paid
    "autorenew" -> Icons.Outlined.Autorenew
    "translate" -> Icons.Outlined.Translate
    "handyman" -> Icons.Outlined.Handyman
    "inventory" -> Icons.Outlined.Inventory2
    "car" -> Icons.Outlined.DirectionsCar
    "build" -> Icons.Outlined.Build
    "verified" -> Icons.Outlined.Verified
    "group" -> Icons.Outlined.Group
    "redeem" -> Icons.Outlined.Redeem
    "card_giftcard" -> Icons.Outlined.CardGiftcard
    "beach" -> Icons.Outlined.BeachAccess
    "luggage" -> Icons.Outlined.Luggage
    "movie" -> Icons.Outlined.Movie
    "sports_esports" -> Icons.Outlined.SportsEsports
    "star" -> Icons.Outlined.StarOutline
    "lightbulb" -> Icons.Outlined.Lightbulb
    "pets" -> Icons.Outlined.Pets
    "music" -> Icons.Outlined.MusicNote
    "palette" -> Icons.Outlined.Palette
    "code" -> Icons.Outlined.Code
    else -> Icons.Outlined.Folder
}

val CUSTOM_ICONS = listOf("folder", "star", "lightbulb", "pets", "music", "palette", "code", "book", "flag", "favorite", "home", "work", "school", "fitness", "restaurant", "flight", "movie", "sports_esports", "savings", "group")
