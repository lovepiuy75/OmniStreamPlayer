package com.overlord.omnistream.data.local.dao

import androidx.room.*
import com.overlord.omnistream.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE type = :type")
    fun getByTypeFlow(type: String): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions")
    suspend fun getAll(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE subscriptions SET lastSyncedTime = :time WHERE id = :id")
    suspend fun updateLastSyncedTime(id: String, time: Long)

    @Query("UPDATE subscriptions SET name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String)

    @Query("UPDATE subscriptions SET targetPlaylistGroupId = :groupId WHERE id = :id")
    suspend fun updateTargetPlaylistGroup(id: String, groupId: String)
}
