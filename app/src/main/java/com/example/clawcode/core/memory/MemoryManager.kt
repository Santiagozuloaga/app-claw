package com.example.clawcode.core.memory

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Módulo 5 - Memory
 * Gestiona base de datos vectorial (simulada), memoria semántica, RAG, etc.
 */
class MemoryManager(context: Context) {
    private val memoryDao = MemoryDatabase.getDatabase(context).memoryDao()

    suspend fun savePreference(key: String, value: String) {
        memoryDao.savePreference(MemoryEntity(key, value))
    }
    
    suspend fun getPreference(key: String): String? {
        return memoryDao.getPreference(key)
    }

    fun getAllPreferences(): Flow<Map<String, String>> {
        return memoryDao.getAllPreferences().map { list ->
            list.associate { it.key to it.value }
        }
    }
}
