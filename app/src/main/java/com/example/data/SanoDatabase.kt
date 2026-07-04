package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [WorkspaceFile::class], version = 1, exportSchema = false)
abstract class SanoDatabase : RoomDatabase() {
    abstract fun workspaceFileDao(): WorkspaceFileDao

    companion object {
        @Volatile
        private var INSTANCE: SanoDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): SanoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SanoDatabase::class.java,
                    "sano_database"
                )
                .addCallback(SanoDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class SanoDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.workspaceFileDao())
                }
            }
        }

        suspend fun populateDatabase(dao: WorkspaceFileDao) {
            // Python file
            dao.insertFile(
                WorkspaceFile(
                    name = "src/main.py",
                    language = "python",
                    content = """# Sano Python Workspace
# Supports Real-time Collaboration & Code Simulation

import time
import random

def greet_developers(team_members):
    print("🚀 Initializing Sano Cloud Collaboration Engine...")
    for member in team_members:
        print(f"✨ Welcome back, {member}!")
        time.sleep(0.1)

if __name__ == "__main__":
    developers = ["Alice (Python Expert)", "Bob (Frontend)", "Charlie (DBA)", "You"]
    greet_developers(developers)
"""
                )
            )

            // Kotlin file
            dao.insertFile(
                WorkspaceFile(
                    name = "src/App.kt",
                    language = "kotlin",
                    content = """package com.sano.editor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay

class CollaborativeEditor(val sessionId: String) {
    fun observeSyncStatus(): Flow<String> = flow {
        emit("Connecting to cloud database...")
        delay(1000)
        emit("Synchronized with Sano Cloud Node.")
    }
}

suspend fun main() {
    val editor = CollaborativeEditor("session_9821_x")
    editor.observeSyncStatus().collect { status ->
        println("🔄 [Sync] ${"$"}{status}")
    }
}
"""
                )
            )

            // HTML file
            dao.insertFile(
                WorkspaceFile(
                    name = "public/index.html",
                    language = "html",
                    content = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Sano Editor Preview</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <div class="card">
        <h1>Sano Real-time Editor</h1>
        <p>This is a live HTML/CSS render in Sano IDE!</p>
        <button id="sync-btn" onclick="triggerSync()">Sync Now</button>
    </div>
    <script src="script.js"></script>
</body>
</html>
"""
                )
            )

            // CSS file
            dao.insertFile(
                WorkspaceFile(
                    name = "public/style.css",
                    language = "css",
                    content = """body {
    background-color: #0b0f19;
    color: #f3f4f6;
    font-family: sans-serif;
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    margin: 0;
}

.card {
    background: linear-gradient(135deg, #1f2937, #111827);
    border: 1px solid #374151;
    padding: 2rem;
    border-radius: 12px;
    box-shadow: 0 4px 20px rgba(6, 182, 212, 0.15);
    text-align: center;
}

button {
    background-color: #06b6d4;
    color: #0b0f19;
    border: none;
    padding: 0.5rem 1.5rem;
    border-radius: 6px;
    cursor: pointer;
    font-weight: bold;
    transition: all 0.2s ease;
}

button:hover {
    box-shadow: 0 0 12px #06b6d4;
}
"""
                )
            )

            // JS file
            dao.insertFile(
                WorkspaceFile(
                    name = "public/script.js",
                    language = "javascript",
                    content = """function triggerSync() {
    console.log("⚡ [Sano Client] Manual synchronization triggered...");
    const btn = document.getElementById("sync-btn");
    btn.textContent = "Syncing...";
    btn.style.backgroundColor = "#10b981";
    
    setTimeout(() => {
        btn.textContent = "Synced!";
        console.log("✅ [Sano Client] Local state matched cloud database.");
    }, 1200);
}
"""
                )
            )

            // SQL file
            dao.insertFile(
                WorkspaceFile(
                    name = "db/queries.sql",
                    language = "sql",
                    content = """-- Sano Collaboration Session Database
SELECT 
    users.id, 
    users.name, 
    sessions.active_file, 
    sessions.last_sync_time 
FROM users 
INNER JOIN sessions ON users.id = sessions.user_id 
WHERE sessions.status = 'ACTIVE' 
ORDER BY sessions.last_sync_time DESC;
"""
                )
            )
        }
    }
}
