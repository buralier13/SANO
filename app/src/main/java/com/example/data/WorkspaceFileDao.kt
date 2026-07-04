package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceFileDao {
    @Query("SELECT * FROM workspace_files ORDER BY name ASC")
    fun getAllFiles(): Flow<List<WorkspaceFile>>

    @Query("SELECT * FROM workspace_files WHERE id = :id LIMIT 1")
    fun getFileById(id: Int): Flow<WorkspaceFile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: WorkspaceFile): Long

    @Update
    suspend fun updateFile(file: WorkspaceFile)

    @Delete
    suspend fun deleteFile(file: WorkspaceFile)

    @Query("DELETE FROM workspace_files WHERE id = :id")
    suspend fun deleteFileById(id: Int)
}
