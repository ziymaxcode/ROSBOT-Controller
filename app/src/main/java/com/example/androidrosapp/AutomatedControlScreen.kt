package com.example.androidrosapp

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun AutomatedControlScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val automationSteps by viewModel.automationStepsList.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    // 1. File picker for IMPORTING Excel
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                val steps = parseExcelFile(context, it)
                if (steps.isNotEmpty()) {
                    viewModel.updateStepsListForDisplay(steps)
                }
            }
        }
    )

    // 2. File saver for EXPORTING CSV
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri: Uri? ->
            uri?.let {
                viewModel.exportToCsv(context, it)
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Automation", fontSize = 20.sp, style = MaterialTheme.typography.titleMedium)

        // --- File Management Row ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel")) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Load Excel")
            }

            // EXPORT BUTTON
            Button(
                onClick = {
                    // Generate a Readable Date Format (e.g., 2026-02-01_15-30-45)
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                    val timestamp = dateFormat.format(Date())
                    val fileName = "Robot_Report_$timestamp.csv"

                    exportLauncher.launch(fileName)
                },
                enabled = automationSteps.isNotEmpty(), // Only enable if there is data
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)) // Green for CSV/Excel
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export")
            }
        }

        // --- Control Buttons Row (Send and Abort) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val automationFile = AutomationFile(steps = automationSteps)
                    viewModel.sendAutomationFile(automationFile)
                },
                enabled = isConnected && automationSteps.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Send")
            }

            Button(
                onClick = {
                    viewModel.abortAutomation()
                },
                enabled = isConnected,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White
                )
            ) {
                Text("Abort")
            }
        }

        // --- Table Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.LightGray)
                .padding(8.dp)
        ) {
            val headerSize = 12.sp

            Text("#", modifier = Modifier.weight(0.35f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = headerSize, maxLines = 1, softWrap = false)
            Text("Rad", modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = headerSize, maxLines = 1, softWrap = false)
            Text("Ang", modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = headerSize, maxLines = 1, softWrap = false)
            Text("Dly", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = headerSize, maxLines = 1, softWrap = false)

            Text("Act.R", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = headerSize, maxLines = 1, softWrap = false)
            Text("Act.A", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = headerSize, maxLines = 1, softWrap = false)

            Text("R.Err%", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.Red, fontSize = headerSize, maxLines = 1, softWrap = false)
            Text("A.Err%", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.Red, fontSize = headerSize, maxLines = 1, softWrap = false)
        }

        // --- Table Content ---
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Color.Gray)
        ) {
            items(automationSteps) { step ->
                AutomationStepRow(step)
                Divider(color = Color.LightGray)
            }
        }
    }
}

@Composable
fun AutomationStepRow(step: AutomationStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val rowSize = 13.sp

        Text(step.slNo.toString(), modifier = Modifier.weight(0.35f), textAlign = TextAlign.Center, fontSize = rowSize)
        Text("${step.radius}", modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center, fontSize = rowSize)
        Text("${step.angle}", modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center, fontSize = rowSize)
        Text("${step.delaySeconds}", modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center, fontSize = rowSize)

        val actualR = step.movedRadius?.let { String.format(Locale.US, "%.2f", it) } ?: "-"
        Text(actualR, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center, fontSize = rowSize, fontWeight = FontWeight.SemiBold)

        val actualA = step.movedAngle?.let { String.format(Locale.US, "%.2f", it) } ?: "-"
        Text(actualA, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center, fontSize = rowSize, fontWeight = FontWeight.SemiBold)

        val rErr = calculatePercentage(step.radius, step.movedRadius)
        Text(rErr, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center, fontSize = rowSize, color = Color.Red)

        val aErr = calculatePercentage(step.angle, step.movedAngle)
        Text(aErr, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center, fontSize = rowSize, color = Color.Red)
    }
}

fun calculatePercentage(target: Float, actual: Double?): String {
    if (actual == null) return "-"
    if (target == 0f) return "-"
    val diff = target - actual
    val percentage = abs((diff / target) * 100)
    return String.format(Locale.US, "%.2f%%", percentage)
}

fun parseExcelFile(context: Context, uri: Uri): List<AutomationStep> {
    val steps = mutableListOf<AutomationStep>()
    try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        if (inputStream != null) {
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            for (row in sheet) {
                if (row.rowNum == 0) continue
                val slNo = row.getCell(0)?.numericCellValue?.toInt() ?: 0
                val radius = row.getCell(1)?.numericCellValue?.toFloat() ?: 0f
                val angle = row.getCell(2)?.numericCellValue?.toFloat() ?: 0f
                val delay = row.getCell(3)?.numericCellValue?.toLong() ?: 0L
                steps.add(AutomationStep(slNo, radius, angle, delay))
            }
            workbook.close()
            inputStream.close()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return steps
}