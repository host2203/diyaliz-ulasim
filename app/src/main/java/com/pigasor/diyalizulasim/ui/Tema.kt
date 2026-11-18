package com.pigasor.diyalizulasim.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Açık tema renkleri: mavi ana renk, gri-mavi ikincil, yeşilimsi vurgu.
private val AcikRenkler = lightColorScheme(
    primary = Color(0xFF2E5AAC),
    secondary = Color(0xFF565E71),
    tertiary = Color(0xFF2C6B62)
) // AcikRenkler: gündüz modunda kullanılacak okunaklı ve sakin renk paletini tanımlar.

// Koyu tema renkleri: aynı paletin gece modunda okunabilen açık tonları.
private val KoyuRenkler = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    secondary = Color(0xFFBFC6DC),
    tertiary = Color(0xFFA0CFC8)
) // KoyuRenkler: gece modunda kontrastı koruyan renkleri tanımlar.

// Uygulamanın temel yazı stili: gövde metinleri için boyut ve satır aralığı.
private val UygulamaTipografisi = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
) // UygulamaTipografisi: tüm ekranlarda tutarlı gövde metni görünümü sağlar.

@Composable
fun DiyalizUlasimTheme(
    koyuTema: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (koyuTema) KoyuRenkler else AcikRenkler,
        typography = UygulamaTipografisi,
        content = content
    )
} // DiyalizUlasimTheme: sistemin açık/koyu moduna göre tüm uygulamaya renk ve tipografi uygular.
