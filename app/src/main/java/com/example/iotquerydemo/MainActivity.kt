package com.example.iotquerydemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RtdbExamplesScreen()
                }
            }
        }
    }
}

@Composable
fun RtdbExamplesScreen() {
    var resultText by remember { mutableStateOf("Ready to test queries") }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // --- EXAMPLE 1 ---
        Text("RTDB Example 1: Viewer Lookup", style = MaterialTheme.typography.titleMedium)
        Text("Path: bySensor/temp1/logs", style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            resultText = "Querying Viewer..."
            runViewerQuery { result -> resultText = result }
        }) {
            Text("Run Example 1")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(24.dp))

        // --- EXAMPLE 2 ---
        Text("RTDB Example 2: Admin Timeline", style = MaterialTheme.typography.titleMedium)
        Text("Path: byTime/temperature", style = MaterialTheme.typography.bodySmall)
        Text("Why: Avoids downloading the entire bySensor tree", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            resultText = "Querying Admin Timeline..."
            runAdminTimelineQuery { result -> resultText = result }
        }) {
            Text("Run Example 2 (The Solution)")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- RESULTS BOX ---
        Text("Results:", style = MaterialTheme.typography.titleMedium)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(
                text = resultText,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

// Function for Example 1
fun runViewerQuery(onResult: (String) -> Unit) {
    val database = FirebaseDatabase.getInstance()
    val ref = database.getReference("bySensor/temp1/logs")

    ref.orderByChild("timestamp")
        .startAt(1708941000000.0)
        .endAt(1708946000000.0)
        .addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return onResult("No data found.")
                val sb = StringBuilder("Viewer Logs (temp1):\n\n")
                for (child in snapshot.children) {
                    sb.append("Log: ${child.key} | Val: ${child.child("value").value}\n")
                }
                onResult(sb.toString())
            }
            override fun onCancelled(error: DatabaseError) {
                onResult("Error: ${error.message}")
            }
        })
}

// Function for Example 2 (Admin Timeline Solution)
fun runAdminTimelineQuery(onResult: (String) -> Unit) {
    val database = FirebaseDatabase.getInstance()
    val ref = database.getReference("byTime/temperature")

    // In the byTime tree, the timestamp IS the key, so we use string representations
    val startTimeStr = "1708941000000"
    val endTimeStr = "1708946000000"

    ref.orderByKey()
        .startAt(startTimeStr)
        .endAt(endTimeStr)
        .addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    onResult("No data found in this range.")
                    return
                }

                val sb = StringBuilder()
                sb.append("Success! Global Timeline Logs:\n\n")

                for (timeNode in snapshot.children) {
                    val timeKey = timeNode.key
                    sb.append("Time: $timeKey\n")

                    // Loop through the log IDs under this specific timestamp
                    for (logNode in timeNode.children) {
                        val sensorId = logNode.child("sensorId").getValue(String::class.java)
                        val value = logNode.child("value").getValue(Double::class.java)
                        sb.append("  -> Sensor: $sensorId | Val: $value\n")
                    }
                    sb.append("-------------------\n")
                }
                onResult(sb.toString())
            }

            override fun onCancelled(error: DatabaseError) {
                onResult("Query Failed: ${error.message}")
            }
        })
}