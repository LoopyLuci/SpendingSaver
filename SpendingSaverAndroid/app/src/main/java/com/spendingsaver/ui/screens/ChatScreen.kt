package com.spendingsaver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendingsaver.ai.AiRepository
import kotlinx.coroutines.launch


@Composable
fun ChatScreen(repository: AiRepository) {
    val aiStatus by repository.aiStatus.collectAsState()
    val aiResponse by repository.aiResponse.collectAsState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(text = "AI Chat", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Status: ${aiStatus.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        if (aiResponse?.data != null) {
            Text(text = "Response: ${aiResponse?.data}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("Ask the AI...") },
                modifier = Modifier.weight(1f), singleLine = true
            )
            Button(
                onClick = { scope.launch { repository.sendChatMessage(input) }; input = "" },
                modifier = Modifier.padding(start = 4.dp)
            ) { Text("Send") }
        }
    }
}
