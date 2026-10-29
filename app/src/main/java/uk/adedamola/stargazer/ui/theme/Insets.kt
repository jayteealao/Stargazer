package uk.adedamola.stargazer.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

/**
 * The system bars plus the display cutout. Screens pad their content with it so nothing sits
 * under a bar or the camera; the keyboard is not included and is handled per screen.
 */
val WindowInsets.Companion.safeBars: WindowInsets
    @Composable get() = systemBars.union(displayCutout)
