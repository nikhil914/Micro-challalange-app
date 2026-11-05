package com.microchallenge.app.ui

import androidx.compose.ui.graphics.Color

/**
 * Game palette. Hues chosen to stay distinguishable for common color-vision
 * deficiencies (based on Okabe-Ito), and every color has a text label so no
 * game relies on color alone.
 */
enum class GameColors(val color: Color, val label: String) {
    RED(Color(0xFFD55E00), "Red"),
    GREEN(Color(0xFF009E73), "Green"),
    BLUE(Color(0xFF0072B2), "Blue"),
    YELLOW(Color(0xFFF0E442), "Yellow"),
    PURPLE(Color(0xFFCC79A7), "Purple"),
    ORANGE(Color(0xFFE69F00), "Orange"),
}
