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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VirusXAccentPalette(
    val displayNameAr: String,
    val darkBgTop: Color,
    val darkBgBottom: Color,
    val darkSurface: Color,
    val darkSurfaceVar: Color,
    val primaryDark: Color,
    val secondaryDark: Color,
    val borderGlowDark: Color,
    val lightBgTop: Color,
    val lightBgBottom: Color,
    val lightSurface: Color,
    val lightSurfaceVar: Color,
    val primaryLight: Color,
    val secondaryLight: Color,
    val borderGlowLight: Color
) {
    CYBER_BLUE(
        displayNameAr = "الأزرق السيبراني",
        darkBgTop = Color(0xFF0A1628),
        darkBgBottom = Color(0xFF060B14),
        darkSurface = Color(0xCC11223B),
        darkSurfaceVar = Color(0xFF162C4A),
        primaryDark = Color(0xFF38BDF8),
        secondaryDark = Color(0xFF0284C7),
        borderGlowDark = Color(0xFF38BDF8),
        lightBgTop = Color(0xFFEFF6FF),
        lightBgBottom = Color(0xFFE0EEFF),
        lightSurface = Color(0xF5FFFFFF),
        lightSurfaceVar = Color(0xFFE2ECF8),
        primaryLight = Color(0xFF0284C7),
        secondaryLight = Color(0xFF0369A1),
        borderGlowLight = Color(0xFF38BDF8)
    ),
    EMERALD_DARK(
        displayNameAr = "الزمردي الفاخر",
        darkBgTop = Color(0xFF08241C),
        darkBgBottom = Color(0xFF05130F),
        darkSurface = Color(0xCC0E3328),
        darkSurfaceVar = Color(0xFF144234),
        primaryDark = Color(0xFF4ADE80),
        secondaryDark = Color(0xFF10B981),
        borderGlowDark = Color(0xFF86EFAC),
        lightBgTop = Color(0xFFECFDF5),
        lightBgBottom = Color(0xFFD1FAE5),
        lightSurface = Color(0xF5FFFFFF),
        lightSurfaceVar = Color(0xFFDCFCE7),
        primaryLight = Color(0xFF059669),
        secondaryLight = Color(0xFF047857),
        borderGlowLight = Color(0xFF10B981)
    ),
    NEON_PURPLE_ORANGE(
        displayNameAr = "بنفسجي × برتقالي",
        darkBgTop = Color(0xFF1E0B2E),
        darkBgBottom = Color(0xFF0D0614),
        darkSurface = Color(0xCC2A123D),
        darkSurfaceVar = Color(0xFF381952),
        primaryDark = Color(0xFFFB923C),
        secondaryDark = Color(0xFFC084FC),
        borderGlowDark = Color(0xFFFB923C),
        lightBgTop = Color(0xFFFAF5FF),
        lightBgBottom = Color(0xFFFFF7ED),
        lightSurface = Color(0xF5FFFFFF),
        lightSurfaceVar = Color(0xFFF3E8FF),
        primaryLight = Color(0xFFEA580C),
        secondaryLight = Color(0xFF9333EA),
        borderGlowLight = Color(0xFFFB923C)
    ),
    CHARCOAL_GOLD(
        displayNameAr = "رمادي × ذهبي",
        darkBgTop = Color(0xFF18181B),
        darkBgBottom = Color(0xFF0B0B0D),
        darkSurface = Color(0xCC242429),
        darkSurfaceVar = Color(0xFF2E2E34),
        primaryDark = Color(0xFFD4AF37),
        secondaryDark = Color(0xFFB8860B),
        borderGlowDark = Color(0xFFE5C158),
        lightBgTop = Color(0xFFFAF8F5),
        lightBgBottom = Color(0xFFF0EBE1),
        lightSurface = Color(0xF5FFFFFF),
        lightSurfaceVar = Color(0xFFEBE5D8),
        primaryLight = Color(0xFF9A7B2C),
        secondaryLight = Color(0xFFB8963E),
        borderGlowLight = Color(0xFFD4AF37)
    ),
    CAFE_CREAM(
        displayNameAr = "كافيه × أوف وايت",
        darkBgTop = Color(0xFF1E1714),
        darkBgBottom = Color(0xFF100C0A),
        darkSurface = Color(0xCC2B211D),
        darkSurfaceVar = Color(0xFF382B26),
        primaryDark = Color(0xFFC89B7B),
        secondaryDark = Color(0xFFA47551),
        borderGlowDark = Color(0xFFD7B196),
        lightBgTop = Color(0xFFF7F3EE),
        lightBgBottom = Color(0xFFEDE4DA),
        lightSurface = Color(0xF5FFFDF9),
        lightSurfaceVar = Color(0xFFE6DACD),
        primaryLight = Color(0xFF7B5236),
        secondaryLight = Color(0xFF9C6B49),
        borderGlowLight = Color(0xFFA47551)
    )
}

data class VirusXThemeConfig(
    val isDarkMode: Boolean = true,
    val palette: VirusXAccentPalette = VirusXAccentPalette.CYBER_BLUE
)

object VirusXThemeManager {
    private const val PREFS = "virusx_theme_prefs"
    private const val KEY_DARK = "is_dark_mode"
    private const val KEY_PALETTE = "accent_palette"

    private val _config = MutableStateFlow(VirusXThemeConfig())
    var currentScheme by mutableStateOf(buildColorScheme(VirusXThemeConfig()))
        private set
    var activeConfigState by mutableStateOf(VirusXThemeConfig())
        private set

    val config: StateFlow<VirusXThemeConfig> = _config.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean(KEY_DARK, true)
        val palName = prefs.getString(KEY_PALETTE, VirusXAccentPalette.CYBER_BLUE.name)
        val pal = runCatching { VirusXAccentPalette.valueOf(palName ?: "") }
            .getOrDefault(VirusXAccentPalette.CYBER_BLUE)
        val cfg = VirusXThemeConfig(isDarkMode = isDark, palette = pal)
        _config.value = cfg
        activeConfigState = cfg
        currentScheme = buildColorScheme(cfg)
    }

    fun setDarkMode(context: Context, isDark: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, isDark).apply()
        val cfg = _config.value.copy(isDarkMode = isDark)
        _config.value = cfg
        activeConfigState = cfg
        currentScheme = buildColorScheme(cfg)
    }

    fun setPalette(context: Context, palette: VirusXAccentPalette) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PALETTE, palette.name).apply()
        val cfg = _config.value.copy(palette = palette)
        _config.value = cfg
        activeConfigState = cfg
        currentScheme = buildColorScheme(cfg)
    }

    fun buildBackgroundBrush(cfg: VirusXThemeConfig = activeConfigState): Brush {
        val p = cfg.palette
        return if (cfg.isDarkMode) {
            Brush.verticalGradient(listOf(p.darkBgTop, p.darkBgBottom))
        } else {
            Brush.verticalGradient(listOf(p.lightBgTop, p.lightBgBottom))
        }
    }

    fun buildGlassBorderBrush(cfg: VirusXThemeConfig = activeConfigState): Brush {
        val p = cfg.palette
        return if (cfg.isDarkMode) {
            Brush.linearGradient(
                listOf(
                    p.borderGlowDark.copy(alpha = 0.58f),
                    p.secondaryDark.copy(alpha = 0.20f),
                    p.borderGlowDark.copy(alpha = 0.38f)
                )
            )
        } else {
            Brush.linearGradient(
                listOf(
                    p.borderGlowLight.copy(alpha = 0.45f),
                    p.secondaryLight.copy(alpha = 0.16f),
                    p.borderGlowLight.copy(alpha = 0.30f)
                )
            )
        }
    }

    fun buildColorScheme(cfg: VirusXThemeConfig): ColorScheme {
        val p = cfg.palette
        return if (cfg.isDarkMode) {
            darkColorScheme(
                primary = p.primaryDark,
                secondary = p.secondaryDark,
                background = p.darkBgBottom,
                surface = p.darkSurface,
                surfaceVariant = p.darkSurfaceVar,
                onPrimary = Color.White,
                onBackground = Color(0xFFF1F5F9),
                onSurface = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        } else {
            lightColorScheme(
                primary = p.primaryLight,
                secondary = p.secondaryLight,
                background = p.lightBgTop,
                surface = p.lightSurface,
                surfaceVariant = p.lightSurfaceVar,
                onPrimary = Color.White,
                onBackground = Color(0xFF18181B),
                onSurface = Color(0xFF18181B),
                onSurfaceVariant = Color(0xFF52525B)
            )
        }
    }
}

@Composable
fun VirusXAppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val cfg = VirusXThemeManager.activeConfigState
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VirusXThemeManager.buildBackgroundBrush(cfg)),
        content = content
    )
}

fun Modifier.virusXGlassCard(
    shape: Shape = RoundedCornerShape(18.dp),
    borderWidthDp: Float = 1.15f
): Modifier {
    val cfg = VirusXThemeManager.activeConfigState
    val surfaceColor = VirusXThemeManager.currentScheme.surface
    return this
        .clip(shape)
        .background(surfaceColor)
        .border(
            width = borderWidthDp.dp,
            brush = VirusXThemeManager.buildGlassBorderBrush(cfg),
            shape = shape
        )
}

@Composable
fun VirusXThemeAppearanceCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { VirusXThemeManager.init(context) }
    val cfg by VirusXThemeManager.config.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .virusXGlassCard(RoundedCornerShape(20.dp))
            .padding(16.dp),
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
                text = "المظهر وثيمات الواجهة الكاملة",
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
                val previewTop = if (cfg.isDarkMode) pal.darkBgTop else pal.lightBgTop
                val previewBot = if (cfg.isDarkMode) pal.darkBgBottom else pal.lightBgBottom
                val previewAccent = if (cfg.isDarkMode) pal.primaryDark else pal.primaryLight
                val previewSec = if (cfg.isDarkMode) pal.secondaryDark else pal.secondaryLight
                val isSelected = cfg.palette == pal

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier
                        .width(104.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(listOf(previewTop, previewBot)))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    previewAccent.copy(alpha = if (isSelected) 0.95f else 0.4f),
                                    previewSec.copy(alpha = if (isSelected) 0.7f else 0.2f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { VirusXThemeManager.setPalette(context, pal) }
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .shadow(if (isSelected) 8.dp else 0.dp, CircleShape, ambientColor = previewAccent, spotColor = previewAccent)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(previewAccent, previewSec))),
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
                        color = if (cfg.isDarkMode) Color(0xFFF1F5F9) else Color(0xFF18181B),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
