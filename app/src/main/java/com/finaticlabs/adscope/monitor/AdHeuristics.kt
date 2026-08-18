package com.finaticlabs.adscope.monitor

import java.util.Locale

object AdHeuristics {

    private val explicitAdPattern = Regex(
        pattern = """\b(advertisement|advert|publicidad|anuncio|anuncios|commercial|sponsored|patrocinado|patrocinada|ad break|pausa publicitaria)\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val promotionalPattern = Regex(
        pattern = """\b(promo|promotion|promoción|oferta especial|special offer)\b""",
        option = RegexOption.IGNORE_CASE
    )

    fun assess(
        packageName: String,
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long?,
        isPlaying: Boolean,
        canSeek: Boolean,
        canSkipNext: Boolean
    ): AdAssessment {
        var score = 0
        val reasons = mutableListOf<String>()
        val combinedText = listOf(title, artist, album)
            .filterNotNull()
            .joinToString(" ")
            .trim()
            .lowercase(Locale.ROOT)

        val spotifyExplicitLabel = packageName == "com.spotify.music" &&
            SpotifyAdMetadata.hasExplicitLabel(title, artist, album)

        if (spotifyExplicitLabel) {
            score = 100
            reasons += "Spotify explicitly identifies this content as advertising."
        } else if (explicitAdPattern.containsMatchIn(combinedText)) {
            score += 75
            reasons += "The metadata contains an explicit advertising keyword."
        } else if (promotionalPattern.containsMatchIn(combinedText)) {
            score += 45
            reasons += "The metadata looks promotional."
        }

        if (isPlaying && title.isNullOrBlank() && artist.isNullOrBlank()) {
            score += 35
            reasons += "It is playing, but title and artist are missing."
        } else if (isPlaying && (title.isNullOrBlank() || artist.isNullOrBlank())) {
            score += 15
            reasons += "Part of the usual content information is missing."
        }

        if (durationMs != null && durationMs in 5_000L..120_000L) {
            score += 15
            reasons += "The duration falls within a common ad range."
        }

        if (isPlaying && !canSeek) {
            score += 10
            reasons += "The session does not allow seeking to a specific position."
        }

        if (isPlaying && !canSkipNext) {
            score += 10
            reasons += "The session does not allow skipping to the next item."
        }

        if (packageName == "com.spotify.music" &&
            isPlaying &&
            (title.isNullOrBlank() || artist.isNullOrBlank())
        ) {
            score += 10
            reasons += "On Spotify, the active session has incomplete metadata."
        }

        val finalScore = score.coerceIn(0, 100)
        val level = when {
            finalScore >= 70 -> DetectionLevel.POSSIBLE_AD
            finalScore >= 40 -> DetectionLevel.SUSPICIOUS
            else -> DetectionLevel.NORMAL
        }

        if (reasons.isEmpty()) {
            reasons += "No relevant advertising signals were found."
        }

        return AdAssessment(
            score = finalScore,
            level = level,
            reasons = reasons
        )
    }
}
