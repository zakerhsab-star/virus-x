package com.virusx.app.ui.theme

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Curated full-app themes where Background + Surface + Buttons harmonize together
 * in both Dark and Light modes (e.g. Cafe & Off-White, Midnight & Gold, Emerald, Royal Blue, Lavender).
 */
enum class VirusXAccentPalette(
    val displayNameAr: String,
    val darkBg: Color,
    val darkSurface: Color,
    val darkSurfaceVar: Color,
    val primaryDark: Color,
    val secondaryDark: Color,
    val lightBg: Color,
    val lightSurface: Color,
    val lightSurfaceVar: Color,
    val primaryLight: Color,
    val secondaryLight: Color
) {
    CAFE_CREAM(
        displayNameAr = "كافيه × أوف وايت",
        darkBg = Color(0xFF171311),
        darkSurface = Color(0xFF221C19),
        darkSurfaceVar = Color(0xFF2E2622),
        primaryDark = Color(0xFFC89B7B),
        secondaryDark = Color(0xFFA47551),
        lightBg = Color(0xFFF7F3EE),
        lightSurface = Color(0xFFFFFDF9),
        lightSurfaceVar = Color(0xFFEDE4DA),
        primaryLight = Color(0xFF7B5236),
        secondaryLight = Color(0xFF9C6B49)
    ),
    CLASSIC_BLUE(
        displayNameAr = "أزرق ملكي",
        darkBg = Color(0xFF0E141B),
        darkSurface = Color(0xFF151E28),
        darkSurfaceVar = Color(0xFF1E2A38),
        primaryDark = Color(0xFF4A90E2),
        secondaryDark = Color(0xFF64B5F6),
        lightBg = Color(0xFFF2F6FC),
        lightSurface = Color(0xFFFFFFFF),
        lightSurfaceVar = Color(0xFFE5EEF9),
        primaryLight = Color(0xFF1A73E8),
        secondaryLight = Color(0xFF4285F4)
    ),
    EMERALD_CALM(
        displayNameAr = "أخضر زمردي",
        darkBg = Color(0xFF0D1715),
        darkSurface = Color(0xFF13221F),
        darkSurfaceVar = Color(0xFF1B302C),
        primaryDark = Color(0xFF2BB6A3),
        secondaryDark = Color(0xFF4FD1C5),
        lightBg = Color(0xFFF1F7F5),
        lightSurface = Color(0xFFFAFFFD),
        lightSurfaceVar = Color(0xFFE0EFEA),
        primaryLight = Color(0xFF0F766E),
        secondaryLight = Color(0xFF14B8A6)
    ),
    ROYAL_INDIGO(
        displayNameAr = "بنفسجي لافندر",
        darkBg = Color(0xFF13111C),
        darkSurface = Color(0xFF1C1929),
        darkSurfaceVar = Color(0xFF262238),
        primaryDark = Color(0xFF9B7EDE),
        secondaryDark = Color(0xFFB79CED),
        lightBg = Color(0xFFF6F4FC),
        lightSurface = Color(0xFFFFFFFF),
        lightSurfaceVar = Color(0xFFEBE6F7),
        primaryLight = Color(0xFF6D28D9),
        secondaryLight = Color(0xFF8B5CF6)
    ),
    MINIMAL_SLATE(
        displayNameAr = "كحلي × ذهبي",
        darkBg = Color(0xFF0B101B),
        darkSurface = Color(0xFF121A2B),
        darkSurfaceVar = Color(0xFF1B263B),
        primaryDark = Color(0xFFD4AF37),
        secondaryDark = Color(0xFFE6C667),
        lightBg = Color(0xFFF8F6F0),
        lightSurface = Color(0xFFFFFEFA),
        lightSurfaceVar = Color(0xFFEEE9DC),
        primaryLight = Color(0xFF9A7B2C),
        secondaryLight = Color(0xFFB8963E)
    )
}

data class VirusXThemeConfig(
    val isDarkMode: Boolean = true,
    val palette: VirusXAccentPalette = VirusXAccentPalette.CAFE_CREAM
)

object VirusXThemeManager {
    private const val PREFS = "virusx_theme_prefs"
    private const val KEY_DARK = "is_dark_mode"
    private const val KEY_PALETTE = "accent_palette"

    private val _config = MutableStateFlow(VirusXThemeConfig())
    var currentScheme by mutableStateOf(buildColorScheme(VirusXThemeConfig()))
        private set
    val config: StateFlow<VirusXThemeConfig> = _config.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean(KEY_DARK, true)
        val palName = prefs.getString(KEY_PALETTE, VirusXAccentPalette.CAFE_CREAM.name)
        val pal = runCatching { VirusXAccentPalette.valueOf(palName ?: "") }
            .getOrDefault(VirusXAccentPalette.CAFE_CREAM)
        val cfg = VirusXThemeConfig(isDarkMode = isDark, palette = pal)
        _config.value = cfg
        currentScheme = buildColorScheme(cfg)
    }

    fun setDarkMode(context: Context, isDark: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, isDark).apply()
        val cfg = _config.value.copy(isDarkMode = isDark)
        _config.value = cfg
        currentScheme = buildColorScheme(cfg)
    }

    fun setPalette(context: Context, palette: VirusXAccentPalette) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PALETTE, palette.name).apply()
        val cfg = _config.value.copy(palette = palette)
        _config.value = cfg
        currentScheme = buildColorScheme(cfg)
    }

    fun buildColorScheme(cfg: VirusXThemeConfig): ColorScheme {
        val p = cfg.palette
        return if (cfg.isDarkMode) {
            darkColorScheme(
                primary = p.primaryDark,
                secondary = p.secondaryDark,
                background = p.darkBg,
                surface = p.darkSurface,
                surfaceVariant = p.darkSurfaceVar,
                onPrimary = Color.White,
                onBackground = Color(0xFFEDEAE6),
                onSurface = Color(0xFFEDEAE6),
                onSurfaceVariant = Color(0xFFA09C96)
            )
        } else {
            lightColorScheme(
                primary = p.primaryLight,
                secondary = p.secondaryLight,
                background = p.lightBg,
                surface = p.lightSurface,
                surfaceVariant = p.lightSurfaceVar,
                onPrimary = Color.White,
                onBackground = Color(0xFF1C1917),
                onSurface = Color(0xFF1C1917),
                onSurfaceVariant = Color(0xFF6B6560)
            )
        }
    }
}

@Composable
fun VirusXThemeAppearanceCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { VirusXThemeManager.init(context) }
    val cfg by VirusXThemeManager.config.collectAsState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "المظهر وثيمات الواجهة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val modes = listOf(
                    Triple(true, "الوضع الداكن", Icons.Rounded.DarkMode),
                    Triple(false, "الوضع الفاتح", Icons.Rounded.LightMode)
                )
                modes.forEach { (darkFlag, label, icon) ->
                    val selected = cfg.isDarkMode == darkFlag
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .clickable { VirusXThemeManager.setDarkMode(context, darkFlag) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(VirusXAccentPalette.entries) { pal ->
                    val previewBg = if (cfg.isDarkMode) pal.darkBg else pal.lightBg
                    val previewAccent = if (cfg.isDarkMode) pal.primaryDark else pal.primaryLight
                    val isSelected = cfg.palette == pal

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .width(96.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(previewBg)
                            .border(
                                width = if (isSelected) 2.2.dp else 1.dp,
                                color = if (isSelected) previewAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { VirusXThemeManager.setPalette(context, pal) }
                            .padding(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(previewAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = pal.displayNameAr,
                            fontSize = 10.sp,
                            color = if (cfg.isDarkMode) Color(0xFFEDEAE6) else Color(0xFF1C1917),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
