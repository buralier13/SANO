package com.example.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.SanoDatabase
import com.example.data.WorkspaceFile
import com.example.data.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import java.util.regex.Pattern

data class Collaborator(
    val name: String,
    val role: String,
    val color: Color,
    val status: String // "Idle", "Typing...", "Offline"
)

data class SyncLog(
    val timestamp: String,
    val sender: String,
    val action: String,
    val type: LogType
)

enum class LogType {
    INFO, SUCCESS, WARNING, CODE_EDIT
}

data class ChatMessage(
    val sender: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isUser: Boolean = false,
    val isAI: Boolean = false,
    val isTyping: Boolean = false
)

class SanoViewModel(application: Application) : AndroidViewModel(application) {
    private val database = SanoDatabase.getDatabase(application, viewModelScope)
    private val repository = WorkspaceRepository(database.workspaceFileDao())

    // Files State
    val allFiles: StateFlow<List<WorkspaceFile>> = repository.allFiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Editor tab management
    private val _openFiles = MutableStateFlow<List<WorkspaceFile>>(emptyList())
    val openFiles: StateFlow<List<WorkspaceFile>> = _openFiles.asStateFlow()

    private val _activeFile = MutableStateFlow<WorkspaceFile?>(null)
    val activeFile: StateFlow<WorkspaceFile?> = _activeFile.asStateFlow()

    // Edited content buffer (to avoid typing lag)
    private val _currentFileContent = MutableStateFlow("")
    val currentFileContent: StateFlow<String> = _currentFileContent.asStateFlow()

    // Interactive Collaboration State
    private val _isCollabActive = MutableStateFlow(true)
    val isCollabActive: StateFlow<Boolean> = _isCollabActive.asStateFlow()

    private val _collaborators = MutableStateFlow<List<Collaborator>>(
        listOf(
            Collaborator("Alice", "Python Dev", Color(0xFF10B981), "Idle"),
            Collaborator("Bob", "Frontend Architect", Color(0xFF38BDF8), "Idle"),
            Collaborator("Charlie", "SQL DBA", Color(0xFFFB923C), "Idle"),
            Collaborator("Gemini AI", "Sano Co-pilot", Color(0xFFA78BFA), "Online")
        )
    )
    val collaborators: StateFlow<List<Collaborator>> = _collaborators.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<SyncLog>>(emptyList())
    val syncLogs: StateFlow<List<SyncLog>> = _syncLogs.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Chat State
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage("Sano System", "Collaboration session #9821 established. Sano Cloud Node is active.", isAI = false),
            ChatMessage("Alice", "Hey everyone! Ready to build something awesome. Let's write some code!", isAI = true),
            ChatMessage("Gemini AI", "Hello! I am Sano Co-pilot. Ask me to write code, debug, or help explain functions. You'll see me type directly into the file!", isAI = true)
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    // Console output state
    private val _consoleOutput = MutableStateFlow<List<String>>(
        listOf(
            "Sano Developer Console v1.0.0",
            "Type 'help' in terminal below to see available commands.",
            "Ready."
        )
    )
    val consoleOutput: StateFlow<List<String>> = _consoleOutput.asStateFlow()

    // Command line Terminal state
    private val _terminalCommand = MutableStateFlow("")
    val terminalCommand: StateFlow<String> = _terminalCommand.asStateFlow()

    // Active Sidebar Tab
    private val _activeSidebarTab = MutableStateFlow("files") // "files", "search", "collab", "chat", "settings"
    val activeSidebarTab: StateFlow<String> = _activeSidebarTab.asStateFlow()

    // Expanded Folder paths State
    private val _expandedFolders = MutableStateFlow<Set<String>>(emptySet())
    val expandedFolders: StateFlow<Set<String>> = _expandedFolders.asStateFlow()

    fun toggleFolderExpanded(folderPath: String) {
        val current = _expandedFolders.value
        if (current.contains(folderPath)) {
            _expandedFolders.value = current - folderPath
        } else {
            _expandedFolders.value = current + folderPath
        }
    }

    // Autocomplete quick bar symbols
    val quickSymbols = listOf("{", "}", "(", ")", ";", "[", "]", "\t", "=", ":", "+", "\"", "'")

    // Active cursor collaborator text position
    private val _remoteCursorIndex = MutableStateFlow<Int?>(null)
    val remoteCursorIndex: StateFlow<Int?> = _remoteCursorIndex.asStateFlow()

    private val _remoteCursorColor = MutableStateFlow(Color.Transparent)
    val remoteCursorColor: StateFlow<Color> = _remoteCursorColor.asStateFlow()

    private val _remoteCursorName = MutableStateFlow("")
    val remoteCursorName: StateFlow<String> = _remoteCursorName.asStateFlow()

    // Search query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Pair<WorkspaceFile, Int>>>(emptyList())
    val searchResults: StateFlow<List<Pair<WorkspaceFile, Int>>> = _searchResults.asStateFlow()

    // Active theme
    private val _selectedTheme = MutableStateFlow("Monokai Cyber") // "Monokai Cyber", "One Dark Pro", "Nord Slate", "Solarized Light"
    val selectedTheme: StateFlow<String> = _selectedTheme.asStateFlow()

    private var collabJob: Job? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        // Automatically open files when loaded
        viewModelScope.launch {
            allFiles.collect { files ->
                if (files.isNotEmpty()) {
                    if (_activeFile.value == null) {
                        // Open main.py or the first file
                        val mainFile = files.find { it.name == "main.py" } ?: files.first()
                        openFile(mainFile)
                    }
                }
            }
        }

        // Start collaboration simulation
        startCollabSimulation()
        addLog("System", "Sano workspace successfully initialized", LogType.INFO)
    }

    fun selectSidebarTab(tab: String) {
        _activeSidebarTab.value = tab
    }

    fun setTheme(theme: String) {
        _selectedTheme.value = theme
        addLog("System", "IDE Theme changed to $theme", LogType.INFO)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            val results = mutableListOf<Pair<WorkspaceFile, Int>>()
            allFiles.value.forEach { file ->
                val count = file.content.split(query).size - 1
                if (count > 0) {
                    results.add(file to count)
                }
            }
            _searchResults.value = results
        }
    }

    fun openFile(file: WorkspaceFile) {
        val currentOpen = _openFiles.value.toMutableList()
        if (!currentOpen.any { it.id == file.id }) {
            currentOpen.add(file)
            _openFiles.value = currentOpen
        }
        _activeFile.value = file
        _currentFileContent.value = file.content
        addLog("System", "Opened file: ${file.name}", LogType.INFO)
    }

    fun closeFile(file: WorkspaceFile) {
        val currentOpen = _openFiles.value.toMutableList()
        currentOpen.removeAll { it.id == file.id }
        _openFiles.value = currentOpen

        if (_activeFile.value?.id == file.id) {
            if (currentOpen.isNotEmpty()) {
                openFile(currentOpen.last())
            } else {
                _activeFile.value = null
                _currentFileContent.value = ""
            }
        }
    }

    fun updateContent(newContent: String) {
        _currentFileContent.value = newContent
        val file = _activeFile.value ?: return
        
        // Auto-save debounced in SQLite
        viewModelScope.launch {
            val updated = file.copy(content = newContent, updatedAt = System.currentTimeMillis())
            repository.updateFile(updated)
            // Keep active file sync
            _activeFile.value = updated
            
            // Simulating Cloud sync indicator
            _isSyncing.value = true
            delay(300)
            _isSyncing.value = false
        }
    }

    fun insertTextAtCursor(cursorPosition: Int, symbol: String): Int {
        val content = _currentFileContent.value
        val before = content.substring(0, cursorPosition)
        val after = content.substring(cursorPosition)
        val result = before + symbol + after
        updateContent(result)
        return cursorPosition + symbol.length
    }

    fun createNewFile(name: String, language: String, parentPath: String = "") {
        viewModelScope.launch {
            val extension = when(language.lowercase()) {
                "python" -> "py"
                "kotlin" -> "kt"
                "html" -> "html"
                "css" -> "css"
                "javascript" -> "js"
                "sql" -> "sql"
                else -> "txt"
            }
            val fileBase = if (name.contains(".")) name else "$name.$extension"
            val fileName = if (parentPath.isEmpty()) fileBase else "$parentPath$fileBase"

            // Check if file already exists
            if (allFiles.value.any { it.name == fileName }) {
                addLog("System", "File already exists: $fileName", LogType.WARNING)
                return@launch
            }
            
            val newFile = WorkspaceFile(
                name = fileName,
                language = language,
                content = when(language.lowercase()) {
                    "python" -> "# New python script\n\ndef main():\n    print(\"Hello Sano!\")\n\nif __name__ == \"__main__\":\n    main()\n"
                    "kotlin" -> "fun main() {\n    println(\"Hello Sano!\")\n}\n"
                    "html" -> "<!DOCTYPE html>\n<html>\n<head>\n    <title>New File</title>\n</head>\n<body>\n    <h1>Hello Sano</h1>\n</body>\n</html>\n"
                    "css" -> "body {\n    background-color: #0b0f19;\n    color: #f3f4f6;\n}\n"
                    "javascript" -> "console.log(\"Hello Sano!\\n\");\n"
                    "sql" -> "-- SQL Query\nSELECT * FROM users;\n"
                    else -> "Hello Sano!"
                }
            )
            val fileId = repository.insertFile(newFile)
            val fileWithId = newFile.copy(id = fileId.toInt())
            openFile(fileWithId)
            addLog("System", "Created and synchronized file: $fileName", LogType.SUCCESS)
        }
    }

    fun createNewFolder(name: String, parentPath: String = "") {
        viewModelScope.launch {
            val folderSuffix = if (name.endsWith("/")) name else "$name/"
            val fullPath = if (parentPath.isEmpty()) folderSuffix else "$parentPath$folderSuffix"

            // Check if folder already exists
            if (allFiles.value.any { it.name == fullPath }) {
                addLog("System", "Folder already exists: $fullPath", LogType.WARNING)
                return@launch
            }

            val newFolder = WorkspaceFile(
                name = fullPath,
                language = "folder",
                content = ""
            )
            repository.insertFile(newFolder)
            addLog("System", "Created folder: $fullPath", LogType.SUCCESS)
        }
    }

    fun renameItem(oldFullPath: String, newName: String) {
        viewModelScope.launch {
            val files = allFiles.value
            if (oldFullPath.endsWith("/")) {
                // Folder rename
                val parts = oldFullPath.removeSuffix("/").split("/")
                val parentPath = if (parts.size > 1) {
                    parts.dropLast(1).joinToString("/") + "/"
                } else {
                    ""
                }
                val newFolderSuffix = if (newName.endsWith("/")) newName else "$newName/"
                val newFullPath = "$parentPath$newFolderSuffix"

                // Update any files starting with oldFullPath
                files.forEach { file ->
                    if (file.name.startsWith(oldFullPath)) {
                        val relative = file.name.substring(oldFullPath.length)
                        val updatedName = "$newFullPath$relative"
                        val updatedLang = if (updatedName.endsWith("/")) "folder" else file.language
                        val updatedFile = file.copy(name = updatedName, language = updatedLang, updatedAt = System.currentTimeMillis())
                        repository.updateFile(updatedFile)

                        // Sync active editors
                        val openList = _openFiles.value.toMutableList()
                        val openIndex = openList.indexOfFirst { it.id == file.id }
                        if (openIndex != -1) {
                            openList[openIndex] = updatedFile
                            _openFiles.value = openList
                        }
                        if (_activeFile.value?.id == file.id) {
                            _activeFile.value = updatedFile
                        }
                    }
                }
                // Update expanded folders list
                val currentExpanded = _expandedFolders.value
                val newExpanded = currentExpanded.map { path ->
                    if (path.startsWith(oldFullPath)) {
                        newFullPath + path.substring(oldFullPath.length)
                    } else {
                        path
                    }
                }.toSet()
                _expandedFolders.value = newExpanded

                addLog("System", "Renamed folder to: $newFullPath", LogType.SUCCESS)
            } else {
                // File rename
                val file = files.find { it.name == oldFullPath } ?: return@launch
                val parts = oldFullPath.split("/")
                val parentPath = if (parts.size > 1) {
                    parts.dropLast(1).joinToString("/") + "/"
                } else {
                    ""
                }
                val newFullPath = "$parentPath$newName"
                val ext = newName.substringAfterLast('.', "")
                val newLang = when (ext.lowercase()) {
                    "py" -> "python"
                    "kt" -> "kotlin"
                    "html" -> "html"
                    "css" -> "css"
                    "js" -> "javascript"
                    "sql" -> "sql"
                    else -> "text"
                }
                val updatedFile = file.copy(name = newFullPath, language = newLang, updatedAt = System.currentTimeMillis())
                repository.updateFile(updatedFile)

                // Sync active editors
                val openList = _openFiles.value.toMutableList()
                val openIndex = openList.indexOfFirst { it.id == file.id }
                if (openIndex != -1) {
                    openList[openIndex] = updatedFile
                    _openFiles.value = openList
                }
                if (_activeFile.value?.id == file.id) {
                    _activeFile.value = updatedFile
                }
                addLog("System", "Renamed file to: $newFullPath", LogType.SUCCESS)
            }
        }
    }

    fun deleteFolder(fullPath: String) {
        viewModelScope.launch {
            val files = allFiles.value
            files.forEach { file ->
                if (file.name.startsWith(fullPath)) {
                    closeFile(file)
                    repository.deleteFile(file)
                }
            }
            // Update expanded folders list
            _expandedFolders.value = _expandedFolders.value.filter { !it.startsWith(fullPath) }.toSet()
            addLog("System", "Deleted folder: $fullPath", LogType.WARNING)
        }
    }

    fun deleteWorkspaceFile(file: WorkspaceFile) {
        viewModelScope.launch {
            closeFile(file)
            repository.deleteFile(file)
            addLog("System", "Deleted workspace file: ${file.name}", LogType.WARNING)
        }
    }

    // Interactive simulated live collaboration
    fun toggleCollaboration(active: Boolean) {
        _isCollabActive.value = active
        if (active) {
            startCollabSimulation()
            addLog("System", "Real-time Collaboration Session Activated", LogType.SUCCESS)
        } else {
            collabJob?.cancel()
            _collaborators.value = _collaborators.value.map { it.copy(status = "Offline") }
            addLog("System", "Collaboration Session Disconnected", LogType.WARNING)
        }
    }

    private fun startCollabSimulation() {
        collabJob?.cancel()
        collabJob = viewModelScope.launch {
            while (_isCollabActive.value) {
                delay(Random.nextLong(15000, 25000)) // Wait 15-25 seconds between collab events
                
                // Select a virtual collaborator
                val list = _collaborators.value.toMutableList()
                val activeIndex = Random.nextInt(0, 3) // Alice, Bob, or Charlie
                val col = list[activeIndex]
                
                // Show typing status
                list[activeIndex] = col.copy(status = "Typing...")
                _collaborators.value = list
                
                val currentFile = _activeFile.value
                val isMatchingFile = when (col.name) {
                    "Alice" -> currentFile?.language == "python"
                    "Bob" -> currentFile?.language in listOf("html", "css", "javascript")
                    "Charlie" -> currentFile?.language == "sql"
                    else -> false
                }

                if (isMatchingFile && currentFile != null) {
                    // Collaborator will actually type into the current active file!
                    val collaboratorComment = when(col.name) {
                        "Alice" -> "\n# Alice: Optimized execution routine - latency reduced\ndef compute_data_stream():\n    return [random.random() for _ in range(10)]\n"
                        "Bob" -> "\n/* Bob: Updated container shadow layout for responsive card */\n.card {\n    box-shadow: 0 8px 30px rgba(0, 0, 0, 0.45);\n}\n"
                        "Charlie" -> "\n-- Charlie: Added query filtering to speed up dashboard loads\nWHERE sessions.last_sync_time > strftime('%s', 'now') - 86400;\n"
                        else -> ""
                    }
                    
                    addLog(col.name, "Drafting modifications in ${currentFile.name}...", LogType.CODE_EDIT)
                    
                    // Simulate cursor typing position
                    _remoteCursorName.value = col.name
                    _remoteCursorColor.value = col.color
                    
                    val textToType = collaboratorComment
                    val currentText = _currentFileContent.value
                    
                    // Typing animation character by character
                    for (charIndex in 1..textToType.length) {
                        if (!_isCollabActive.value || _activeFile.value?.id != currentFile.id) break
                        
                        _currentFileContent.value = currentText + textToType.substring(0, charIndex)
                        _remoteCursorIndex.value = _currentFileContent.value.length
                        delay(Random.nextLong(50, 150)) // Typing speed
                    }
                    
                    // Save the final text
                    updateContent(_currentFileContent.value)
                    
                    // Reset remote cursor
                    _remoteCursorIndex.value = null
                    
                    addLog(col.name, "Synced edits to ${currentFile.name}", LogType.SUCCESS)
                } else {
                    // Edits a background file instead
                    val files = allFiles.value
                    val targetFile = when(col.name) {
                        "Alice" -> files.find { it.language == "python" }
                        "Bob" -> files.find { it.language in listOf("html", "css", "javascript") }
                        "Charlie" -> files.find { it.language == "sql" }
                        else -> null
                    }
                    if (targetFile != null) {
                        val appendText = when(col.name) {
                            "Alice" -> "\n# Alice: Added analytics helper\ndef get_session_duration():\n    return 3600\n"
                            "Bob" -> "\n// Bob: Quick responsive UI fix\nwindow.addEventListener('resize', () => {\n    console.log('Viewport adapted');\n});\n"
                            "Charlie" -> "\n-- Charlie: Indexing for fast joins\nCREATE INDEX IF NOT EXISTS idx_user_sessions ON sessions(user_id);\n"
                            else -> ""
                        }
                        addLog(col.name, "Modifying background file: ${targetFile.name}", LogType.INFO)
                        delay(3000) // simulating editing time
                        repository.updateFile(targetFile.copy(content = targetFile.content + appendText))
                        addLog(col.name, "Synced background edits to ${targetFile.name}", LogType.SUCCESS)
                    }
                }
                
                // Return to idle
                val listReset = _collaborators.value.toMutableList()
                listReset[activeIndex] = col.copy(status = "Idle")
                _collaborators.value = listReset
            }
        }
    }

    private fun addLog(sender: String, action: String, type: LogType) {
        val timestamp = timeFormat.format(Date())
        val newLog = SyncLog(timestamp, sender, action, type)
        _syncLogs.value = listOf(newLog) + _syncLogs.value
    }

    // Terminal Command Interpreter
    fun setTerminalCommand(cmd: String) {
        _terminalCommand.value = cmd
    }

    fun executeTerminalCommand() {
        val rawCmd = _terminalCommand.value.trim()
        if (rawCmd.isEmpty()) return

        val output = _consoleOutput.value.toMutableList()
        output.add("> $rawCmd")

        val parts = rawCmd.split(" ")
        val command = parts[0].lowercase()
        val arg = if (parts.size > 1) parts[1] else ""

        when(command) {
            "help" -> {
                output.add("Sano Terminal Commands:")
                output.add("  run               - Executes the active file and shows output")
                output.add("  ls                - Lists all project workspace files")
                output.add("  cat <filename>    - Displays contents of specified file")
                output.add("  git status        - Displays current git tracking state")
                output.add("  git commit        - Commits edits to virtual branch")
                output.add("  sync              - Triggers hard refresh to Sano Cloud Database")
                output.add("  clear             - Clears this terminal console")
            }
            "clear" -> {
                _consoleOutput.value = emptyList()
                _terminalCommand.value = ""
                return
            }
            "ls" -> {
                output.add("Sano Local Workspace Tree:")
                allFiles.value.forEach { file ->
                    output.add("  - ${file.name}   [${file.language.uppercase()}] (${file.content.length} chars)")
                }
            }
            "cat" -> {
                if (arg.isEmpty()) {
                    output.add("Error: Please specify file name, e.g. 'cat main.py'")
                } else {
                    val file = allFiles.value.find { it.name.lowercase() == arg.lowercase() }
                    if (file != null) {
                        output.add("--- Displaying: ${file.name} ---")
                        file.content.split("\n").forEach { line ->
                            output.add(line)
                        }
                    } else {
                        output.add("Error: File '$arg' not found in Sano workspace.")
                    }
                }
            }
            "git" -> {
                val sub = arg.lowercase()
                if (sub == "status") {
                    output.add("On branch main")
                    output.add("Your branch is up to date with 'origin/main'.")
                    output.add("")
                    output.add("Changes to be committed:")
                    output.add("  (use \"git restore --staged <file>...\" to unstage)")
                    output.add("        modified:   ${_activeFile.value?.name ?: "No active files"}")
                    output.add("")
                    output.add("Cloud Sync Status: CONNECTED")
                } else if (sub == "commit" || (parts.size > 2 && parts[1].lowercase() == "commit")) {
                    output.add("[main ad83bc2] Simulating local changes committed to virtual repository.")
                    output.add(" 1 file changed, 14 insertions(+)")
                    addLog("Git Node", "Committed workspace modifications", LogType.INFO)
                } else {
                    output.add("Git command not recognized in local terminal. Try 'git status'.")
                }
            }
            "sync" -> {
                viewModelScope.launch {
                    output.add("Initiating synchronization with Sano Cloud Node Europe...")
                    _isSyncing.value = true
                    delay(1200)
                    _isSyncing.value = false
                    output.add("✅ Cloud sync complete. Latency: 32ms. Workspace is identical.")
                    addLog("System", "Manual Cloud Database sync successful", LogType.SUCCESS)
                }
            }
            "run" -> {
                val active = _activeFile.value
                if (active == null) {
                    output.add("Error: No file is open in Sano editor.")
                } else {
                    output.add("🚀 Running ${active.name}...")
                    viewModelScope.launch {
                        delay(600)
                        val runOutput = simulateCodeRun(active)
                        runOutput.forEach { line ->
                            output.add(line)
                        }
                        addLog("Runner", "Executed ${active.name} successfully", LogType.SUCCESS)
                    }
                }
            }
            else -> {
                output.add("Command '$command' not found. Type 'help' for Sano terminal usage.")
            }
        }

        _consoleOutput.value = output
        _terminalCommand.value = ""
    }

    fun runActiveFile() {
        _terminalCommand.value = "run"
        executeTerminalCommand()
    }

    private fun simulateCodeRun(file: WorkspaceFile): List<String> {
        val result = mutableListOf<String>()
        when (file.language.lowercase()) {
            "python" -> {
                result.add("Python 3.10.2 (Sano Environment)")
                result.add("---------------------------------")
                if (file.content.contains("def greet_developers")) {
                    result.add("🚀 Initializing Sano Cloud Collaboration Engine...")
                    result.add("✨ Welcome back, Alice (Python Expert)!")
                    result.add("✨ Welcome back, Bob (Frontend)!")
                    result.add("✨ Welcome back, Charlie (DBA)!")
                    result.add("✨ Welcome back, You!")
                    result.add("")
                    result.add("Process finished with exit code 0")
                } else {
                    result.add("Running code script...")
                    result.add("Success! Code executed with output state.")
                    result.add("Process finished with exit code 0")
                }
            }
            "kotlin" -> {
                result.add("Kotlin/JVM Compiler v1.9.0")
                result.add("---------------------------")
                if (file.content.contains("CollaborativeEditor")) {
                    result.add("🔄 [Sync] Connecting to cloud database...")
                    result.add("🔄 [Sync] Synchronized with Sano Cloud Node.")
                } else {
                    result.add("Kotlin main routine ran.")
                    result.add("Exit code: 0")
                }
            }
            "html" -> {
                result.add("Sano HTML Rendering Engine v1.0")
                result.add("--------------------------------")
                result.add("Page structure compiled successfully:")
                result.add("  - Title: Sano Editor Preview")
                result.add("  - CSS loaded from style.css")
                result.add("  - JavaScript loaded from script.js")
                result.add("Status: Rendering preview... Web element active.")
            }
            "css" -> {
                result.add("Sano CSS Validator v1.0")
                result.add("-------------------------")
                result.add("Checking syntax in style.css...")
                result.add("  - body: OK")
                result.add("  - .card: OK")
                result.add("  - button: OK")
                result.add("Result: Styles compiles successfully (0 errors, 0 warnings)")
            }
            "javascript" -> {
                result.add("Node.js v18.12.1 (Sano Simulation)")
                result.add("-----------------------------------")
                if (file.content.contains("triggerSync")) {
                    result.add("Compiled script trigger functions.")
                } else {
                    result.add("Script output: Done.")
                }
            }
            "sql" -> {
                result.add("Sano SQL-Lite Instance Engine")
                result.add("--------------------------------")
                if (file.content.contains("SELECT")) {
                    result.add("| id | name     | active_file | last_sync_time       |")
                    result.add("+----+----------+-------------+----------------------+")
                    result.add("|  1 | Alice    | main.py     | 2026-07-04 10:42:15  |")
                    result.add("|  2 | Bob      | index.html  | 2026-07-04 10:41:40  |")
                    result.add("|  3 | Charlie  | queries.sql | 2026-07-04 10:40:02  |")
                    result.add("+----+----------+-------------+----------------------+")
                    result.add("(3 rows returned in 12ms)")
                } else {
                    result.add("Statement OK. 0 rows affected.")
                }
            }
            else -> {
                result.add("Raw document compiled. Characters: ${file.content.length}")
            }
        }
        return result
    }

    // AI Chat & Co-pilot integration using Gemini
    fun sendMessageToChat(text: String) {
        val userMsg = ChatMessage(sender = "You", message = text, isUser = true)
        _chatMessages.value = _chatMessages.value + userMsg

        _isChatLoading.value = true

        viewModelScope.launch {
            // Check if user is asking the co-pilot to write code directly in the editor!
            val isCodeCommand = text.lowercase().contains("write") || text.lowercase().contains("generate") || text.lowercase().contains("implement")
            val active = _activeFile.value
            
            val systemPrompt = """
                You are Gemini AI Co-pilot, a brilliant real-time developer collaborator inside Sano, a sleek browser-feeling dark-themed IDE.
                The user can chat with you or ask you to write code.
                Current active file is: ${active?.name ?: "None"} in language: ${active?.language ?: "None"}.
                Keep your responses short, professional, and developer-friendly. Avoid long explanations.
                If the user asks you to write code, provide the code clearly in markdown inside your message. You can also mention that you will co-author the code directly into Sano editor.
            """.trimIndent()

            val apiKey = com.example.BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                // Mock smart response if API key is not configured yet
                delay(1500)
                val mockResponse = generateMockAIResponse(text, active)
                
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    sender = "Gemini AI",
                    message = mockResponse.first,
                    isAI = true
                )
                _isChatLoading.value = false

                // If it is a code generation command, simulate typing the code directly in the Sano editor!
                if (isCodeCommand && active != null && mockResponse.second != null) {
                    simulateAICodeInsertion(mockResponse.second!!, active)
                }
            } else {
                // Real API Call
                try {
                    val promptWithContext = "System instructions: $systemPrompt\nUser message: $text"
                    val request = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = promptWithContext))))
                    )
                    
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.service.generateContent(apiKey, request)
                    }
                    
                    val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                        ?: "I'm having trouble connecting right now. Let me know if you'd like to try again!"
                    
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        sender = "Gemini AI",
                        message = responseText,
                        isAI = true
                    )
                    _isChatLoading.value = false

                    // If it is a code command, extract the code from markdown block and simulate writing it!
                    if (isCodeCommand && active != null) {
                        val extractedCode = extractCodeFromMarkdown(responseText)
                        if (extractedCode != null) {
                            simulateAICodeInsertion(extractedCode, active)
                        }
                    }
                } catch (e: Exception) {
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        sender = "Gemini AI",
                        message = "Error: Unable to connect to Gemini API. Fallback local model loaded: ${e.localizedMessage}",
                        isAI = true
                    )
                    _isChatLoading.value = false
                }
            }
        }
    }

    private fun extractCodeFromMarkdown(text: String): String? {
        val pattern = Pattern.compile("```[a-zA-Z]*\\n([\\s\\S]*?)\\n```")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    private fun simulateAICodeInsertion(code: String, file: WorkspaceFile) {
        viewModelScope.launch {
            addLog("Gemini AI", "Writing code into ${file.name}...", LogType.CODE_EDIT)
            _remoteCursorName.value = "Gemini AI"
            _remoteCursorColor.value = Color(0xFFA78BFA) // Purple
            
            val currentText = _currentFileContent.value
            val textToType = "\n\n" + code + "\n"
            
            for (i in 1..textToType.length) {
                if (_activeFile.value?.id != file.id) break
                _currentFileContent.value = currentText + textToType.substring(0, i)
                _remoteCursorIndex.value = _currentFileContent.value.length
                delay(30) // Fast typing animation for AI
            }
            
            updateContent(_currentFileContent.value)
            _remoteCursorIndex.value = null
            addLog("Gemini AI", "Successfully co-authored and synced code to ${file.name}", LogType.SUCCESS)
        }
    }

    private fun generateMockAIResponse(text: String, file: WorkspaceFile?): Pair<String, String?> {
        val lowText = text.lowercase()
        return if (lowText.contains("quicksort") || lowText.contains("sort")) {
            Pair(
                "I would love to help! Quicksort is an efficient sorting algorithm using divide-and-conquer. I am co-authoring a robust Python quicksort function into your active Sano editor tab right now!",
                """def quicksort(arr):
    if len(arr) <= 1:
        return arr
    pivot = arr[len(arr) // 2]
    left = [x for x in arr if x < pivot]
    middle = [x for x in arr if x == pivot]
    right = [x for x in arr if x > pivot]
    return quicksort(left) + middle + quicksort(right)

# Test quicksort
print("Sorted array:", quicksort([3, 6, 8, 10, 1, 2, 1]))"""
            )
        } else if (lowText.contains("fibonacci") || lowText.contains("fibo")) {
            Pair(
                "Fibonacci numbers form a sequence where each number is the sum of the two preceding ones. I'll write a fast memoized Fibonacci generator for you in the editor!",
                """def fibonacci(n, memo={}):
    if n in memo: return memo[n]
    if n <= 1: return n
    memo[n] = fibonacci(n-1, memo) + fibonacci(n-2, memo)
    return memo[n]

# Test Fibonacci
print("Fibonacci(10):", fibonacci(10))"""
            )
        } else if (lowText.contains("hello") || lowText.contains("hi")) {
            Pair(
                "Hello developer! Welcome to Sano IDE. I am your collaborative co-pilot. I am connected via Sano Cloud Node. Ask me to write code, design layout elements, or explain concepts. You can also toggles real-time collaborator events in the sidebar!",
                null
            )
        } else {
            Pair(
                "I understand! I'm ready to write some customized functions or scripts. What specific algorithms or components should we build together today in Sano?",
                null
            )
        }
    }
}
