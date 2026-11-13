package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.screens.InboxOutboxScreen
import com.example.ui.screens.MeshNetworkScreen
import com.example.ui.screens.TransceiverScreen
import com.example.ui.theme.MeshAudioTheme
import com.example.ui.theme.ThemeMode

const val ROUTE_TRANSCEIVER = "transceiver"
const val ROUTE_INBOX = "inbox"
const val ROUTE_MESH = "mesh"

private data class MeshNavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val meshDestinations = listOf(
    MeshNavDestination(
        route = ROUTE_TRANSCEIVER,
        labelRes = R.string.nav_transceiver,
        selectedIcon = Icons.Filled.GraphicEq,
        unselectedIcon = Icons.Outlined.GraphicEq,
        testTag = "nav_tab_studio"
    ),
    MeshNavDestination(
        route = ROUTE_INBOX,
        labelRes = R.string.nav_inbox,
        selectedIcon = Icons.Filled.Inbox,
        unselectedIcon = Icons.Outlined.Inbox,
        testTag = "nav_tab_notes"
    ),
    MeshNavDestination(
        route = ROUTE_MESH,
        labelRes = R.string.nav_mesh,
        selectedIcon = Icons.Filled.Hub,
        unselectedIcon = Icons.Outlined.Hub,
        testTag = "nav_tab_mesh"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshAudioApp(viewModel: MeshViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_TRANSCEIVER

    val requiredPermissions = remember { buildRequiredMeshPermissions() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val micAndNearbyOk = checkCorePermissionsGranted(context)
        viewModel.onPermissionsUpdated(micAndNearbyOk)
    }

    LaunchedEffect(Unit) {
        val granted = checkCorePermissionsGranted(context)
        viewModel.onPermissionsUpdated(granted)
    }

    LaunchedEffect(uiState.recordingState.errorMessage) {
        val err = uiState.recordingState.errorMessage
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            viewModel.clearRecorderError()
        }
    }

    LaunchedEffect(uiState.playbackState.errorMessage) {
        val err = uiState.playbackState.errorMessage
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            viewModel.clearPlaybackError()
        }
    }

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    MeshAudioTheme(themeMode = uiState.settings.themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = stringResource(R.string.app_full_name),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "${uiState.settings.callsign} · ${uiState.settings.localNodeId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        actions = {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(
                                    text = "OFFLINE P2P",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val next = when (uiState.settings.themeMode) {
                                        ThemeMode.DARK -> ThemeMode.LIGHT
                                        ThemeMode.LIGHT -> ThemeMode.DARK
                                        ThemeMode.SYSTEM -> ThemeMode.DARK
                                    }
                                    viewModel.setThemeMode(next)
                                },
                                modifier = Modifier.testTag("quick_theme_toggle")
                            ) {
                                Icon(
                                    imageVector = if (uiState.settings.themeMode == ThemeMode.LIGHT) {
                                        Icons.Filled.DarkMode
                                    } else {
                                        Icons.Filled.LightMode
                                    },
                                    contentDescription = "Toggle light or dark theme"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            meshDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    modifier = Modifier.testTag(dest.testTag),
                                    icon = {
                                        DestinationBadgeIcon(
                                            dest = dest,
                                            selected = selected,
                                            unreadCount = uiState.unreadCount,
                                            peerCount = uiState.peers.size
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            meshDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    modifier = Modifier.testTag(dest.testTag),
                                    icon = {
                                        DestinationBadgeIcon(
                                            dest = dest,
                                            selected = selected,
                                            unreadCount = uiState.unreadCount,
                                            peerCount = uiState.peers.size
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) }
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_TRANSCEIVER,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(ROUTE_TRANSCEIVER) {
                            TransceiverScreen(
                                uiState = uiState,
                                onRequestPermissions = {
                                    permissionLauncher.launch(requiredPermissions)
                                },
                                onToggleDiscovery = { viewModel.toggleDiscoveryService() },
                                onRefreshSweep = { viewModel.refreshMeshSweep() },
                                onSelectTargetPeer = { viewModel.selectTargetPeer(it) },
                                onStartHoldRecording = { viewModel.startRecording(locked = false) },
                                onFinishHoldRecording = { viewModel.finishAndBroadcastRecording() },
                                onToggleHandsFreeRecording = {
                                    if (uiState.recordingState.isRecording) {
                                        viewModel.finishAndBroadcastRecording()
                                    } else {
                                        viewModel.startRecording(locked = true)
                                    }
                                },
                                onCancelRecording = { viewModel.cancelRecording() },
                                onPlayToggle = { viewModel.togglePlayNote(it) },
                                onSeekNote = { note, frac -> viewModel.seekNoteWaveform(note, frac) },
                                onRelayNote = { viewModel.relayNote(it) },
                                onToggleRead = { viewModel.toggleReadStatus(it) },
                                onDeleteNote = { viewModel.deleteNote(it) },
                                onOpenAllNotes = { navigateTo(ROUTE_INBOX) }
                            )
                        }

                        composable(ROUTE_INBOX) {
                            InboxOutboxScreen(
                                uiState = uiState,
                                onSelectFilter = { viewModel.setFilter(it) },
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                onMarkAllRead = { viewModel.markAllNotesRead() },
                                onPlayToggle = { viewModel.togglePlayNote(it) },
                                onSeekNote = { note, frac -> viewModel.seekNoteWaveform(note, frac) },
                                onRelayNote = { viewModel.relayNote(it) },
                                onToggleRead = { viewModel.toggleReadStatus(it) },
                                onDeleteNote = { viewModel.deleteNote(it) }
                            )
                        }

                        composable(ROUTE_MESH) {
                            MeshNetworkScreen(
                                uiState = uiState,
                                onToggleDiscovery = { viewModel.toggleDiscoveryService() },
                                onRefreshSweep = { viewModel.refreshMeshSweep() },
                                onConnectPeer = { viewModel.connectPeer(it) },
                                onTargetPeer = { peer ->
                                    val nextId = if (uiState.selectedTargetPeerId == peer.nodeId) null else peer.nodeId
                                    viewModel.selectTargetPeer(nextId)
                                    navigateTo(ROUTE_TRANSCEIVER)
                                },
                                onUpdateCallsign = { viewModel.updateCallsign(it) },
                                onToggleAutoRelay = { viewModel.setAutoRelay(it) },
                                onMaxHopsChange = { viewModel.setMaxHopTtl(it) },
                                onThemeModeChange = { viewModel.setThemeMode(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationBadgeIcon(
    dest: MeshNavDestination,
    selected: Boolean,
    unreadCount: Int,
    peerCount: Int
) {
    val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
    val badgeValue = when (dest.route) {
        ROUTE_INBOX -> unreadCount
        ROUTE_MESH -> peerCount
        else -> 0
    }
    if (badgeValue > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(badgeValue.toString())
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = stringResource(dest.labelRes))
        }
    } else {
        Icon(imageVector = icon, contentDescription = stringResource(dest.labelRes))
    }
}

private fun buildRequiredMeshPermissions(): Array<String> {
    val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        list.add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        list.add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        list.add(Manifest.permission.BLUETOOTH_SCAN)
        list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        list.add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    return list.toTypedArray()
}

private fun checkCorePermissionsGranted(context: Context): Boolean {
    val micGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val wifiGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.NEARBY_WIFI_DEVICES
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    return micGranted && wifiGranted
}
