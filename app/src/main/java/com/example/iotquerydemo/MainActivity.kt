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
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    QueryShowcaseScreen()
                }
            }
        }
    }
}

@Composable
fun QueryShowcaseScreen() {
    var resultText by remember { mutableStateOf("Ready to test queries") }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // --- EXAMPLE 1 ---
        Text("RTDB 1: Viewer Lookup", style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            resultText = "Querying Viewer..."
            runViewerQuery { resultText = it }
        }) { Text("Run Example 1") }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // --- EXAMPLE 2 ---
        Text("RTDB 2: Admin Timeline", style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            resultText = "Querying Admin Timeline..."
            runAdminTimelineQuery { resultText = it }
        }) { Text("Run Example 2") }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // --- EXAMPLE 3 ---
        Text("Firestore 3: Technician Query", style = MaterialTheme.typography.titleMedium)
        Text("Solution: Composite Index created", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            resultText = "Running complex Firestore query..."
            runTechnicianQuery { resultText = it }
        }) { Text("Run Example 3") }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // --- EXAMPLE 4 ---
        Text("Firestore 4: Admin Rollups", style = MaterialTheme.typography.titleMedium)
        Text("Path: hourly_stats", style = MaterialTheme.typography.bodySmall)
        Text("Filters: hourKey between 2026022814 and 2026022816", style = MaterialTheme.typography.bodySmall)
        Text("Why: Bypasses the lack of a GROUP BY function", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            resultText = "Fetching Dashboard Chart Data..."
            runAdminRollupQuery { resultText = it }
        }) {
            Text("Run Example 4 (The Workaround)")
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

// --- QUERY FUNCTIONS ---

// Function for Example 1 (RTDB Viewer Lookup)
fun runViewerQuery(onResult: (String) -> Unit) {
    val db = FirebaseDatabase.getInstance().getReference("bySensor/temp1/logs")
    db.orderByChild("timestamp").startAt(1708941000000.0).endAt(1708946000000.0)
        .addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return onResult("No data found.")
                val sb = StringBuilder("Viewer Logs:\n\n")
                snapshot.children.forEach {
                    sb.append("Log: ${it.key} | Val: ${it.child("value").value}\n")
                }
                onResult(sb.toString())
            }
            override fun onCancelled(error: DatabaseError) { onResult("Error: ${error.message}") }
        })
}

// Function for Example 2 (RTDB Admin Timeline)
fun runAdminTimelineQuery(onResult: (String) -> Unit) {
    val db = FirebaseDatabase.getInstance().getReference("byTime/temperature")
    db.orderByKey().startAt("1708941000000").endAt("1708946000000")
        .addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return onResult("No data found.")
                val sb = StringBuilder("Global Timeline:\n\n")
                snapshot.children.forEach { timeNode ->
                    sb.append("Time: ${timeNode.key}\n")
                    timeNode.children.forEach { logNode ->
                        sb.append("  -> Sensor: ${logNode.child("sensorId").value} | Val: ${logNode.child("value").value}\n")
                    }
                }
                onResult(sb.toString())
            }
            override fun onCancelled(error: DatabaseError) { onResult("Error: ${error.message}") }
        })
}

// Function for Example 3 (Firestore Technician Query & Composite Index)
fun runTechnicianQuery(onResult: (String) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val startTime = 1708941600000L
    val endTime = 1708943000000L

    db.collection("sensor_readings")
        .whereEqualTo("siteId", "S1")
        .whereEqualTo("category", "temperature")
        .whereGreaterThanOrEqualTo("temp", 80.0)
        .whereGreaterThanOrEqualTo("timestamp", startTime)
        .whereLessThanOrEqualTo("timestamp", endTime)
        .get()
        .addOnSuccessListener { documents ->
            if (documents.isEmpty) return@addOnSuccessListener onResult("Query succeeded, but no documents matched.")
            val sb = StringBuilder("Success! Found Anomalies:\n\n")
            for (doc in documents) {
                sb.append("Sensor: ${doc.getString("sensorId")}\nTemp: ${doc.getDouble("temp")}\nTime: ${doc.getLong("timestamp")}\n-------------------\n")
            }
            onResult(sb.toString())
        }
        .addOnFailureListener { exception -> onResult("Error: ${exception.message}") }
}

// Function for Example 4 (Admin Dashboard Rollups)
fun runAdminRollupQuery(onResult: (String) -> Unit) {
    val db = FirebaseFirestore.getInstance()

    // We only use the hourKey for the range query to avoid needing another complex index
    db.collection("hourly_stats")
        .whereGreaterThanOrEqualTo("hourKey", "2026022814")
        .whereLessThanOrEqualTo("hourKey", "2026022816")
        .orderBy("hourKey")
        .get()
        .addOnSuccessListener { documents ->
            if (documents.isEmpty) {
                onResult("No hourly stats found.")
                return@addOnSuccessListener
            }

            val sb = StringBuilder("Success! Admin Dashboard Chart Data:\n\n")
            for (doc in documents) {
                val hour = doc.getString("hourKey")
                val avg = doc.getDouble("avg")
                val count = doc.getDouble("count")

                sb.append("Hour Block: $hour\n")
                sb.append("  -> Avg Temp: $avg\n")
                sb.append("  -> Total Readings: ${count?.toInt()}\n")
                sb.append("-------------------\n")
            }
            onResult(sb.toString())
        }
        .addOnFailureListener { exception ->
            onResult("Query Failed: ${exception.message}")
        }
}