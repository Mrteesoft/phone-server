package com.phoneserver.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.phoneserver.mobile.runtime.TerminalBackendKind
import com.phoneserver.mobile.runtime.UbuntuInstallPhase
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class AppTab(
        val label: String,
        val icon: ImageVector
) {
    Dashboard("Home", Icons.Rounded.Home),
    Terminal("Terminal", Icons.Rounded.Code),
    Projects("Projects", Icons.Rounded.Folder),
    Services("Services", Icons.Rounded.Storage)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneServerApp(viewModel: PhoneServerViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTabName by rememberSaveable { mutableStateOf(AppTab.Terminal.name) }
    val selectedTab = remember(selectedTabName) { AppTab.valueOf(selectedTabName) }
    val terminalMode = selectedTab == AppTab.Terminal
    val navigationItemColors = if (terminalMode) {
        NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFA7F3D0),
                selectedTextColor = Color(0xFFA7F3D0),
                unselectedIconColor = Color(0xFF9CA3AF),
                unselectedTextColor = Color(0xFF9CA3AF),
                indicatorColor = Color(0xFF101922)
        )
    } else {
        NavigationBarItemDefaults.colors()
    }

    Scaffold(
            modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
            containerColor = if (terminalMode) Color(0xFF05070A) else MaterialTheme.colorScheme.background,
            topBar = {
                if (!terminalMode) {
                    CenterAlignedTopAppBar(
                            title = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Phone Server", fontWeight = FontWeight.SemiBold)
                                    Text(
                                            text = "Local-first Android runtime",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                        containerColor = if (terminalMode) Color(0xFF05070A) else MaterialTheme.colorScheme.surface
                ) {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTabName = tab.name },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                                colors = navigationItemColors
                        )
                    }
                }
            }
    ) { paddingValues ->
        when (selectedTab) {
            AppTab.Dashboard -> DashboardScreen(
                    modifier = Modifier.padding(paddingValues),
                    uiState = uiState,
                    onOpenTerminal = { selectedTabName = AppTab.Terminal.name },
                    onOpenProjects = { selectedTabName = AppTab.Projects.name }
            )

            AppTab.Terminal -> TerminalScreen(
                    modifier = Modifier.padding(paddingValues),
                    uiState = uiState,
                    runtime = uiState.terminalRuntime,
                    ubuntuRuntime = uiState.ubuntuRuntime,
                    onRunCommand = viewModel::runCommand,
                    onClearTerminal = viewModel::clearTerminal,
                    onInterruptTerminal = viewModel::interruptTerminal,
                    onResizeTerminal = viewModel::resizeTerminal,
                    onPrepareUbuntuRuntime = viewModel::prepareUbuntuRuntime,
                    onInstallUbuntuRuntime = viewModel::installUbuntuRuntime,
                    onUseAndroidRuntime = viewModel::useAndroidRuntime,
                    onUseUbuntuRuntime = viewModel::useUbuntuRuntime
            )

            AppTab.Projects -> ProjectsScreen(
                    modifier = Modifier.padding(paddingValues),
                    uiState = uiState,
                    onCreateWorkspace = viewModel::createWorkspace,
                    onUseWorkspace = {
                        viewModel.useWorkspace(it)
                        selectedTabName = AppTab.Terminal.name
                    }
            )

            AppTab.Services -> ServicesScreen(
                    modifier = Modifier.padding(paddingValues),
                    uiState = uiState,
                    runtime = uiState.terminalRuntime,
                    ubuntuRuntime = uiState.ubuntuRuntime,
                    onStartService = viewModel::startManagedService,
                    onStopService = viewModel::stopManagedService,
                    onRestartService = viewModel::restartManagedService,
                    onPrepareUbuntuRuntime = viewModel::prepareUbuntuRuntime,
                    onInstallUbuntuRuntime = viewModel::installUbuntuRuntime,
                    onUseAndroidRuntime = viewModel::useAndroidRuntime,
                    onUseUbuntuRuntime = viewModel::useUbuntuRuntime
            )
        }
    }
}

@Composable
private fun DashboardScreen(
        modifier: Modifier,
        uiState: PhoneServerUiState,
        onOpenTerminal: () -> Unit,
        onOpenProjects: () -> Unit
) {
    LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                    modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                    brush = Brush.linearGradient(
                                            listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.26f),
                                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f)
                                            )
                                    ),
                                    shape = RoundedCornerShape(28.dp)
                            )
                            .padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                            text = "Open the app and use the phone directly",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                    )
                    Text(
                            text = "This build is local-only. No account. No registration. No external server. " +
                                    "Workspaces live inside the app sandbox and commands run on-device.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onOpenTerminal) {
                            Text("Open Terminal")
                        }
                        OutlinedButton(onClick = onOpenProjects) {
                            Text("Create Workspace")
                        }
                    }
                }
            }
        }

        item {
            SummaryCard(
                    title = "Current directory",
                    value = uiState.currentDirectory,
                    supporting = "The terminal starts inside the workspace root."
            )
        }

        item {
            SummaryCard(
                    title = "Workspaces",
                    value = uiState.workspaces.size.toString(),
                    supporting = "Create local folders for repos, builds, and service files."
            )
        }

        item {
            SummaryCard(
                    title = "Runtime state",
                    value = if (uiState.runningCommand) "Command running" else "Idle",
                    supporting = "The shell tab executes commands on-device through the local shell."
            )
        }

        item {
            SummaryCard(
                    title = "Managed services",
                    value = uiState.managedServices.count { it.status == "RUNNING" }.toString(),
                    supporting = "Long-running local commands stay visible in the Services tab."
            )
        }
    }
}

@Composable
private fun TerminalScreen(
        modifier: Modifier,
        uiState: PhoneServerUiState,
        runtime: TerminalRuntimeSummary,
        ubuntuRuntime: UbuntuRuntimeSummary,
        onRunCommand: (String) -> Unit,
        onClearTerminal: () -> Unit,
        onInterruptTerminal: () -> Unit,
        onResizeTerminal: (Int, Int) -> Unit,
        onPrepareUbuntuRuntime: () -> Unit,
        onInstallUbuntuRuntime: () -> Unit,
        onUseAndroidRuntime: () -> Unit,
        onUseUbuntuRuntime: () -> Unit
) {
    var command by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    val terminalInteractionSource = remember { MutableInteractionSource() }
    val terminalTapModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(
                onTap = {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
        )
    }
    val statusIndicatorColor = when {
        runtime.kind == TerminalBackendKind.ANDROID_LOCAL &&
                ubuntuRuntime.phase == UbuntuInstallPhase.FAILED.name -> Color(0xFFFF8B7C)

        runtime.kind == TerminalBackendKind.ANDROID_LOCAL &&
                (ubuntuRuntime.phase == UbuntuInstallPhase.DOWNLOADING_ROOTFS.name ||
                        ubuntuRuntime.phase == UbuntuInstallPhase.EXTRACTING_ROOTFS.name ||
                        ubuntuRuntime.phase == UbuntuInstallPhase.VERIFYING_BOOT.name) -> Color(0xFFF59E0B)

        uiState.runningCommand -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }
    val statusLabel = when {
        runtime.kind == TerminalBackendKind.ANDROID_LOCAL &&
                ubuntuRuntime.phase == UbuntuInstallPhase.DOWNLOADING_ROOTFS.name -> "installing"

        runtime.kind == TerminalBackendKind.ANDROID_LOCAL &&
                ubuntuRuntime.phase == UbuntuInstallPhase.EXTRACTING_ROOTFS.name -> "extracting"

        runtime.kind == TerminalBackendKind.ANDROID_LOCAL &&
                ubuntuRuntime.phase == UbuntuInstallPhase.VERIFYING_BOOT.name -> "verifying"

        runtime.kind == TerminalBackendKind.ANDROID_LOCAL &&
                ubuntuRuntime.phase == UbuntuInstallPhase.FAILED.name -> "failed"

        uiState.runningCommand -> "busy"
        else -> "ready"
    }

    fun submitCommand() {
        val trimmed = command.trim()
        if (trimmed.isEmpty() || uiState.runningCommand) {
            return
        }

        onRunCommand(trimmed)
        command = ""
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(uiState.terminalLines.size) {
        if (uiState.terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(uiState.terminalLines.lastIndex)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
            modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFF05070A))
                    .imePadding()
                    .clickable(
                            interactionSource = terminalInteractionSource,
                            indication = null
                    ) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = uiState.currentDirectory,
                        color = Color(0xFF9CA3AF),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
                Text(
                        text = runtime.displayName,
                        color = Color(0xFF4B5563),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                        modifier = Modifier
                                .size(8.dp)
                                .background(
                                        color = statusIndicatorColor,
                                        shape = CircleShape
                                )
                )
                Text(
                        text = statusLabel,
                        color = Color(0xFF9CA3AF),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                )
            }
        }

        when {
            runtime.kind == TerminalBackendKind.ANDROID_LOCAL && ubuntuRuntime.canUse -> {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = "Ubuntu userspace is ready for Linux commands.",
                            color = Color(0xFF9CA3AF),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onUseUbuntuRuntime) {
                        Text(
                                text = "use ubuntu",
                                color = Color(0xFFA7F3D0),
                                fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            runtime.kind == TerminalBackendKind.ANDROID_LOCAL &&
                    ubuntuRuntime.phase == UbuntuInstallPhase.FAILED.name -> {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = ubuntuRuntime.detail,
                            color = Color(0xFFFF8B7C),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                    )
                    if (ubuntuRuntime.canInstall) {
                        TextButton(onClick = onInstallUbuntuRuntime) {
                            Text(
                                    text = ubuntuRuntime.installButtonLabel.lowercase(),
                                    color = Color(0xFFA7F3D0),
                                    fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            runtime.kind == TerminalBackendKind.ANDROID_LOCAL && ubuntuRuntime.canInstall -> {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = "Ubuntu scaffold is ready. Install Ubuntu Base to enable Linux commands.",
                            color = Color(0xFF9CA3AF),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onInstallUbuntuRuntime) {
                        Text(
                                text = "install ubuntu",
                                color = Color(0xFFA7F3D0),
                                fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            runtime.kind == TerminalBackendKind.ANDROID_LOCAL && ubuntuRuntime.canPrepare -> {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = "Need Linux userspace commands like apt? Prepare Ubuntu first.",
                            color = Color(0xFF9CA3AF),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onPrepareUbuntuRuntime) {
                        Text(
                                text = "prepare ubuntu",
                                color = Color(0xFFA7F3D0),
                                fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            runtime.kind == TerminalBackendKind.ANDROID_LOCAL &&
                    ubuntuRuntime.phase == UbuntuInstallPhase.DOWNLOADING_ROOTFS.name -> {
                Text(
                        text = if (ubuntuRuntime.progressLabel.isNotBlank()) {
                            "Ubuntu download in progress: ${ubuntuRuntime.progressLabel}"
                        } else {
                            ubuntuRuntime.detail
                        },
                        color = Color(0xFF9CA3AF),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                )
            }

            runtime.kind == TerminalBackendKind.ANDROID_LOCAL &&
                    ubuntuRuntime.phase == UbuntuInstallPhase.EXTRACTING_ROOTFS.name -> {
                Text(
                        text = ubuntuRuntime.detail,
                        color = Color(0xFF9CA3AF),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                )
            }

            runtime.kind == TerminalBackendKind.ANDROID_LOCAL &&
                    ubuntuRuntime.phase == UbuntuInstallPhase.VERIFYING_BOOT.name -> {
                Text(
                        text = ubuntuRuntime.detail,
                        color = Color(0xFF9CA3AF),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                )
            }

            runtime.kind == TerminalBackendKind.UBUNTU_2204 -> {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = "Ubuntu userspace active. ${ubuntuRuntime.defaultUsername}@phone boots through /bin/bash --login.",
                            color = Color(0xFF9CA3AF),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onUseAndroidRuntime) {
                        Text(
                                text = "use android",
                                color = Color(0xFFA7F3D0),
                                fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        BoxWithConstraints(
                modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
        ) {
            val minCellWidthPx = with(density) { 9.dp.roundToPx() }
            val minCellHeightPx = with(density) { 20.dp.roundToPx() }

            LazyColumn(
                    modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 6.dp, bottom = 72.dp)
                            .onSizeChanged { size ->
                                val columns = (size.width / minCellWidthPx).coerceAtLeast(40)
                                val rows = (size.height / minCellHeightPx).coerceAtLeast(16)
                                onResizeTerminal(columns, rows)
                            }
                            .then(terminalTapModifier),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.terminalLines, key = { it.id }) { line ->
                    val lineColor = when (line.kind) {
                        TerminalLineKind.COMMAND -> Color(0xFFA7F3D0)
                        TerminalLineKind.OUTPUT -> Color(0xFFF8FAFC)
                        TerminalLineKind.STATUS -> Color(0xFF9CA3AF)
                        TerminalLineKind.ERROR -> Color(0xFFFF8B7C)
                    }

                    SelectionContainer {
                        Text(
                                text = line.text,
                                fontFamily = FontFamily.Monospace,
                                color = lineColor,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                        )
                    }
                }
            }
            Column(
                    modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color(0xFF05070A))
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                            onClick = onInterruptTerminal,
                            enabled = uiState.runningCommand,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                                text = "ctrl+c",
                                color = if (uiState.runningCommand) Color(0xFFFCA5A5) else Color(0xFF6B7280),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(
                            onClick = { command += "\t" },
                            enabled = !uiState.runningCommand,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                                text = "tab",
                                color = if (uiState.runningCommand) Color(0xFF6B7280) else Color(0xFFA7F3D0),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(
                            onClick = {
                                val clipboardText = clipboardManager.getText()?.text.orEmpty()
                                if (clipboardText.isNotEmpty()) {
                                    command += clipboardText
                                }
                            },
                            enabled = !uiState.runningCommand,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                                text = "paste",
                                color = if (uiState.runningCommand) Color(0xFF6B7280) else Color(0xFFA7F3D0),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(
                            onClick = onClearTerminal,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                                text = "clear",
                                color = Color(0xFFA7F3D0),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Text(
                        text = if (runtime.kind == TerminalBackendKind.UBUNTU_2204) {
                            "tap anywhere to type, then press enter or run. ubuntu diagnostics: ${ubuntuRuntime.diagnosticsPath.ifBlank { "pending" }}"
                        } else {
                            "tap anywhere to type, then press enter or run"
                        },
                        color = Color(0xFF4B5563),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                )
                Row(
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = buildPrompt(
                                    runtime = runtime,
                                    ubuntuRuntime = ubuntuRuntime,
                                    currentDirectory = uiState.currentDirectory
                            ),
                            color = Color(0xFFA7F3D0),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    BasicTextField(
                            modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyUp &&
                                                (event.key == Key.Enter || event.key == Key.NumPadEnter)
                                        ) {
                                            submitCommand()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                            value = command,
                            onValueChange = { command = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color(0xFFF8FAFC),
                                    fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(Color(0xFFA7F3D0)),
                            keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done,
                                    autoCorrectEnabled = false
                            ),
                            keyboardActions = KeyboardActions(
                                    onDone = { submitCommand() }
                            ),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (command.isEmpty()) {
                                        Text(
                                                text = if (uiState.runningCommand) {
                                                    "command running..."
                                                } else {
                                                    runtime.commandHint
                                                },
                                                color = Color(0xFF6B7280),
                                                fontFamily = FontFamily.Monospace,
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    TextButton(
                            onClick = ::submitCommand,
                            enabled = command.isNotBlank() && !uiState.runningCommand,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                                text = if (uiState.runningCommand) "busy" else "run",
                                color = if (uiState.runningCommand) Color(0xFF6B7280) else Color(0xFFA7F3D0),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectsScreen(
        modifier: Modifier,
        uiState: PhoneServerUiState,
        onCreateWorkspace: (String) -> Unit,
        onUseWorkspace: (String) -> Unit
) {
    var workspaceName by rememberSaveable { mutableStateOf("") }

    LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                    colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
            ) {
                Column(
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Create a local workspace", style = MaterialTheme.typography.titleLarge)
                    Text(
                            text = "Workspaces are regular directories inside the app sandbox. " +
                                    "Start here before cloning repos or running project commands.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = workspaceName,
                            onValueChange = { workspaceName = it },
                            label = { Text("Workspace name") },
                            singleLine = true
                    )
                    Button(
                            onClick = {
                                onCreateWorkspace(workspaceName)
                                workspaceName = ""
                            },
                            enabled = workspaceName.isNotBlank()
                    ) {
                        Text("Create Workspace")
                    }
                }
            }
        }

        if (uiState.workspaces.isEmpty()) {
            item {
                SummaryCard(
                        title = "No workspaces yet",
                        value = "0",
                        supporting = "Create one above and jump into it from the terminal."
                )
            }
        } else {
            items(uiState.workspaces, key = { it.path }) { workspace ->
                Card(
                        colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                ) {
                    Column(
                            modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(workspace.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                                text = workspace.path,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                                text = "${workspace.fileCount} items",
                                style = MaterialTheme.typography.labelLarge
                        )
                        TextButton(onClick = { onUseWorkspace(workspace.path) }) {
                            Text("Use In Terminal")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServicesScreen(
        modifier: Modifier,
        uiState: PhoneServerUiState,
        runtime: TerminalRuntimeSummary,
        ubuntuRuntime: UbuntuRuntimeSummary,
        onStartService: (String) -> Unit,
        onStopService: (String) -> Unit,
        onRestartService: (String) -> Unit,
        onPrepareUbuntuRuntime: () -> Unit,
        onInstallUbuntuRuntime: () -> Unit,
        onUseAndroidRuntime: () -> Unit,
        onUseUbuntuRuntime: () -> Unit
) {
    var serviceCommand by rememberSaveable { mutableStateOf("") }

    LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SummaryCard(
                    title = "Service supervisor",
                    value = if (uiState.managedServices.any { it.status == "RUNNING" }) "Active" else "Ready",
                    supporting = "Run long-lived local commands from the current workspace and stop or restart them here."
            )
        }

        item {
            Card(
                    colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
            ) {
                Column(
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Terminal runtime", style = MaterialTheme.typography.titleMedium)
                    Text(runtime.displayName, style = MaterialTheme.typography.labelLarge)
                    Text(
                            text = runtime.detail,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                            text = "Home: ${runtime.homeDirectory}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onUseAndroidRuntime) {
                            Text("Use Android Shell")
                        }
                        OutlinedButton(
                                onClick = onUseUbuntuRuntime,
                                enabled = ubuntuRuntime.canUse
                        ) {
                            Text("Use Ubuntu")
                        }
                    }
                }
            }
        }

        item {
            Card(
                    colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
            ) {
                Column(
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(ubuntuRuntime.releaseLabel, style = MaterialTheme.typography.titleMedium)
                    Text(ubuntuRuntime.phase, style = MaterialTheme.typography.labelLarge)
                    Text(
                            text = ubuntuRuntime.detail,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ubuntuRuntime.progressLabel.isNotBlank()) {
                        Text(
                                text = ubuntuRuntime.progressLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                            text = "Architecture: ${ubuntuRuntime.architecture.ifBlank { "unknown" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                            text = "Rootfs path: ${ubuntuRuntime.rootfsPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                            text = "Guest home: ${ubuntuRuntime.guestHomePath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                            text = "Default user: ${ubuntuRuntime.defaultUsername}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                            text = "Archive cache: ${ubuntuRuntime.archivePath.ifBlank { "not prepared yet" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                            text = "Source: ${ubuntuRuntime.sourceUrl.ifBlank { "unsupported on this device" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                            text = "Workspace mount source: ${ubuntuRuntime.workspaceMountPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                            text = "Diagnostics log: ${ubuntuRuntime.diagnosticsPath.ifBlank { "not generated yet" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ubuntuRuntime.diagnosticsPreview.isNotBlank()) {
                        Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF101922)
                        ) {
                            Text(
                                    modifier = Modifier.padding(12.dp),
                                    text = ubuntuRuntime.diagnosticsPreview,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFB9D7B0),
                                    style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                                onClick = onPrepareUbuntuRuntime,
                                enabled = ubuntuRuntime.canPrepare
                        ) {
                            Text("Prepare Ubuntu")
                        }
                        OutlinedButton(
                                onClick = onInstallUbuntuRuntime,
                                enabled = ubuntuRuntime.canInstall
                        ) {
                            Text(ubuntuRuntime.installButtonLabel)
                        }
                    }
                }
            }
        }

        items(uiState.services, key = { it.name }) { service ->
            Card(
                    colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
            ) {
                Column(
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(service.name, style = MaterialTheme.typography.titleMedium)
                    Text(service.status, style = MaterialTheme.typography.labelLarge)
                    Text(
                            text = service.detail,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                    colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
            ) {
                Column(
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Start managed service", style = MaterialTheme.typography.titleMedium)
                    Text(
                            text = "This runs a long-lived command in the current terminal directory, for example `python -m http.server 8080`.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = serviceCommand,
                            onValueChange = { serviceCommand = it },
                            label = { Text("Service command") },
                            singleLine = true
                    )
                    Button(
                            onClick = {
                                onStartService(serviceCommand)
                                serviceCommand = ""
                            },
                            enabled = serviceCommand.isNotBlank()
                    ) {
                        Text("Start Service")
                    }
                }
            }
        }

        if (uiState.managedServices.isEmpty()) {
            item {
                SummaryCard(
                        title = "No managed services",
                        value = "0",
                        supporting = "Start a long-running command here and it will stay visible until stopped."
                )
            }
        } else {
            items(uiState.managedServices, key = { it.id }) { service ->
                Card(
                        colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                ) {
                    Column(
                            modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(service.name, style = MaterialTheme.typography.titleMedium)
                        Text(service.status, style = MaterialTheme.typography.labelLarge)
                        Text(
                                text = service.command,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                                text = service.workingDirectory,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                                text = "Started: ${service.startedAt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (service.outputPreview.isNotBlank()) {
                            Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF101922)
                            ) {
                                Text(
                                        modifier = Modifier.padding(12.dp),
                                        text = service.outputPreview,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFB9D7B0),
                                        style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (service.lastExitCode != null) {
                            Text(
                                    text = "Last exit code: ${service.lastExitCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { onRestartService(service.id) }) {
                                Text("Restart")
                            }
                            Button(onClick = { onStopService(service.id) }) {
                                Text("Stop")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildPrompt(
        runtime: TerminalRuntimeSummary,
        ubuntuRuntime: UbuntuRuntimeSummary,
        currentDirectory: String
): String {
    val (user, homePath) = when (runtime.kind) {
        TerminalBackendKind.UBUNTU_2204 -> ubuntuRuntime.defaultUsername to ubuntuRuntime.guestHomePath
        TerminalBackendKind.ANDROID_LOCAL -> "android" to runtime.homeDirectory
    }

    return "$user@phone:${formatPromptPath(currentDirectory, homePath)}\$"
}

private fun formatPromptPath(
        currentDirectory: String,
        homePath: String
): String {
    return when {
        currentDirectory == homePath -> "~"
        currentDirectory.startsWith("$homePath/") -> "~/${currentDirectory.removePrefix("$homePath/")}"
        else -> currentDirectory
    }
}

@Composable
private fun SummaryCard(
        title: String,
        value: String,
        supporting: String
) {
    Card(
            colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
    ) {
        Column(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
