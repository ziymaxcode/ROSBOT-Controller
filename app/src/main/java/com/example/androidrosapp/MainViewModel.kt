package com.example.androidrosapp

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * MainViewModel acts as the central logic hub.
 * Extends AndroidViewModel to access system services (like NsdManager) for device discovery.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Repository for ROS communication
    private val rosRepository = RosRepository(this)

    // System Service for Network Service Discovery (mDNS/Bonjour)
    private val nsdManager = application.getSystemService(Context.NSD_SERVICE) as NsdManager

    // --- Connection State ---
    val connectionState: StateFlow<ConnectionState> = rosRepository.connectionStatus

    // Derived boolean for simple UI checks
    val isConnected: StateFlow<Boolean> = connectionState.map {
        it == ConnectionState.CONNECTED
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val robotFeedback = rosRepository.feedbackMessage

    // --- Error Handling ---
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    // --- Automation State ---
    private val _automationStepsList = MutableStateFlow<List<AutomationStep>>(emptyList())
    val automationStepsList = _automationStepsList.asStateFlow()
    private var currentFeedbackStepIndex = 0

    // --- DISCOVERY STATE ---
    // List of found robots on the network
    private val _discoveredDevices = MutableStateFlow<List<NsdServiceInfo>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // The specific service type rosbridge uses (tcp)
    private val SERVICE_TYPE = "_rosbridge._tcp."

    // --- LOGGING STATE ---
    data class SystemLog(val timestamp: Long, val tag: String, val message: String, val type: LogType)
    enum class LogType { ERROR, WARNING, INFO }

    private val _systemLogs = MutableStateFlow<List<SystemLog>>(emptyList())
    val systemLogs = _systemLogs.asStateFlow()

    init {
        // Observe incoming feedback
        viewModelScope.launch {
            robotFeedback.collect { feedback ->
                // Filter out initial empty state for automation updates
                if (feedback.movedRadius != 0f || feedback.movedAngle != 0f || feedback.errorVector != 0f) {
                    updateAutomationStepFeedback(feedback)
                }

                // --- PROCESS LOGS ---
                // If the PSoC or Pi sent a log message, process it
                if (!feedback.logMessage.isNullOrEmpty()) {
                    addSystemLog(feedback.logMessage)
                }
            }
        }
    }

    /**
     * Parses incoming log strings.
     * Supports strict format "PSOC:<LEVEL>:<MODULE>:<MESSAGE>"
     * AND legacy format "# ERROR: ..."
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun addSystemLog(rawMessage: String) {
        var type = LogType.INFO
        var tag = "System"
        var message = rawMessage

        // 1. Check for Strict "Best Practice" Protocol
        if (rawMessage.startsWith("PSOC:")) {
            val parts = rawMessage.split(":", limit = 4)
            if (parts.size >= 4) {
                // Parse Level
                type = when(parts[1]) {
                    "ERR" -> LogType.ERROR
                    "WRN" -> LogType.WARNING
                    else -> LogType.INFO
                }

                // Parse Module (e.g., MOTOR)
                tag = "PSoC ${parts[2]}"

                // Parse Message
                message = parts[3].trim()
            }
        }
        // 2. Fallback: Parse legacy C-style messages
        else {
            if (rawMessage.contains("ERROR") || rawMessage.contains("FATAL") || rawMessage.contains("ASSERT")) {
                type = LogType.ERROR
            } else if (rawMessage.contains("WARN")) {
                type = LogType.WARNING
            }

            tag = if (rawMessage.contains("#")) "PSoC" else "Pi/System"
            message = rawMessage.replace("#", "").trim()
        }

        // Create Log Entry
        val newLog = SystemLog(
            timestamp = System.currentTimeMillis(),
            tag = tag,
            message = message,
            type = type
        )

        // Update List (Keep last 50 logs)
        val currentLogs = _systemLogs.value.toMutableList()
        currentLogs.add(0, newLog)
        if (currentLogs.size > 50) currentLogs.removeLast()
        _systemLogs.value = currentLogs

        // Show Snackbar ONLY for critical errors so user sees it immediately
        if (type == LogType.ERROR) {
            setErrorMessage("$tag: $message")
        }
    }

    fun clearLogs() {
        _systemLogs.value = emptyList()
    }
    fun sendShutdownCommand() {
        // We use a dedicated topic "/sys_command" to keep it separate from motor commands
        val command = """
            {
              "op": "publish",
              "topic": "/sys_command",
              "msg": { "data": "SHUTDOWN" }
            }
        """.trimIndent()

        rosRepository.sendCommand(command)
        setErrorMessage("Shutdown signal sent to Robot.")
    }
    // --- Discovery Functions ---

    fun startDiscovery() {
        _discoveredDevices.value = emptyList()
        _isScanning.value = true

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NSD", "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d("NSD", "Service found: $service")
                if (service.serviceType.contains("_rosbridge._tcp")) {
                    // Resolve IP
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e("NSD", "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            Log.d("NSD", "Resolve Succeeded. $serviceInfo")
                            val currentList = _discoveredDevices.value.toMutableList()
                            // Avoid duplicates
                            if (currentList.none { it.serviceName == serviceInfo.serviceName }) {
                                currentList.add(serviceInfo)
                                _discoveredDevices.value = currentList
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e("NSD", "service lost: $service")
                val currentList = _discoveredDevices.value.toMutableList()
                currentList.removeAll { it.serviceName == service.serviceName }
                _discoveredDevices.value = currentList
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _isScanning.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isScanning.value = false
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            _errorMessage.value = "Discovery error: ${e.message}"
        }
    }

    fun stopDiscovery() {
        try {
            discoveryListener?.let {
                nsdManager.stopServiceDiscovery(it)
            }
        } catch (e: Exception) {
            // Ignore if already stopped
        }
        _isScanning.value = false
    }

    // --- Connection Functions ---

    fun connect(ipAddress: String) {
        stopDiscovery() // Stop scanning when connecting
        viewModelScope.launch {
            clearErrorMessage()
            _automationStepsList.value = emptyList()
            currentFeedbackStepIndex = 0
            rosRepository.connect(ipAddress)
        }
    }

    fun disconnect() {
        rosRepository.disconnect()
        clearErrorMessage()
        _automationStepsList.value = emptyList()
        currentFeedbackStepIndex = 0
    }

    // --- Error Functions ---
    fun setErrorMessage(message: String) { _errorMessage.value = message }
    fun clearErrorMessage() { _errorMessage.value = null }

    // --- Command Functions ---
    fun sendManualMoveCommand(radius: Float, angle: Float) {
        val command = PolarMoveCommand(radius = radius, angle = angle)
        val commandJsonString = Json.encodeToString(command)
        val stringMessage = StringMessage(data = commandJsonString)
        val rosMessage = RosMessage(
            op = "publish",
            topic = "/polar_move_cmd",
            type = "std_msgs/String",
            msg = stringMessage
        )
        val jsonMessage = Json.encodeToString(rosMessage)
        rosRepository.sendCommand(jsonMessage)
    }

    fun sendAutomationFile(automationFile: AutomationFile) {
        currentFeedbackStepIndex = 0
        _automationStepsList.value = automationFile.steps
        val fileJsonString = Json.encodeToString(automationFile)
        val stringMessage = StringMessage(data = fileJsonString)
        val rosMessage = RosMessage(
            op = "publish",
            topic = "/polar_move_cmd",
            type = "std_msgs/String",
            msg = stringMessage
        )
        val jsonMessage = Json.encodeToString(rosMessage)
        rosRepository.sendCommand(jsonMessage)
    }

    fun abortAutomation() {
        // Send Radius=0, Angle=0 to stop immediately
        sendManualMoveCommand(0f, 0f)
        currentFeedbackStepIndex = 0
    }

    // --- Helper Functions ---
    private fun updateAutomationStepFeedback(feedback: RobotFeedback) {
        val currentList = _automationStepsList.value.toMutableList()

        if (currentFeedbackStepIndex < currentList.size) {
            // Get the current step object
            val stepToUpdate = currentList[currentFeedbackStepIndex]

            // --- ERROR CALCULATION ---
            // Calculate differences (Target - Actual)
            val rErrorVal = stepToUpdate.radius - feedback.movedRadius
            val aErrorVal = stepToUpdate.angle - feedback.movedAngle

            val updatedStep = stepToUpdate.copy(
                movedRadius = feedback.movedRadius.toDouble(),
                movedAngle = feedback.movedAngle.toDouble(),
                errorVector = String.format(Locale.US, "%.2f%%", feedback.errorVector),
                // Save calculated errors as strings for the data model
                radiusError = String.format(Locale.US, "%.2f", rErrorVal),
                angleError = String.format(Locale.US, "%.2f", aErrorVal)
            )

            currentList[currentFeedbackStepIndex] = updatedStep
            _automationStepsList.value = currentList
            currentFeedbackStepIndex++
        }
    }

    fun updateStepsListForDisplay(steps: List<AutomationStep>) {
        _automationStepsList.value = steps
        currentFeedbackStepIndex = 0
        clearErrorMessage()
    }
    fun exportToCsv(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val sb = StringBuilder()

                // 1. Add CSV Header
                sb.append("Sl No,Target Radius (cm),Target Angle (deg),Delay (s),Actual Radius,Actual Angle,Radius Error %,Angle Error %\n")

                // 2. Add Data Rows
                _automationStepsList.value.forEach { step ->
                    val actualR = step.movedRadius?.let { String.format(Locale.US, "%.2f", it) } ?: "-"
                    val actualA = step.movedAngle?.let { String.format(Locale.US, "%.2f", it) } ?: "-"

                    val rErrVal = if (step.radius != 0f && step.movedRadius != null) {
                        kotlin.math.abs((step.radius - step.movedRadius) / step.radius * 100)
                    } else 0.0

                    val aErrVal = if (step.angle != 0f && step.movedAngle != null) {
                        kotlin.math.abs((step.angle - step.movedAngle) / step.angle * 100)
                    } else 0.0

                    val rErrStr = if (step.movedRadius != null) String.format(Locale.US, "%.2f%%", rErrVal) else "-"
                    val aErrStr = if (step.movedAngle != null) String.format(Locale.US, "%.2f%%", aErrVal) else "-"

                    sb.append("${step.slNo},${step.radius},${step.angle},${step.delaySeconds},$actualR,$actualA,$rErrStr,$aErrStr\n")
                }

                // 3. Write to File
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(sb.toString().toByteArray())
                }

                // Notify User
                setErrorMessage("Success: Report exported successfully!")

            } catch (e: Exception) {
                e.printStackTrace()
                setErrorMessage("Export Failed: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
        stopDiscovery()
    }
}