package com.spendingsaver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendingsaver.ai.AiRepository
import kotlinx.coroutines.launch


@Composable
fun ImageGenerationScreen(repository: AiRepository) {
    val aiStatus by repository.aiStatus.collectAsState()
    val aiResponse by repository.aiResponse.collectAsState()
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("A minimalist logo for a budgeting app") }
    var style by remember { mutableStateOf("default") }
    var result by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(text = "Image Generation", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Status: ${aiStatus.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = prompt, onValueChange = { prompt = it },
            label = { Text("Prompt") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = style, onValueChange = { style = it },
            label = { Text("Style") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { scope.launch { result = repository.sendImageGeneration(prompt, style).data } },
                modifier = Modifier.weight(1f)
            ) { Text("Generate") }
        }
        if (result != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = result!!, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
