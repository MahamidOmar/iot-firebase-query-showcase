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
                    RtdbExample1Screen()
                }
            }
        }
    }
}

@Composable
fun RtdbExample1Screen() {
    // This variable holds the text shown on the screen.
    // When it changes, the screen automatically updates.
    var resultText by remember { mutableStateOf("Ready to test Example 1") }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("RTDB Example 1: Viewer Lookup", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Path: bySensor/temp1/logs")
        Text("Filter: timestamp between 1708941000000 and 1708946000000")

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            resultText = "Querying database..."
            runViewerQuery { result ->
                resultText = result
            }
        }) {
            Text("Run Query")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Displays the results or errors
        Text(text = resultText)
    }
}

// The actual Firebase Query function
fun runViewerQuery(onResult: (String) -> Unit) {
    val database = FirebaseDatabase.getInstance()
    val ref = database.getReference("bySensor/temp1/logs")

    // The time range that covers our dummy data
    val startTime = 1708941000000.0
    val endTime = 1708946000000.0

    ref.orderByChild("timestamp")
        .startAt(startTime)
        .endAt(endTime)
        .addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    onResult("No data found in this range.")
                    return
                }

                val sb = StringBuilder()
                sb.append("Success! Found ${snapshot.childrenCount} logs:\n\n")

                for (child in snapshot.children) {
                    val category = child.child("category").getValue(String::class.java)
                    val value = child.child("value").getValue(Double::class.java)
                    val timestamp = child.child("timestamp").getValue(Long::class.java)

                    sb.append("Log ID: ${child.key}\n")
                    sb.append("Category: $category\n")
                    sb.append("Value: $value\n")
                    sb.append("Time: $timestamp\n")
                    sb.append("-------------------\n")
                }
                onResult(sb.toString())
            }

            override fun onCancelled(error: DatabaseError) {
                onResult("Query Failed: ${error.message}\nDetails: ${error.details}")
            }
        })
}