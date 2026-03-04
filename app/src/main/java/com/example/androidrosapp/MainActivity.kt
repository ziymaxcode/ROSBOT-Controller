package com.example.androidrosapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidrosapp.ui.theme.AndroidRosAppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- FORCE WI-FI BINDING ---
        // This ensures the app uses the Robot's Wi-Fi (even if no internet)
        // instead of switching to Mobile Data (4G/5G).
        forceWifiUsage()

        setContent {
            AndroidRosAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RobotControllerApp(mainViewModel)
                }
            }
        }
    }

    /**
     * Forces the app to bind traffic to the Wi-Fi network if available.
     * Solves the issue where Android switches to 4G/5G when Wi-Fi has no internet.
     */
    private fun forceWifiUsage() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Bind the app/process to use this Wi-Fi network exclusively
                connectivityManager.bindProcessToNetwork(network)
            }

            override fun onLost(network: Network) {
                // Unbind when Wi-Fi is lost so 4G works again
                connectivityManager.bindProcessToNetwork(null)
            }
        })
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RobotControllerApp(viewModel: MainViewModel) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val connectionState by viewModel.connectionState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // State to toggle the Log Dialog
    var showLogDialog by remember { mutableStateOf(false) }

    // Show Snackbar for critical errors (Transient)
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            if (it.isNotEmpty()) {
                snackbarHostState.showSnackbar(message = it, actionLabel = "Dismiss", duration = SnackbarDuration.Short)
                viewModel.clearErrorMessage()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ROS BOT CONTROLLER") },
                actions = {
                    // Status Text
                    val (statusText, statusColor) = when (connectionState) {
                        ConnectionState.IDLE -> "Idle" to Color.Gray
                        ConnectionState.CONNECTING -> "..." to Color.Yellow
                        ConnectionState.CONNECTED -> "Live" to Color.Green
                        ConnectionState.DISCONNECTED -> "Offline" to Color.Red
                    }
                    Text(text = statusText, color = statusColor, modifier = Modifier.padding(end = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    // Diagnostic Log Button (Info Icon)
                    IconButton(onClick = { showLogDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "System Logs", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            ConnectionBar(viewModel = viewModel, connectionState = connectionState)
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> ManualControlScreen(viewModel = viewModel)
                    1 -> AutomatedControlScreen(viewModel = viewModel)
                }
            }
        }

        // Show the Log Dialog overlay if requested
        if (showLogDialog) {
            DiagnosticLogDialog(
                viewModel = viewModel,
                onDismiss = { showLogDialog = false }
            )
        }
    }
}

// --- DIAGNOSTIC LOG DIALOG ---
@Composable
fun DiagnosticLogDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val logs by viewModel.systemLogs.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("System Diagnostics") },
        text = {
            // Box with fixed height to allow scrolling of long lists
            Box(modifier = Modifier.height(400.dp).fillMaxWidth()) {
                if (logs.isEmpty()) {
                    Text("No logs received yet.", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))
                    ) {
                        items(logs) { log ->
                            LogItemRow(log)
                            Divider(color = Color.LightGray, thickness = 0.5.dp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.clearLogs() }) { Text("Clear") }
        }
    )
}

@Composable
fun LogItemRow(log: MainViewModel.SystemLog) {
    // Color coding for different log levels
    val textColor = when (log.type) {
        MainViewModel.LogType.ERROR -> Color.Red
        MainViewModel.LogType.WARNING -> Color(0xFFE65100) // Dark Orange
        MainViewModel.LogType.INFO -> Color.Black
    }

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    val timeStr = timeFormat.format(Date(log.timestamp))

    Column(modifier = Modifier.padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Source Tag (PSoC / System)
            Text(
                text = "[${log.tag}]",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(end = 8.dp)
            )
            // Timestamp
            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        // The Actual Message (supports multiple lines)
        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontWeight = if (log.type == MainViewModel.LogType.ERROR) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// --- COLLAPSIBLE CONNECTION BAR ---
@Composable
fun ConnectionBar(viewModel: MainViewModel, connectionState: ConnectionState) {
    var selectedDeviceName by remember { mutableStateOf("No Device Selected") }
    var showDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var targetIp by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(true) }

    // NEW: State for the Shutdown Confirmation Warning
    var showShutdownWarning by remember { mutableStateOf(false) }

    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            isExpanded = false
        }
    }

    val isConnected = connectionState == ConnectionState.CONNECTED
    val isConnecting = connectionState == ConnectionState.CONNECTING

    if (showDialog) {
        DeviceDiscoveryDialog(
            viewModel = viewModel,
            onDismiss = { showDialog = false },
            onDeviceSelected = { device, ip ->
                selectedDeviceName = device.serviceName
                targetIp = ip
                showDialog = false
            }
        )
    }

    // NEW: The Shutdown Confirmation Dialog
    if (showShutdownWarning) {
        AlertDialog(
            onDismissRequest = { showShutdownWarning = false },
            title = { Text("⚠ Power Off Robot") },
            text = { Text("Are you sure you want to completely shut down the Raspberry Pi OS? You will lose connection and must physically restart the robot.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendShutdownCommand()
                        viewModel.disconnect() // Disconnect the app after sending
                        showShutdownWarning = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Shut Down") }
            },
            dismissButton = {
                TextButton(onClick = { showShutdownWarning = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp).animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Connection Manager", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))

                if (!isExpanded) {
                    val summaryText = if (isConnected) selectedDeviceName else "Not Connected"
                    val summaryColor = if (isConnected) Color(0xFF006400) else Color.Gray
                    Text(summaryText, fontSize = 12.sp, color = summaryColor, modifier = Modifier.padding(end = 8.dp))
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = selectedDeviceName,
                        onValueChange = {},
                        label = { Text("Selected Robot") },
                        readOnly = true,
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = { showDialog = true }, enabled = !isConnected) {
                                Icon(Icons.Default.Search, contentDescription = "Scan")
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetIp,
                        onValueChange = { targetIp = it },
                        label = { Text("IP Address") },
                        modifier = Modifier.weight(1f),
                        enabled = !isConnected,
                        singleLine = true
                    )

                    // Main Connect / Disconnect Button
                    Button(
                        onClick = {
                            if (isConnected) {
                                viewModel.disconnect()
                                selectedDeviceName = "No Device Selected"
                            } else {
                                if (targetIp.isNotEmpty()) {
                                    viewModel.connect(targetIp)
                                } else {
                                    viewModel.setErrorMessage("Please scan and select a robot first.")
                                }
                            }
                        },
                        enabled = !isConnecting
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Text(if (isConnected) "Disconnect" else "Connect")
                        }
                    }
                }

                // --- NEW: THE SHUTDOWN BUTTON ---
                if (isConnected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showShutdownWarning = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                    ) {
                        Text("⚠ POWER OFF ROBOT")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { isExpanded = false }) {
                        Text("Close Section", color = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(16.dp).padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceDiscoveryDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onDeviceSelected: (NsdServiceInfo, String) -> Unit
) {
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }

    AlertDialog(
        onDismissRequest = {
            viewModel.stopDiscovery()
            onDismiss()
        },
        title = { Text("Available Robots") },
        text = {
            Column(modifier = Modifier.height(300.dp)) {
                if (isScanning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Scanning local network...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                }

                if (discoveredDevices.isEmpty() && !isScanning) {
                    Text("No robots found. Ensure Robot is on and connected to Wi-Fi.", color = Color.Red)
                }

                LazyColumn {
                    items(discoveredDevices) { device ->
                        val host = device.host
                        val ip = host?.hostAddress ?: ""

                        ListItem(
                            headlineContent = { Text(device.serviceName, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("IP: $ip") },
                            modifier = Modifier
                                .clickable {
                                    viewModel.stopDiscovery()
                                    onDeviceSelected(device, ip)
                                }
                                .padding(vertical = 4.dp)
                        )
                        Divider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.stopDiscovery()
                onDismiss()
            }) {
                Text("Cancel")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.startDiscovery() }) {
                Row {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rescan")
                }
            }
        }
    )
}