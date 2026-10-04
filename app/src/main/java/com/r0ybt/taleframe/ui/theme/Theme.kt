package com.r0ybt.taleframe.ui.theme

import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary=Color(0xFF8E435F),onPrimary=Color.White,primaryContainer=Color(0xFFF6D8E3),onPrimaryContainer=Color(0xFF39202A),
    secondary=Color(0xFF705A64),onSecondary=Color.White,secondaryContainer=Color(0xFFEEDFE5),onSecondaryContainer=Color(0xFF30252A),
    tertiary=Color(0xFF53665D),background=Color(0xFFFFF8F9),onBackground=Color(0xFF30292D),surface=Color(0xFFFFF8F9),onSurface=Color(0xFF30292D),
    surfaceVariant=Color(0xFFF0E5E9),onSurfaceVariant=Color(0xFF5A4C53),outline=Color(0xFF897780),surfaceContainer=Color(0xFFF7EDF0),surfaceContainerHigh=Color(0xFFF0E5E9),surfaceContainerLow=Color(0xFFFAEFF2),surfaceContainerLowest=Color(0xFFFFFBFC),surfaceContainerHighest=Color(0xFFEADFE3),surfaceTint=Color(0xFF8E435F),outlineVariant=Color(0xFFD7C3CD))
private val Dark = darkColorScheme(
    primary=Color(0xFFE8AFC5),onPrimary=Color(0xFF542338),primaryContainer=Color(0xFF68334A),onPrimaryContainer=Color(0xFFFFD9E7),
    secondary=Color(0xFFD5BCC7),onSecondary=Color(0xFF3B2C34),secondaryContainer=Color(0xFF4E3B45),onSecondaryContainer=Color(0xFFF0DDE6),
    tertiary=Color(0xFFB1CEC0),background=Color(0xFF201C20),onBackground=Color(0xFFEDE4E8),surface=Color(0xFF201C20),onSurface=Color(0xFFEDE4E8),
    surfaceVariant=Color(0xFF493F46),onSurfaceVariant=Color(0xFFD2C3CB),outline=Color(0xFF9D8C96),surfaceContainer=Color(0xFF2A2429),surfaceContainerHigh=Color(0xFF352E34),surfaceContainerLow=Color(0xFF282227),surfaceContainerLowest=Color(0xFF171417),surfaceContainerHighest=Color(0xFF40363D),surfaceTint=Color(0xFFE8AFC5),outlineVariant=Color(0xFF584952))
@Composable
fun TaleFrameTheme(content:@Composable ()->Unit) {
    val context=LocalContext.current
    val preferences=remember {context.getSharedPreferences("editor",0)}
    var mode by remember {mutableStateOf(preferences.getString("theme","system"))}
    DisposableEffect(preferences) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener {p,key->if(key=="theme") mode=p.getString("theme","system")}
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {preferences.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val dark=when(mode) {"dark"->true;"light"->false;else->isSystemInDarkTheme()}
    SideEffect {(context as? ComponentActivity)?.enableEdgeToEdge(statusBarStyle=if(dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),navigationBarStyle=if(dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))}
    MaterialTheme(colorScheme=if(dark) Dark else Light,typography=Typography,shapes=Shapes(small=RoundedCornerShape(8.dp),medium=RoundedCornerShape(12.dp),large=RoundedCornerShape(16.dp)),content=content)
}
