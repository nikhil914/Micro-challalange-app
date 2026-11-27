package com.microchallenge.app.ui.design

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.microchallenge.app.R

/** Bundled OFL fonts (see docs/CREDITS.md). Outfit for headings and numbers, Inter for body text. */
object AppFonts {
    val display: FontFamily = FontFamily(
        Font(R.font.outfit_medium, FontWeight.Medium),
        Font(R.font.outfit_semibold, FontWeight.SemiBold),
        Font(R.font.outfit_bold, FontWeight.Bold),
        Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
        Font(R.font.outfit_black, FontWeight.Black),
    )
    val body: FontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )
}
