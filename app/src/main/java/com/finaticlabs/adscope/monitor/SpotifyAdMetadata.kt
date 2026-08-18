package com.finaticlabs.adscope.monitor

/** Parsed position inside a Spotify advertising block, for example 3 of 3. */
data class SpotifyAdProgress(
    val current: Int,
    val total: Int
) {
    val displayLabel: String
        get() = "Ad $current of $total"
}

/**
 * Parses Spotify's public media metadata without assuming a fixed block size.
 * Handles labels such as "Anuncio • 1 de 3" and "Advertisement 2 of 4".
 */
object SpotifyAdMetadata {

    private val explicitLabelPattern = Regex(
        pattern = """\b(anuncio|anuncios|advertisement|advertisements|publicidad|ad break)\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val progressPattern = Regex(
        pattern = """\b(?:anuncio|anuncios|advertisement|advertisements|publicidad)\b\s*(?:[•·\-–—:]\s*)?(\d{1,2})\s*(?:de|of)\s*(\d{1,2})\b""",
        option = RegexOption.IGNORE_CASE
    )

    fun parseProgress(vararg values: String?): SpotifyAdProgress? {
        values.forEach { value ->
            if (value.isNullOrBlank()) return@forEach
            val match = progressPattern.find(value) ?: return@forEach
            val current = match.groupValues[1].toIntOrNull() ?: return@forEach
            val total = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (current >= 1 && total >= current) {
                return SpotifyAdProgress(current = current, total = total)
            }
        }
        return null
    }

    fun hasExplicitLabel(vararg values: String?): Boolean =
        values.any { value -> !value.isNullOrBlank() && explicitLabelPattern.containsMatchIn(value) }

    fun displayLabel(vararg values: String?): String? {
        parseProgress(*values)?.let { return it.displayLabel }
        return values.firstOrNull { value ->
            !value.isNullOrBlank() && explicitLabelPattern.containsMatchIn(value)
        }
    }
}
