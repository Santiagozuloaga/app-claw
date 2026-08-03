package com.example.clawcode.core.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreference(memory: MemoryEntity)

    @Query("SELECT value FROM memory_table WHERE `key` = :key")
    suspend fun getPreference(key: String): String?

    @Query("SELECT * FROM memory_table")
    fun getAllPreferences(): Flow<List<MemoryEntity>>
}
