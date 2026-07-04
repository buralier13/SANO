package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workspace_files")
data class WorkspaceFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val content: String,
    val language: String,
    val isReadOnly: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
