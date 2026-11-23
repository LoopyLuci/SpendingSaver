package com.spendingsaver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendingsaver.ai.AiRepository
import kotlinx.coroutines.launch


@Composable
fun AISettingsScreen(repository: AiRepository) {
    var serviceUrl by remember { mutableStateOf("http://127.0.0.1:8787") }
    val aiStatus by repository.aiStatus.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(aiStatus) {
        if (aiStatus == com.spendingsaver.ai.AiStatus.IDLE) {
            scope.launch { repository.checkAIStatus() }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "AI Service", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = serviceUrl, onValueChange = { serviceUrl = it },
            label = { Text("Service URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { repository.setBaseUrl(serviceUrl) }, modifier = Modifier.weight(1f)) {
                Text("Save URL")
            }
            Button(onClick = { scope.launch { repository.checkAIStatus() } }, modifier = Modifier.weight(1f)) {
                Text("Ping AI")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Status: ${aiStatus.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
