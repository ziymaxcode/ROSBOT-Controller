package com.example.androidrosapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs

@Composable
fun ManualControlScreen(viewModel: MainViewModel) {
    // UI State for inputs
    var radiusInput by remember { mutableStateOf("") }
    var angleInput by remember { mutableStateOf("") }

    // Observables from ViewModel
    val feedback by viewModel.robotFeedback.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Parse inputs safely to numbers for calculation
    val targetRadius = radiusInput.toFloatOrNull() ?: 0f
    val targetAngle = angleInput.toFloatOrNull() ?: 0f

    // --- ERROR CALCULATION LOGIC (PERCENTAGE) ---
    // Calculate percentage error: ((Target - Actual) / Target) * 100
    val radiusErrorStr = calculateManualPercentage(targetRadius, feedback.movedRadius.toDouble())
    val angleErrorStr = calculateManualPercentage(targetAngle, feedback.movedAngle.toDouble())

    // Format Strings for Display (2 decimal places)
    val movedRadiusStr = String.format(Locale.US, "%.2f", feedback.movedRadius)
    val movedAngleStr = String.format(Locale.US, "%.2f", feedback.movedAngle)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Manual Control", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        // --- INPUTS (With Number Pad) ---
        OutlinedTextField(
            value = radiusInput,
            onValueChange = { radiusInput = it },
            label = { Text("Radius (cm)") },
            // Forces numeric keypad
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = angleInput,
            onValueChange = { angleInput = it },
            label = { Text("Angle (deg)") },
            // Forces numeric keypad
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        // --- ACTION BUTTONS ROW (MOVE and ABORT) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Move Button
            Button(
                onClick = { viewModel.sendManualMoveCommand(targetRadius, targetAngle) },
                enabled = isConnected,
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("MOVE ROBOT")
            }

            // Abort Button (Red)
            Button(
                onClick = {
                    // 1. Instantly send 0,0 to stop the robot motors
                    viewModel.sendManualMoveCommand(0f, 0f)

                    // 2. Clear the text boxes for safety
                    radiusInput = ""
                    angleInput = ""

                    // 3. Show a quick warning message via the ViewModel's snackbar system
                    viewModel.setErrorMessage("Manual Abort Triggered: Motors Stopped")
                },
                enabled = isConnected,
                modifier = Modifier.weight(1f).height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White
                )
            ) {
                Text("ABORT")
            }
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // --- FEEDBACK SECTION ---
        Text("Real-time Telemetry", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

        // Row 1: Actual Movement
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TelemetryBox("Moved Radius", movedRadiusStr)
            TelemetryBox("Moved Angle", movedAngleStr)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 2: Calculated Errors (Red/Warning colors)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TelemetryBox("Radius Error %", radiusErrorStr, isError = true)
            TelemetryBox("Angle Error %", angleErrorStr, isError = true)
        }

        // --- ERROR MESSAGE DISPLAY ---
        // (Note: This is an inline display of errorMessage, but the main app scaffold also shows snackbars!)
        if (!errorMessage.isNullOrEmpty()) {
            Text(
                text = "Error: $errorMessage",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

// Helper to calculate percentage error locally for Manual Screen
fun calculateManualPercentage(target: Float, actual: Double): String {
    // If target is 0, we can't calculate percentage error (division by zero)
    if (target == 0f) return "-"

    val diff = target - actual
    val percentage = (diff / target) * 100
    return String.format(Locale.US, "%.2f%%", percentage)
}

// Helper Composable for the data boxes
@Composable
fun TelemetryBox(label: String, value: String, isError: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            // Use Red tint for error boxes, standard tint for normal data
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.width(160.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}