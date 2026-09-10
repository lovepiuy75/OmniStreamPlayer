package com.overlord.omnistream

import com.overlord.omnistream.data.gdrive.GoogleDriveService
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleDriveUrlTest {

    @Test
    fun testExtractFolderId() {
        val url1 = "https://drive.google.com/drive/folders/1p_9goY5IKN5uQEyEUPvqsa77RYsJcpy9?usp=drive_link"
        val expected = "1p_9goY5IKN5uQEyEUPvqsa77RYsJcpy9"
        assertEquals(expected, GoogleDriveService.extractFolderId(url1))

        val url2 = "https://drive.google.com/open?id=1p_9goY5IKN5uQEyEUPvqsa77RYsJcpy9"
        assertEquals(expected, GoogleDriveService.extractFolderId(url2))

        val rawId = "1p_9goY5IKN5uQEyEUPvqsa77RYsJcpy9"
        assertEquals(expected, GoogleDriveService.extractFolderId(rawId))
    }

    @Test
    fun testBuildPublicStreamUrl() {
        val service = GoogleDriveService()
        val fileId = "1KsivPBQzvDyVjGQvlli_f7bwYQthZdIp"
        val streamUrl = service.buildPublicStreamUrl(fileId)
        assertEquals("https://drive.google.com/uc?export=download&id=1KsivPBQzvDyVjGQvlli_f7bwYQthZdIp", streamUrl)
    }

    @Test
    fun testNaturalCompare() {
        val list = listOf("ep10", "ep1", "ep2", "ep80", "ep3")
        val sorted = list.sortedWith { a, b -> GoogleDriveService.naturalCompare(a, b) }
        assertEquals(listOf("ep1", "ep2", "ep3", "ep10", "ep80"), sorted)
    }

    @Test
    fun testFetchFolderAudioFilesOver50Limit() = kotlinx.coroutines.runBlocking {
        val service = GoogleDriveService()
        val folderId = "1p_9goY5IKN5uQEyEUPvqsa77RYsJcpy9"
        val files = service.fetchFolderAudioFiles(folderId, "創辦人故事")
        // 原本停在 50 筆，現在透過 Drive v3 API 與分頁機制取得全部最新音訊（目前已有 85 首以上）
        org.junit.Assert.assertTrue("檔案數量應 >= 85，實測為: ${files.size}", files.size >= 85)
        // 驗證首尾自然排序
        org.junit.Assert.assertTrue(files.first().title.contains("ep1_"))
        val lastTitle = files.last().title
        org.junit.Assert.assertTrue(lastTitle.contains("ep8") || lastTitle.contains("ep9") || lastTitle.contains("ep10"))
    }
}

