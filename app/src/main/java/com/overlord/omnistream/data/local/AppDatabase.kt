package com.overlord.omnistream.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.overlord.omnistream.data.local.dao.PlaybackStateDao
import com.overlord.omnistream.data.local.dao.PlaylistDao
import com.overlord.omnistream.data.local.dao.SubscriptionDao
import com.overlord.omnistream.data.local.entity.PlaybackStateEntity
import com.overlord.omnistream.data.local.entity.PlaylistItemEntity
import com.overlord.omnistream.data.local.entity.SubscriptionEntity

@Database(
    entities = [PlaylistItemEntity::class, PlaybackStateEntity::class, SubscriptionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omnistream_database.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
