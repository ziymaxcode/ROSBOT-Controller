package com.example.androidrosapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * This file contains all the data class structures for messages
 * sent to and received from the rosbridge WebSocket server.
 */

// 1. Generic classes for the rosbridge protocol structure
@Serializable
data class RosMessage<T>(
    val op: String, // "publish", "subscribe", etc.
    val topic: String? = null,
    val msg: T? = null,
    val type: String? = null
)

// 2. Data structures for command and feedback messages
@Serializable
data class PolarMoveCommand(
    val radius: Float,
    val angle: Float
)

@Serializable
data class RobotFeedback(
    @SerialName("moved_radius")
    val movedRadius: Float = 0f,
    @SerialName("moved_angle")
    val movedAngle: Float = 0f,
    @SerialName("error_vector")
    val errorVector: Float = 0f,
    @SerialName("log_message")
    val logMessage: String? = null
)

@Serializable
data class StringMessage(
    val data: String
)

// 3. Data structures for the automation file feature
@Serializable
data class AutomationStep(
    val slNo: Int,
    val radius: Float,
    val angle: Float,
    val delaySeconds: Long,

    // Feedback fields (Nullable because they aren't there initially)
    // Changed to 'val' to work best with the .copy() method in ViewModel
    val movedRadius: Double? = null,
    val movedAngle: Double? = null,
    val errorVector: String? = null,

    // --- NEW FIELDS REQUIRED FOR ERROR CALCULATION ---
    val radiusError: String? = null,
    val angleError: String? = null

)

@Serializable
data class AutomationFile(
    val steps: List<AutomationStep>
)