package com.example.data

import kotlinx.coroutines.flow.Flow

class WorkspaceRepository(private val dao: WorkspaceFileDao) {
    val allFiles: Flow<List<WorkspaceFile>> = dao.getAllFiles()

    fun getFileById(id: Int): Flow<WorkspaceFile?> = dao.getFileById(id)

    suspend fun insertFile(file: WorkspaceFile): Long = dao.insertFile(file)

    suspend fun updateFile(file: WorkspaceFile) = dao.updateFile(file)

    suspend fun deleteFile(file: WorkspaceFile) = dao.deleteFile(file)

    suspend fun deleteFileById(id: Int) = dao.deleteFileById(id)
}
