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
        Text("Firestore 3: Technician limitation", style = MaterialTheme.typography.titleMedium)
        Text("Filters: siteId=S1, category=temperature, temp>=80, + timestamp range", style = MaterialTheme.typography.bodySmall)
        Text("Expectation: FAILED_PRECONDITION (Missing Composite Index)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            resultText = "Running complex Firestore query..."
            runTechnicianQuery { resultText = it }
        }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
            Text("Run Example 3 (Intentional Fail)")
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

// Function for Example 3 (Technician Composite Index Limitation)
fun runTechnicianQuery(onResult: (String) -> Unit) {
    val db = FirebaseFirestore.getInstance()

    // Time range mapping to our dummy documents
    val startTime = 1708941600000L
    val endTime = 1708943000000L

    db.collection("sensor_readings")
        .whereEqualTo("siteId", "S1")
        .whereEqualTo("category", "temperature")
        .whereGreaterThanOrEqualTo("temp", 80.0) // Inequality 1
        .whereGreaterThanOrEqualTo("timestamp", startTime) // Inequality 2 / Range
        .whereLessThanOrEqualTo("timestamp", endTime)
        .get()
        .addOnSuccessListener { documents ->
            if (documents.isEmpty) {
                onResult("Query succeeded, but no documents matched.")
                return@addOnSuccessListener
            }

            val sb = StringBuilder("Success! Found Anomalies:\n\n")
            for (doc in documents) {
                val sensor = doc.getString("sensorId")
                val temp = doc.getDouble("temp")
                val time = doc.getLong("timestamp")
                sb.append("Sensor: $sensor\nTemp: $temp\nTime: $time\n-------------------\n")
            }
            onResult(sb.toString())
        }
        .addOnFailureListener { exception ->
            // This is exactly what we WANT to happen for the presentation!
            val errorMessage = "QUERY FAILED! (As expected)\n\n" +
                    "Error: ${exception.message}\n\n" +
                    "Look closely at the error message above. Firestore is telling you it needs a Composite Index, and it even provides a direct URL to build it."
            onResult(errorMessage)
        }
}