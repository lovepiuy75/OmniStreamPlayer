package com.overlord.omnistream

import com.overlord.omnistream.data.youtube.YouTubePlaylistParser
import com.overlord.omnistream.data.youtube.YouTubeRssParser
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeUrlAndFilterTest {

    @Test
    fun testExtractChannelId() {
        val url = "https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw"
        val expected = "UC_x5XG1OV2P6uZZ5FSM9Ttw"
        assertEquals(expected, YouTubeRssParser.extractChannelId(url))
    }

    @Test
    fun testExtractPlaylistId() {
        val url = "https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"
        val expected = "PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"
        assertEquals(expected, YouTubePlaylistParser.extractPlaylistId(url))
    }
}
