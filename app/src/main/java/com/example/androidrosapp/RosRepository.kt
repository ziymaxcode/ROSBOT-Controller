package com.example.androidrosapp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

// Define the possible connection states
enum class ConnectionState {
    IDLE,        // Not connected
    CONNECTING,  // Connection in progress
    CONNECTED,   // WebSocket Open
    DISCONNECTED // Connection lost/failed
}

class RosRepository(private val viewModel: MainViewModel) {

    private var webSocket: WebSocket? = null
    private val client: OkHttpClient

    private val _connectionStatus = MutableStateFlow(ConnectionState.IDLE)
    val connectionStatus: StateFlow<ConnectionState> = _connectionStatus

    private val _feedbackMessage = MutableStateFlow(RobotFeedback())
    val feedbackMessage: StateFlow<RobotFeedback> = _feedbackMessage

    init {
        val logging = HttpLoggingInterceptor().apply { setLevel(HttpLoggingInterceptor.Level.BASIC) }
        client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Keep alive indefinitely
            .build()
    }

    fun connect(ipAddress: String) {
        if (webSocket != null || _connectionStatus.value == ConnectionState.CONNECTING) return

        _connectionStatus.value = ConnectionState.CONNECTING

        try {
            val request = Request.Builder().url("ws://$ipAddress:9090").build()
            webSocket = client.newWebSocket(request, RosWebSocketListener())
        } catch (e: Exception) {
            _connectionStatus.value = ConnectionState.DISCONNECTED
            viewModel.setErrorMessage("Invalid IP: ${e.message}")
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionStatus.value = ConnectionState.DISCONNECTED
    }

    fun sendCommand(jsonMessage: String) {
        if (_connectionStatus.value == ConnectionState.CONNECTED) {
            webSocket?.send(jsonMessage)
        } else {
            viewModel.setErrorMessage("Not connected.")
        }
    }

    private inner class RosWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            println("WebSocket Connected")
            _connectionStatus.value = ConnectionState.CONNECTED
            subscribeToFeedback()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                // 1. Try to decode as standard ROS JSON message
                val outerMessage = Json.decodeFromString<RosMessage<StringMessage>>(text)

                if (outerMessage.op == "publish" && outerMessage.msg != null) {
                    val feedbackJsonString = outerMessage.msg.data
                    val feedbackObject = Json.decodeFromString<RobotFeedback>(feedbackJsonString)
                    _feedbackMessage.value = feedbackObject
                }
            } catch (e: Exception) {
                // 2. PARSING FAILED - Check if it is a Raw Log Message
                // The PSoC sends raw strings like "# NAK:SYSTEM..." or "# ERROR..."
                // which are NOT valid JSON. We capture them here.

                if (text.trim().startsWith("#") || text.trim().startsWith("PSOC")) {
                    println("Received Raw Log: $text")

                    // Create a feedback object that ONLY has the log message
                    val logFeedback = RobotFeedback(logMessage = text)
                    _feedbackMessage.value = logFeedback
                } else {
                    // It's some other garbage, just log it to console
                    println("Ignored malformed message: $text")
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            _connectionStatus.value = ConnectionState.DISCONNECTED
            println("WebSocket Closing: $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connectionStatus.value = ConnectionState.DISCONNECTED
            viewModel.setErrorMessage("Connection Failed: ${t.message}")
            this@RosRepository.webSocket = null
        }
    }

    private fun subscribeToFeedback() {
        val subscriptionMessage = RosMessage<Unit>(
            op = "subscribe",
            topic = "/robot_feedback",
            type = "std_msgs/String"
        )
        val jsonMessage = Json.encodeToString(RosMessage.serializer(Unit.serializer()), subscriptionMessage)
        sendCommand(jsonMessage)
    }
}