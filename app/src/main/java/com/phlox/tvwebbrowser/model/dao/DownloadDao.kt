package com.phlox.tvwebbrowser.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.phlox.tvwebbrowser.model.Download

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads")
    suspend fun getAll(): List<Download>

    @Insert
    suspend fun insert(download: Download): Long

    @Update
    suspend fun update(vararg downloads: Download)

    @Delete
    suspend fun delete(download: Download)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("SELECT * FROM downloads ORDER BY time DESC LIMIT 100 OFFSET :offset")
    suspend fun allByLimitOffset(offset: Long): List<Download>
}
