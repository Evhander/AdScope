package com.finaticlabs.adscope.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyAdMetadataTest {

    @Test
    fun parsesThreeAdBlock() {
        val progress = SpotifyAdMetadata.parseProgress("Anuncio • 3 de 3")
        assertEquals(3, progress?.current)
        assertEquals(3, progress?.total)
        assertEquals("Anuncio 3 de 3", progress?.displayLabel)
    }

    @Test
    fun parsesArbitraryBlockSize() {
        val progress = SpotifyAdMetadata.parseProgress("Advertisement 2 of 4")
        assertEquals(2, progress?.current)
        assertEquals(4, progress?.total)
    }

    @Test
    fun rejectsInvalidProgressButKeepsExplicitDetection() {
        assertNull(SpotifyAdMetadata.parseProgress("Anuncio 4 de 3"))
        assertTrue(SpotifyAdMetadata.hasExplicitLabel("Anuncio 4 de 3"))
    }

    @Test
    fun ignoresNormalTrackMetadata() {
        assertNull(SpotifyAdMetadata.parseProgress("Waterfall", "Dynazty"))
    }
}
