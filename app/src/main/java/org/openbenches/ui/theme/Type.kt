package org.openbenches.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.openbenches.R

// Varela Round, the website's only typeface. It has one weight, so "bold" is synthesised,
// just as browsers do for the website's buttons.
val VarelaRound = FontFamily(Font(R.font.varela_round, FontWeight.Normal))

private val base = Typography()

val Typography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = VarelaRound),
    headlineLarge = base.headlineLarge.copy(fontFamily = VarelaRound),
    headlineMedium = base.headlineMedium.copy(fontFamily = VarelaRound),
    headlineSmall = base.headlineSmall.copy(fontFamily = VarelaRound),
    titleLarge = base.titleLarge.copy(fontFamily = VarelaRound),
    titleMedium = base.titleMedium.copy(fontFamily = VarelaRound),
    titleSmall = base.titleSmall.copy(fontFamily = VarelaRound),
    bodyLarge = base.bodyLarge.copy(fontFamily = VarelaRound),
    bodyMedium = base.bodyMedium.copy(fontFamily = VarelaRound),
    bodySmall = base.bodySmall.copy(fontFamily = VarelaRound),
    labelLarge = base.labelLarge.copy(fontFamily = VarelaRound, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = VarelaRound),
    labelSmall = base.labelSmall.copy(fontFamily = VarelaRound),
)

/** The inscription on a bench plaque: large, centred, like #benchInscription on the website. */
val InscriptionStyle = TextStyle(
    fontFamily = VarelaRound,
    fontSize = 21.sp,
    lineHeight = 28.sp,
    color = SoftBlack,
)
