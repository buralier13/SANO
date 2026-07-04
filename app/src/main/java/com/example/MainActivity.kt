package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.WorkspaceFile
import com.example.ui.*
import com.example.ui.editor.SyntaxHighlightingTransformation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SanoTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    SanoIdeLayout(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Custom Sleek Interface visual theme
@Composable
fun SanoTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = Color(0xFFD0BCFF),       // Lavender
        secondary = Color(0xFFEFB8C8),     // M3 Pink / Rose
        tertiary = Color(0xFFC2E7FF),      // Pale Blue
        background = Color(0xFF0F0F0F),    // Dark Charcoal Background
        surface = Color(0xFF1C1B1F),       // Dark Gray Header/Sidebar/Nav
        surfaceVariant = Color(0xFF2C2C2C),// Accent Active Row Highlight
        onPrimary = Color(0xFF381E72),
        onSecondary = Color(0xFF492532),
        onBackground = Color(0xFFE3E2E6),
        onSurface = Color(0xFFE3E2E6),
        onSurfaceVariant = Color(0xFF938F99),
        outline = Color(0xFF49454F)        // Sleek Border/Divider color
    )

    MaterialTheme(
        colorScheme = darkColors,
        content = content
    )
}

@Composable
fun SanoIdeLayout(
    modifier: Modifier = Modifier,
    viewModel: SanoViewModel = viewModel()
) {
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val openFiles by viewModel.openFiles.collectAsStateWithLifecycle()
    val activeFile by viewModel.activeFile.collectAsStateWithLifecycle()
    val currentContent by viewModel.currentFileContent.collectAsStateWithLifecycle()
    val isCollabActive by viewModel.isCollabActive.collectAsStateWithLifecycle()
    val collaborators by viewModel.collaborators.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()
    val consoleOutput by viewModel.consoleOutput.collectAsStateWithLifecycle()
    val terminalCommand by viewModel.terminalCommand.collectAsStateWithLifecycle()
    val activeSidebarTab by viewModel.activeSidebarTab.collectAsStateWithLifecycle()
    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    val expandedFolders by viewModel.expandedFolders.collectAsStateWithLifecycle()

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var selectedParentPathForCreation by remember { mutableStateOf("") }
    var itemPathToRename by remember { mutableStateOf("") }
    var isRenamingFolder by remember { mutableStateOf(false) }
    var itemPathToDelete by remember { mutableStateOf("") }
    var isDeletingFolder by remember { mutableStateOf(false) }
    var bottomPanelHeight by remember { mutableStateOf(180.dp) }
    var isBottomPanelOpen by remember { mutableStateOf(true) }
    var selectedBottomTab by remember { mutableStateOf("terminal") } // "console", "terminal", "collab"

    // Responsive screen size layout mapping (Tablet/Emulator screen vs Phone)
    BoxWithConstraints(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        val isTablet = maxWidth >= 720.dp
        var isMobileSidebarOpen by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Sano Header/Title Bar
            SanoHeader(
                isSyncing = isSyncing,
                collaborators = collaborators,
                isCollabActive = isCollabActive,
                onToggleCollab = { viewModel.toggleCollaboration(it) },
                onRunFile = { viewModel.runActiveFile() },
                isTablet = isTablet,
                onOpenMobileSidebar = { isMobileSidebarOpen = !isMobileSidebarOpen }
            )

            Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            // 2. Central IDE workspace
            Row(modifier = Modifier.weight(1f)) {
                // Left Icon sidebar (VS Code style)
                SanoIconSidebar(
                    activeTab = activeSidebarTab,
                    onTabSelected = {
                        viewModel.selectSidebarTab(it)
                        if (!isTablet) isMobileSidebarOpen = true
                    },
                    isCollabActive = isCollabActive
                )

                Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp, modifier = Modifier.fillMaxHeight().width(1.dp))

                // Left Sidebar Drawer Panel (Files, Search, AI, Collaboration, Settings)
                if (isTablet || isMobileSidebarOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(if (isTablet) 240.dp else 220.dp)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            SidebarPanelHeader(
                                activeTab = activeSidebarTab,
                                onCloseMobilePanel = { isMobileSidebarOpen = false },
                                isTablet = isTablet
                            )

                            Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                            Box(modifier = Modifier.weight(1f)) {
                                when (activeSidebarTab) {
                                    "files" -> FileExplorerPanel(
                                        files = allFiles,
                                        activeFile = activeFile,
                                        expandedFolders = expandedFolders,
                                        onFileSelected = {
                                            viewModel.openFile(it)
                                            if (!isTablet) isMobileSidebarOpen = false
                                        },
                                        onToggleFolder = { viewModel.toggleFolderExpanded(it) },
                                        onCreateFileClick = { parent ->
                                            selectedParentPathForCreation = parent
                                            showNewFileDialog = true
                                        },
                                        onCreateFolderClick = { parent ->
                                            selectedParentPathForCreation = parent
                                            showNewFolderDialog = true
                                        },
                                        onRenameClick = { path, isFolder ->
                                            itemPathToRename = path
                                            isRenamingFolder = isFolder
                                            showRenameDialog = true
                                        },
                                        onDeleteClick = { path, isFolder ->
                                            itemPathToDelete = path
                                            isDeletingFolder = isFolder
                                            showDeleteConfirmDialog = true
                                        }
                                    )
                                    "search" -> SearchPanel(
                                        query = searchQuery,
                                        onQueryChanged = { viewModel.setSearchQuery(it) },
                                        results = searchResults,
                                        onSelectResult = { file ->
                                            viewModel.openFile(file)
                                            if (!isTablet) isMobileSidebarOpen = false
                                        }
                                    )
                                    "chat" -> AiChatPanel(
                                        messages = chatMessages,
                                        isLoading = isChatLoading,
                                        onSendMessage = { viewModel.sendMessageToChat(it) }
                                    )
                                    "collab" -> CollabPanel(
                                        collaborators = collaborators,
                                        logs = syncLogs,
                                        isCollabActive = isCollabActive
                                    )
                                    "settings" -> SettingsPanel(
                                        selectedTheme = selectedTheme,
                                        onThemeSelected = { viewModel.setTheme(it) }
                                    )
                                }
                            }
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp, modifier = Modifier.fillMaxHeight().width(1.dp))
                }

                // Center Main Code Editor area
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Open Tabs
                    if (openFiles.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(MaterialTheme.colorScheme.surface)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            openFiles.forEach { file ->
                                EditorTab(
                                    file = file,
                                    isActive = activeFile?.id == file.id,
                                    onSelect = { viewModel.openFile(file) },
                                    onClose = { viewModel.closeFile(file) }
                                )
                            }
                        }
                    } else {
                        // Empty tabs spacer
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(MaterialTheme.colorScheme.surface)
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                    // The actual Code Editor Canvas
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (activeFile != null) {
                            CodeEditorCanvas(
                                content = currentContent,
                                onContentChanged = { viewModel.updateContent(it) },
                                file = activeFile!!,
                                viewModel = viewModel
                            )
                        } else {
                            EditorEmptyState(
                                onCreateFileClick = { showNewFileDialog = true }
                            )
                        }
                    }

                    // 3. Bottom Output Console / Terminal Panel
                    if (isBottomPanelOpen) {
                        Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(bottomPanelHeight)
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            // Bottom Tab headers
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .background(MaterialTheme.colorScheme.surface),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    BottomTabButton("TERMINAL", selectedBottomTab == "terminal") { selectedBottomTab = "terminal" }
                                    BottomTabButton("RUN CONSOLE", selectedBottomTab == "console") { selectedBottomTab = "console" }
                                    BottomTabButton("CLOUD LOGS", selectedBottomTab == "collab") { selectedBottomTab = "collab" }
                                }

                                IconButton(
                                    onClick = { isBottomPanelOpen = false },
                                    modifier = Modifier.size(32.dp).padding(end = 8.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Minimize Panel", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                            // Bottom Panel Body
                            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                                when (selectedBottomTab) {
                                    "terminal" -> SanoTerminalView(
                                        logs = consoleOutput,
                                        command = terminalCommand,
                                        onCommandChanged = { viewModel.setTerminalCommand(it) },
                                        onExecute = { viewModel.executeTerminalCommand() }
                                    )
                                    "console" -> SanoConsoleView(logs = consoleOutput)
                                    "collab" -> CollabLogsView(logs = syncLogs)
                                }
                            }
                        }
                    } else {
                        // Quick Toggle to restore bottom panel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { isBottomPanelOpen = true }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = "Open Terminal", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore Developer Console & Terminal", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            // 4. Desktop-style status footer
            SanoStatusBar(activeFile = activeFile, isCollabActive = isCollabActive, isSyncing = isSyncing)
        }

        // New File Creation Overlay Dialog
        if (showNewFileDialog) {
            NewFileDialog(
                onDismiss = { 
                    showNewFileDialog = false
                    selectedParentPathForCreation = ""
                },
                onCreate = { name, lang ->
                    viewModel.createNewFile(name, lang, selectedParentPathForCreation)
                    showNewFileDialog = false
                    selectedParentPathForCreation = ""
                }
            )
        }

        // New Folder Creation Overlay Dialog
        if (showNewFolderDialog) {
            NewFolderDialog(
                onDismiss = { 
                    showNewFolderDialog = false
                    selectedParentPathForCreation = ""
                },
                onCreate = { name ->
                    viewModel.createNewFolder(name, selectedParentPathForCreation)
                    showNewFolderDialog = false
                    selectedParentPathForCreation = ""
                }
            )
        }

        // Rename Overlay Dialog
        if (showRenameDialog) {
            val initial = if (isRenamingFolder) {
                itemPathToRename.removeSuffix("/").substringAfterLast("/")
            } else {
                itemPathToRename.substringAfterLast("/")
            }
            RenameDialog(
                initialName = initial,
                isFolder = isRenamingFolder,
                onDismiss = { 
                    showRenameDialog = false
                    itemPathToRename = ""
                    isRenamingFolder = false
                },
                onRename = { newName ->
                    viewModel.renameItem(itemPathToRename, newName)
                    showRenameDialog = false
                    itemPathToRename = ""
                    isRenamingFolder = false
                }
            )
        }

        // Delete Confirmation Overlay Dialog
        if (showDeleteConfirmDialog) {
            val display = itemPathToDelete.removeSuffix("/").substringAfterLast("/")
            DeleteConfirmDialog(
                itemName = display,
                isFolder = isDeletingFolder,
                onDismiss = { 
                    showDeleteConfirmDialog = false
                    itemPathToDelete = ""
                    isDeletingFolder = false
                },
                onConfirm = {
                    if (isDeletingFolder) {
                        viewModel.deleteFolder(itemPathToDelete)
                    } else {
                        val fileToDelete = allFiles.find { it.name == itemPathToDelete }
                        if (fileToDelete != null) {
                            viewModel.deleteWorkspaceFile(fileToDelete)
                        }
                    }
                    showDeleteConfirmDialog = false
                    itemPathToDelete = ""
                    isDeletingFolder = false
                }
            )
        }
    }
}

// ---------------- IDE COMPONENT SUB-VIEWS ----------------

@Composable
fun SanoHeader(
    isSyncing: Boolean,
    collaborators: List<Collaborator>,
    isCollabActive: Boolean,
    onToggleCollab: (Boolean) -> Unit,
    onRunFile: () -> Unit,
    isTablet: Boolean,
    onOpenMobileSidebar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isTablet) {
            IconButton(
                onClick = onOpenMobileSidebar,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Menu, contentDescription = "Sidebar Toggle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Sano cyber badge logo (Monogram badge styled after Sleek Interface spec)
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "S",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = "Sano",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (isTablet) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("v1.0.0-Cloud", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Live Cloud Sync Indicator pulse
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_anim"
            )

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        color = (if (isSyncing) Color(0xFFFB923C) else Color(0xFF10B981)).copy(
                            alpha = if (isSyncing) 1f else pulseAlpha
                        )
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isSyncing) "Syncing..." else "Cloud Synced",
                color = if (isSyncing) Color(0xFFFB923C) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Collaboration room action toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onToggleCollab(!isCollabActive) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = if (isCollabActive) Icons.Default.Group else Icons.Outlined.PersonOutline,
                contentDescription = "Collab",
                tint = if (isCollabActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            if (isTablet) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isCollabActive) "Collab ON (${collaborators.count { it.status != "Offline" }})" else "Collab OFF",
                    color = if (isCollabActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Run play action button (F5)
        Button(
            onClick = onRunFile,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(30.dp).testTag("run_button")
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Run", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("RUN", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
fun SanoIconSidebar(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    isCollabActive: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(52.dp)
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        SidebarIconButton(Icons.Default.Folder, "Files", activeTab == "files") { onTabSelected("files") }
        SidebarIconButton(Icons.Default.Search, "Search", activeTab == "search") { onTabSelected("search") }
        SidebarIconButton(Icons.Default.Group, "Collaborators", activeTab == "collab", showBadge = isCollabActive) { onTabSelected("collab") }
        SidebarIconButton(Icons.Default.Chat, "AI Chat", activeTab == "chat") { onTabSelected("chat") }

        Spacer(modifier = Modifier.weight(1f))

        SidebarIconButton(Icons.Default.Settings, "Settings", activeTab == "settings") { onTabSelected("settings") }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun SidebarIconButton(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    showBadge: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )

        if (showBadge) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
            )
        }
    }
}

@Composable
fun SidebarPanelHeader(
    activeTab: String,
    onCloseMobilePanel: () -> Unit,
    isTablet: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = activeTab.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp,
            modifier = Modifier.weight(1f)
        )

        if (!isTablet) {
            IconButton(onClick = onCloseMobilePanel, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Close Panel", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------- PANEL VIEWS ----------------

data class TreeItem(
    val name: String,
    val fullPath: String,
    val isFolder: Boolean,
    val depth: Int,
    val file: WorkspaceFile? = null
)

fun getVisibleTreeItems(
    files: List<WorkspaceFile>,
    expandedFolders: Set<String>
): List<TreeItem> {
    val folderPaths = mutableSetOf<String>()
    for (f in files) {
        if (f.name.endsWith("/") || f.language == "folder") {
            val path = if (f.name.endsWith("/")) f.name else "${f.name}/"
            folderPaths.add(path)
        } else {
            val parts = f.name.split("/")
            var current = ""
            for (i in 0 until parts.size - 1) {
                current += parts[i] + "/"
                folderPaths.add(current)
            }
        }
    }

    fun getChildren(parentPath: String): List<TreeItem> {
        val children = mutableListOf<TreeItem>()

        // Subfolders
        val subfolders = folderPaths.filter { folder ->
            if (parentPath.isEmpty()) {
                folder.count { it == '/' } == 1 && !folder.startsWith("/")
            } else {
                folder.startsWith(parentPath) && folder != parentPath &&
                        folder.substring(parentPath.length).count { it == '/' } == 1
            }
        }.map { folderPath ->
            val name = if (parentPath.isEmpty()) {
                folderPath.removeSuffix("/")
            } else {
                folderPath.substring(parentPath.length).removeSuffix("/")
            }
            TreeItem(
                name = name,
                fullPath = folderPath,
                isFolder = true,
                depth = if (parentPath.isEmpty()) 0 else parentPath.count { it == '/' }
            )
        }
        children.addAll(subfolders.sortedBy { it.name.lowercase() })

        // Files
        val immediateFiles = files.filter { f ->
            if (f.name.endsWith("/") || f.language == "folder") return@filter false
            if (parentPath.isEmpty()) {
                !f.name.contains("/")
            } else {
                f.name.startsWith(parentPath) && !f.name.substring(parentPath.length).contains("/")
            }
        }.map { f ->
            val name = if (parentPath.isEmpty()) f.name else f.name.substring(parentPath.length)
            TreeItem(
                name = name,
                fullPath = f.name,
                isFolder = false,
                depth = if (parentPath.isEmpty()) 0 else parentPath.count { it == '/' },
                file = f
            )
        }
        children.addAll(immediateFiles.sortedBy { it.name.lowercase() })

        return children
    }

    val result = mutableListOf<TreeItem>()
    fun traverse(parentPath: String) {
        val children = getChildren(parentPath)
        for (item in children) {
            result.add(item)
            if (item.isFolder && expandedFolders.contains(item.fullPath)) {
                traverse(item.fullPath)
            }
        }
    }
    traverse("")
    return result
}

@Composable
fun FileExplorerPanel(
    files: List<WorkspaceFile>,
    activeFile: WorkspaceFile?,
    expandedFolders: Set<String>,
    onFileSelected: (WorkspaceFile) -> Unit,
    onToggleFolder: (String) -> Unit,
    onCreateFileClick: (String) -> Unit,
    onCreateFolderClick: (String) -> Unit,
    onRenameClick: (String, Boolean) -> Unit,
    onDeleteClick: (String, Boolean) -> Unit
) {
    val visibleItems = remember(files, expandedFolders) {
        getVisibleTreeItems(files, expandedFolders)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("WORKSPACE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { onCreateFileClick("") },
                    modifier = Modifier.size(24.dp).testTag("new_file_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New File", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { onCreateFolderClick("") },
                    modifier = Modifier.size(24.dp).testTag("new_folder_button")
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (visibleItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No items in workspace", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(visibleItems) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (!item.isFolder && activeFile?.name == item.fullPath) 
                                    MaterialTheme.colorScheme.surfaceVariant 
                                else 
                                    Color.Transparent
                            )
                            .clickable {
                                if (item.isFolder) {
                                    onToggleFolder(item.fullPath)
                                } else if (item.file != null) {
                                    onFileSelected(item.file)
                                }
                            }
                            .padding(start = (item.depth * 12 + 4).dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Expand/Collapse Icon for folders, or indent Spacer for files
                        if (item.isFolder) {
                            val isExpanded = expandedFolders.contains(item.fullPath)
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        } else {
                            Spacer(modifier = Modifier.width(18.dp))
                        }

                        // Node Icon
                        val icon = if (item.isFolder) {
                            Icons.Default.Folder
                        } else {
                            val f = item.file
                            when (f?.language?.lowercase()) {
                                "python" -> Icons.Default.Code
                                "kotlin" -> Icons.Default.Code
                                "html" -> Icons.Default.Html
                                "css" -> Icons.Default.Css
                                "javascript" -> Icons.Default.Javascript
                                "sql" -> Icons.Default.Storage
                                else -> Icons.Default.Description
                            }
                        }

                        val iconColor = if (item.isFolder) {
                            Color(0xFFFBBF24) // Yellow/Amber Folder color
                        } else {
                            val f = item.file
                            when (f?.language?.lowercase()) {
                                "python" -> Color(0xFFFB923C)
                                "kotlin" -> MaterialTheme.colorScheme.primary
                                "html" -> MaterialTheme.colorScheme.secondary
                                "css" -> Color(0xFF38BDF8)
                                "javascript" -> Color(0xFFFBBF24)
                                "sql" -> Color(0xFF34D399)
                                else -> Color.Gray
                            }
                        }

                        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))

                        // Node Name
                        Text(
                            text = item.name,
                            color = if (!item.isFolder && activeFile?.name == item.fullPath) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            fontFamily = FontFamily.Monospace
                        )

                        // Action Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.isFolder) {
                                IconButton(
                                    onClick = { onCreateFileClick(item.fullPath) },
                                    modifier = Modifier.size(20.dp).testTag("add_file_to_${item.name}")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add File", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                }
                                IconButton(
                                    onClick = { onCreateFolderClick(item.fullPath) },
                                    modifier = Modifier.size(20.dp).testTag("add_folder_to_${item.name}")
                                ) {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Add Folder", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(12.dp))
                                }
                            }
                            IconButton(
                                onClick = { onRenameClick(item.fullPath, item.isFolder) },
                                modifier = Modifier.size(20.dp).testTag("rename_${item.name}")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                            }
                            IconButton(
                                onClick = { onDeleteClick(item.fullPath, item.isFolder) },
                                modifier = Modifier.size(20.dp).testTag("delete_${item.name}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchPanel(
    query: String,
    onQueryChanged: (String) -> Unit,
    results: List<Pair<WorkspaceFile, Int>>,
    onSelectResult: (WorkspaceFile) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            placeholder = { Text("Search code patterns...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(4.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("SEARCH RESULTS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(8.dp))

        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (query.isEmpty()) "Type above to search Sano" else "No matching references found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { (file, count) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onSelectResult(file) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text("Language: ${file.language.uppercase()}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("$count matches", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiChatPanel(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    onSendMessage: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                val bubbleBg = when {
                    msg.isUser -> MaterialTheme.colorScheme.surfaceVariant
                    msg.sender == "Sano System" -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                }
                val nameColor = when {
                    msg.isUser -> MaterialTheme.colorScheme.primary
                    msg.sender == "Alice" -> MaterialTheme.colorScheme.secondary
                    msg.sender == "Gemini AI" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bubbleBg)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(msg.sender, color = nameColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(msg.message, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }

            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gemini Co-pilot is typing...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .padding(8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (text.isNotBlank()) {
                        onSendMessage(text)
                        text = ""
                    }
                })
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSendMessage(text)
                        text = ""
                    }
                },
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun CollabPanel(
    collaborators: List<Collaborator>,
    logs: List<SyncLog>,
    isCollabActive: Boolean
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("ACTIVE SESSION COLLABORATORS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))

        collaborators.forEach { col ->
            val isActive = isCollabActive && col.status != "Offline"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isActive) col.color else Color.DarkGray)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(col.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(if (isActive) "${col.role} (${col.status})" else "Offline", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("SESSION METRICS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sync Channel: WebRTC Cloud Node 1", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("Database Engine: SQLite / Room Local Store", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("Latency: 24 ms", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("Changes Synced: ${logs.count { it.type == LogType.SUCCESS }} saved commits", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun SettingsPanel(
    selectedTheme: String,
    onThemeSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Text("IDE VISUAL THEME", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))

            val themes = listOf("Monokai Cyber", "One Dark Pro", "Nord Slate", "Solarized Light")
            themes.forEach { theme ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeSelected(theme) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedTheme == theme,
                        onClick = { onThemeSelected(theme) },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(theme, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outline)

        Column {
            Text("WORKSPACE STATS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Autosave: Enabled (Debounced)", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text("Auto-bracket complete: Active", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text("Tab indent size: 4 spaces", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// ---------------- MAIN EDITOR VIEWS ----------------

@Composable
fun EditorTab(
    file: WorkspaceFile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val tabBg = if (isActive) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface
    val tabBorderColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .background(tabBg)
            .clickable(onClick = onSelect)
            .drawBehind {
                // Top accent line for active tab matching Sleek design spec
                if (isActive) {
                    drawLine(
                        color = tabBorderColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Code,
            contentDescription = null,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = file.name,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.width(10.dp))
        IconButton(
            onClick = {
                onClose()
            },
            modifier = Modifier.size(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close Tab", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(10.dp))
        }
    }
    Divider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.fillMaxHeight().width(1.dp))
}

@Composable
fun CodeEditorCanvas(
    content: String,
    onContentChanged: (String) -> Unit,
    file: WorkspaceFile,
    viewModel: SanoViewModel
) {
    val remoteCursorIndex by viewModel.remoteCursorIndex.collectAsStateWithLifecycle()
    val remoteCursorColor by viewModel.remoteCursorColor.collectAsStateWithLifecycle()
    val remoteCursorName by viewModel.remoteCursorName.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Line Numbers Column
        val lineCount = content.split("\n").size
        Column(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.End
        ) {
            for (i in 1..lineCount) {
                Text(
                    text = i.toString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().height(18.dp)
                )
            }
        }

        Divider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.fillMaxHeight().width(1.dp))

        // Text editor viewport
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 12.dp, horizontal = 12.dp)
        ) {
            // Synthesized live remote typing cursor overlay
            if (remoteCursorIndex != null && remoteCursorIndex!! <= content.length) {
                val textBeforeCursor = content.substring(0, remoteCursorIndex!!)
                val textLines = textBeforeCursor.split("\n")
                val cursorLine = textLines.size - 1
                val cursorCol = textLines.last().length

                // Roughly compute position on screen for the floating indicator
                val xOffset = (cursorCol * 7).dp
                val yOffset = (cursorLine * 18).dp

                Box(
                    modifier = Modifier
                        .offset(x = xOffset, y = yOffset)
                        .size(width = 2.dp, height = 16.dp)
                        .background(remoteCursorColor)
                ) {
                    Box(
                        modifier = Modifier
                            .offset(y = (-10).dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(remoteCursorColor)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(remoteCursorName, color = Color.Black, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            BasicTextField(
                value = content,
                onValueChange = onContentChanged,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("code_editor_field"),
                visualTransformation = SyntaxHighlightingTransformation(file.language)
            )
        }
    }

    // Floating touch helper bar (quick brackets complete) at bottom of editor
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            viewModel.quickSymbols.forEach { sym ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            viewModel.updateContent(content + sym)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(sym, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EditorEmptyState(
    onCreateFileClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Draw image asset
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)), RoundedCornerShape(12.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_sano_logo_1783169336304),
                contentDescription = "Sano Logo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("No active document open", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Create a new script or choose from file tree to start Sano Cloud sync.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCreateFileClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Create Sano File", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

// ---------------- TERMINAL & CONSOLE VIEWS ----------------

@Composable
fun BottomTabButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val bg = if (isActive) MaterialTheme.colorScheme.background else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun SanoTerminalView(
    logs: List<String>,
    command: String,
    onCommandChanged: (String) -> Unit,
    onExecute: () -> Unit
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                logs.forEach { line ->
                    val textColor = when {
                        line.startsWith(">") -> MaterialTheme.colorScheme.primary
                        line.startsWith("Error") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = line,
                        color = textColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$ ", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
            BasicTextField(
                value = command,
                onValueChange = onCommandChanged,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).testTag("terminal_input_field"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onExecute() })
            )
        }
    }
}

@Composable
fun SanoConsoleView(logs: List<String>) {
    val runOutputs = logs.filter { !it.startsWith(">") && !it.contains("Terminal") && !it.contains("Type 'help'") }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (runOutputs.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Execute your scripts to see system results in this Console.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            items(runOutputs) { line ->
                Text(line, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun CollabLogsView(logs: List<SyncLog>) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (logs.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No collaboration log sessions registered.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            items(logs) { log ->
                val color = when(log.type) {
                    LogType.SUCCESS -> Color(0xFF10B981)
                    LogType.WARNING -> Color(0xFFFB923C)
                    LogType.CODE_EDIT -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("[${log.timestamp}]", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(log.sender, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(log.action, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ---------------- LOWER STATUS FOOTER BAR ----------------

@Composable
fun SanoStatusBar(
    activeFile: WorkspaceFile?,
    isCollabActive: Boolean,
    isSyncing: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(10.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Sano Cloud Connected",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.weight(1f))

        if (activeFile != null) {
            Text(
                text = "Language: ${activeFile.language.uppercase()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "UTF-8",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ---------------- NEW FILE DIALOG OVERLAY ----------------

@Composable
fun NewFileDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("python") }
    val languages = listOf("python", "kotlin", "html", "css", "javascript", "sql")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Workspace File", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("File Name (e.g. server_app)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("new_file_name_field")
                )

                Column {
                    Text("Programming Language", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        languages.forEach { lang ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (selectedLanguage == lang) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { selectedLanguage = lang }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = lang.uppercase(),
                                    color = if (selectedLanguage == lang) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name, selectedLanguage)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.testTag("dialog_create_button")
            ) {
                Text("Synchronize", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun NewFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Workspace Folder", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder Name (e.g. components)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("new_folder_name_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.testTag("dialog_create_folder_button")
            ) {
                Text("Create Folder", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun RenameDialog(
    initialName: String,
    isFolder: Boolean,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isFolder) "Rename Folder" else "Rename File", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isFolder) "New Folder Name" else "New File Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("rename_name_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onRename(name)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.testTag("dialog_rename_button")
            ) {
                Text("Rename", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun DeleteConfirmDialog(
    itemName: String,
    isFolder: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isFolder) "Delete Folder" else "Delete File", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.error) },
        text = {
            Text(
                text = if (isFolder) {
                    "Are you sure you want to delete folder '$itemName' and all its contents recursively? This action is irreversible."
                } else {
                    "Are you sure you want to delete file '$itemName'? This action is irreversible."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                modifier = Modifier.testTag("dialog_delete_confirm_button")
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
