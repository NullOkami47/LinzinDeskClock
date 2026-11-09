package com.linzin.linzindeskclock.display

/** Orientation policy shared by manual, lock-screen and dream hosts. */
enum class LandscapeOrientation { NORMAL, REVERSE, SENSOR }

fun clockOrientation(
    hasHingeSensor: Boolean,
    isMotorolaDevice: Boolean,
    isTallAspectRatio: Boolean,
    flipScreen180: Boolean,
    savedOrientation: LandscapeOrientation? = null
): LandscapeOrientation = when {
    flipScreen180 -> savedOrientation?.takeUnless { it == LandscapeOrientation.SENSOR }
        ?: LandscapeOrientation.NORMAL
    !hasHingeSensor || isMotorolaDevice || isTallAspectRatio -> LandscapeOrientation.SENSOR
    else -> LandscapeOrientation.REVERSE
}

/** Android Surface.ROTATION_90 = 1 and ROTATION_270 = 3. */
fun oppositeLandscapeOrientation(rotation: Int): LandscapeOrientation =
    if (rotation == 1) LandscapeOrientation.REVERSE else LandscapeOrientation.NORMAL
