package com.kiwicup.scheduledmessenger.core

/** How the app picks light or dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** A named accent color the user can pick from (ARGB). */
data class ColorPreset(val name: String, val argb: Int)

/** Tonal roles derived from one seed color; enough to build a Material 3 scheme without a library. */
data class DerivedPalette(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val surfaceTint: Int
)

/**
 * Pure ARGB arithmetic: luminance, blending and palette derivation.
 * Kept in `core` so the rules are unit-tested without Android's Color class.
 */
object ThemeColors {

    val presets: List<ColorPreset> = listOf(
        ColorPreset("Teal", 0xFF1E5F74.toInt()),
        ColorPreset("Indigo", 0xFF3F51B5.toInt()),
        ColorPreset("Purple", 0xFF7E57C2.toInt()),
        ColorPreset("Pink", 0xFFD81B60.toInt()),
        ColorPreset("Red", 0xFFC62828.toInt()),
        ColorPreset("Orange", 0xFFEF6C00.toInt()),
        ColorPreset("Amber", 0xFFF9A825.toInt()),
        ColorPreset("Green", 0xFF2E7D32.toInt()),
        ColorPreset("Cyan", 0xFF00838F.toInt()),
        ColorPreset("Blue", 0xFF1565C0.toInt()),
        ColorPreset("Brown", 0xFF6D4C41.toInt()),
        ColorPreset("Slate", 0xFF455A64.toInt())
    )

    val defaultSeed: Int = presets.first().argb
    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val BLACK: Int = 0xFF000000.toInt()

    fun alpha(argb: Int): Int = (argb ushr 24) and 0xFF
    fun red(argb: Int): Int = (argb shr 16) and 0xFF
    fun green(argb: Int): Int = (argb shr 8) and 0xFF
    fun blue(argb: Int): Int = argb and 0xFF

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** WCAG relative luminance, 0 (black) to 1 (white). */
    fun luminance(argb: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(red(argb)) + 0.7152 * channel(green(argb)) + 0.0722 * channel(blue(argb))
    }

    /** WCAG contrast ratio between two opaque colors, 1 to 21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a) + 0.05
        val lb = luminance(b) + 0.05
        return if (la > lb) la / lb else lb / la
    }

    /** White or black, whichever reads better on [background]. */
    fun readableOn(background: Int): Int =
        if (contrast(background, WHITE) >= contrast(background, BLACK)) WHITE else BLACK

    /** Linear blend of [from] toward [to] by [fraction] in 0..1; alpha is kept opaque. */
    fun blend(from: Int, to: Int, fraction: Double): Int {
        val f = fraction.coerceIn(0.0, 1.0)
        fun mix(x: Int, y: Int) = Math.round(x + (y - x) * f).toInt()
        return argb(255, mix(red(from), red(to)), mix(green(from), green(to)), mix(blue(from), blue(to)))
    }

    /**
     * Derives container tones from a seed. Light schemes lighten toward white, dark schemes
     * darken toward black, and every "on" color is chosen for contrast rather than assumed.
     */
    fun derive(seed: Int, dark: Boolean): DerivedPalette {
        val opaqueSeed = argb(255, red(seed), green(seed), blue(seed))
        val primary = if (dark) blend(opaqueSeed, WHITE, 0.35) else opaqueSeed
        val primaryContainer = if (dark) blend(opaqueSeed, BLACK, 0.55) else blend(opaqueSeed, WHITE, 0.80)
        val secondaryContainer = if (dark) blend(opaqueSeed, BLACK, 0.70) else blend(opaqueSeed, WHITE, 0.88)
        return DerivedPalette(
            primary = primary,
            onPrimary = readableOn(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = readableOn(primaryContainer),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = readableOn(secondaryContainer),
            surfaceTint = primary
        )
    }

    /** Parses "#RRGGBB" or "#AARRGGBB"; null for anything else. */
    fun parseHex(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        if (hex.length != 6 && hex.length != 8) return null
        val value = hex.toLongOrNull(16) ?: return null
        return if (hex.length == 6) (0xFF000000L or value).toInt() else value.toInt()
    }

    fun toHex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)
}
