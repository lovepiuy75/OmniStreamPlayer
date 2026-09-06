package com.overlord.omnistream

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConfigBackupJsonTest {

    @Test
    fun testBackupJsonStructure() {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        val subs = JSONArray()
        val s1 = JSONObject().apply {
            put("id", "folder123")
            put("name", "創辦人故事")
            put("type", "GDRIVE")
            put("publicUrl", "https://drive.google.com/drive/folders/folder123")
            put("autoAddToPlaylist", true)
            put("isPlaylist", false)
        }
        subs.put(s1)
        root.put("subscriptions", subs)

        val groups = JSONArray()
        val g1 = JSONObject().apply {
            put("id", "grp_1")
            put("name", "我的群組")
        }
        groups.put(g1)
        root.put("playlist_groups", groups)

        val jsonStr = root.toString(2)
        assertNotNull(jsonStr)

        val parsed = JSONObject(jsonStr)
        assertEquals(1, parsed.getInt("version"))
        assertEquals(1, parsed.getJSONArray("subscriptions").length())
        assertEquals("創辦人故事", parsed.getJSONArray("subscriptions").getJSONObject(0).getString("name"))
        assertEquals("grp_1", parsed.getJSONArray("playlist_groups").getJSONObject(0).getString("id"))
    }
}
