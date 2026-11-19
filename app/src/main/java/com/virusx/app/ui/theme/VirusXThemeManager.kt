package com.virusx.app.ui.theme

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

enum class VirusXAccentPalette(
    val displayNameAr: String,
    val primaryDark: Color,
    val primaryLight: Color,
    val secondaryDark: Color,
    val secondaryLight: Color
) {
    CLASSIC_BLUE(
        displayNameAr = "أزرق كلاسيكي",
        primaryDark = Color(0xFF4A90E2),
        primaryLight = Color(0xFF1A73E8),
        secondaryDark = Color(0xFF64B5F6),
        secondaryLight = Color(0xFF4285F4)
    ),
    EMERALD_CALM(
        displayNameAr = "أخضر هادئ",
        primaryDark = Color(0xFF26A69A),
        primaryLight = Color(0xFF00897B),
        secondaryDark = Color(0xFF4DB6AC),
        secondaryLight = Color(0xFF26A69A)
    ),
    ROYAL_INDIGO(
        displayNameAr = "بنفسجي راقي",
        primaryDark = Color(0xFF7E57C2),
        primaryLight = Color(0xFF5E35B1),
        secondaryDark = Color(0xFF9575CD),
        secondaryLight = Color(0xFF7E57C2)
    ),
    MINIMAL_SLATE(
        displayNameAr = "رمادي عصري",
        primaryDark = Color(0xFF78909C),
        primaryLight = Color(0xFF455A64),
        secondaryDark = Color(0xFF90A4AE),
        secondaryLight = Color(0xFF607D8B)
    )
}

data class VirusXThemeConfig(
    val isDarkMode: Boolean = true,
    val palette: VirusXAccentPalette = VirusXAccentPalette.CLASSIC_BLUE
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
        val palName = prefs.getString(KEY_PALETTE, VirusXAccentPalette.CLASSIC_BLUE.name)
        val pal = runCatching { VirusXAccentPalette.valueOf(palName ?: "") }
            .getOrDefault(VirusXAccentPalette.CLASSIC_BLUE)
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
        return if (cfg.isDarkMode) {
            darkColorScheme(
                primary = cfg.palette.primaryDark,
                secondary = cfg.palette.secondaryDark,
                background = Color(0xFF0F1419),
                surface = Color(0xFF161D24),
                surfaceVariant = Color(0xFF1E2732),
                onPrimary = Color.White,
                onBackground = Color(0xFFE7E9EA),
                onSurface = Color(0xFFE7E9EA),
                onSurfaceVariant = Color(0xFF8B98A5)
            )
        } else {
            lightColorScheme(
                primary = cfg.palette.primaryLight,
                secondary = cfg.palette.secondaryLight,
                background = Color(0xFFF7F8FA),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFEEF1F5),
                onPrimary = Color.White,
                onBackground = Color(0xFF111827),
                onSurface = Color(0xFF111827),
                onSurfaceVariant = Color(0xFF6B7280)
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
        shape = RoundedCornerShape(16.dp),
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
                    text = "المظهر والألوان",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Dark / Light Segmented Switch
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

            // Accent Color Swatches
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VirusXAccentPalette.entries.forEach { pal ->
                    val swatchColor = if (cfg.isDarkMode) pal.primaryDark else pal.primaryLight
                    val isSelected = cfg.palette == pal
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { VirusXThemeManager.setPalette(context, pal) }
                            .padding(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = pal.displayNameAr,
                            fontSize = 10.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
