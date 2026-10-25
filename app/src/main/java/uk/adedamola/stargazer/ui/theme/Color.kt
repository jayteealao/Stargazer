package uk.adedamola.stargazer.ui.theme

import androidx.compose.ui.graphics.Color

val FactoryOrange = Color(0xFFFF6600)
val FactoryCyan = Color(0xFF00CCFF)
val FactoryYellow = Color(0xFFFFCC00)
val FactoryDarkGrey = Color(0xFF1A1A1A)
val FactorySurfaceGrey = Color(0xFF2D2D2D)
val FactoryTextWhite = Color(0xFFF0F0F0)
val FactoryTextGrey = Color(0xFFAAAAAA)
val FactoryBorder = Color(0xFF444444)
// Fill behind yellow warnings: the access-ended banner and failure messages.
val FactoryWarnContainer = Color(0xFF3A2A00)
// Outline of a resting control, such as the STAR toggle.
val FactoryRestOutline = Color.White.copy(alpha = 0.3f)
// Label and outline of a control that cannot act right now (offline, rate limited).
val FactoryDisabledText = Color(0xFF666666)
val FactoryDisabledBorder = Color(0xFF333333)
// Unselected rows and radio rings in the drawer's star lists.
val FactoryMutedWhite = Color.White.copy(alpha = 0.6f)
// Loading skeleton blocks: the drawer lists and the profile.
val FactorySkeleton = Color.White.copy(alpha = 0.1f)
val FactorySkeletonFaint = Color.White.copy(alpha = 0.06f)
// Avatar initials circle when no picture loads: one tint per account, picked by user id.
val FactoryAvatarTints = listOf(Color(0xFFFFB380), Color(0xFF80E5FF), Color(0xFFFFE680))
// Avatar initials circle for an account with no name.
val FactoryNoNameTint = Color(0xFFCCCCCC)

// Material 3 Color Schemes
val Primary = FactoryOrange
val OnPrimary = Color.White
val PrimaryContainer = Color(0xFF331400)
val OnPrimaryContainer = Color(0xFFFFCCAA)

val Secondary = Color(0xFF00CCFF) // Cyan accent for tech feel
val OnSecondary = Color.Black
val SecondaryContainer = Color(0xFF004455)
val OnSecondaryContainer = Color(0xFFAAEEFF)

val Tertiary = Color(0xFFFFCC00) // Yellow warning color
val OnTertiary = Color.Black

val Background = FactoryDarkGrey
val OnBackground = FactoryTextWhite
val Surface = FactorySurfaceGrey
val OnSurface = FactoryTextWhite
