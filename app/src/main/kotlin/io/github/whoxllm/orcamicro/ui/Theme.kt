package io.github.whoxllm.orcamicro.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Accent and neutral colors from the pinned desktop OrcaSlicer GUI_App.cpp / official logo.
private val LightColors = lightColorScheme(
    primary = Color(0xFF009688), onPrimary = Color.White, primaryContainer = Color(0xFFD4EFEB), onPrimaryContainer = Color(0xFF004D45),
    secondary = Color(0xFF00796B), secondaryContainer = Color(0xFFD4EFEB), onSecondaryContainer = Color(0xFF004D45),
    background = Color(0xFFF1F1F1), surface = Color.White, surfaceVariant = Color(0xFFE9E9E9),
    onSurface = Color(0xFF262523), onSurfaceVariant = Color(0xFF626262), outlineVariant = Color(0xFFD7D7D7),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF35BCAE), onPrimary = Color(0xFF002F2A), primaryContainer = Color(0xFF005C53), onPrimaryContainer = Color(0xFFD0F4EE),
    secondary = Color(0xFF35BCAE), secondaryContainer = Color(0xFF005C53), onSecondaryContainer = Color(0xFFD0F4EE),
    background = Color(0xFF242424), surface = Color(0xFF2B2B2B), surfaceVariant = Color(0xFF36363B),
    onSurface = Color(0xFFFAFAFA), onSurfaceVariant = Color(0xFFB2B3B5), outlineVariant = Color(0xFF4A4A4A),
)
private val CompactType = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
)
@Composable
fun OrcaMicroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = CompactType,
        shapes = Shapes(RoundedCornerShape(4.dp), RoundedCornerShape(6.dp), RoundedCornerShape(8.dp), RoundedCornerShape(10.dp), RoundedCornerShape(12.dp)),
        content = content,
    )
}
