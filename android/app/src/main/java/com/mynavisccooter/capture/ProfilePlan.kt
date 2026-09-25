package com.mynavisccooter.capture

enum class ScooterProfile { ORIGINAL, PERSONALIZED }

data class OriginalSettings(
    val ecoSpeed: Int,
    val driveSpeed: Int,
    val sportSpeed: Int,
    val zeroStart: Boolean
)

data class PersonalizedSettings(
    val driveSpeed: Int = 25,
    val sportSpeed: Int = 30,
    val zeroStart: Boolean
)

/**
 * Pure decision model. It produces a write plan only after an authenticated,
 * verified backup exists; it never sends Bluetooth commands itself.
 */
object ProfilePlan {
    fun originalPlan(original: OriginalSettings): OriginalSettings = original

    fun personalizedPlan(original: OriginalSettings, settings: PersonalizedSettings): OriginalSettings =
        original.copy(
            ecoSpeed = original.ecoSpeed,
            driveSpeed = settings.driveSpeed,
            sportSpeed = settings.sportSpeed,
            zeroStart = settings.zeroStart
        )

    fun canApply(authenticated: Boolean, backupVerified: Boolean, serialMatches: Boolean): Boolean =
        authenticated && backupVerified && serialMatches
}
