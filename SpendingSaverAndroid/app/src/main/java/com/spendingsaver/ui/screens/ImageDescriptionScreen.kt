package com.spendingsaver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendingsaver.ai.AiRepository
import kotlinx.coroutines.launch


@Composable
fun ImageDescriptionScreen(repository: AiRepository) {
    val aiStatus by repository.aiStatus.collectAsState()
    val aiResponse by repository.aiResponse.collectAsState()
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("Describe this image in detail.") }
    var imageRef by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(text = "Image Description", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Status: ${aiStatus.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = imageRef, onValueChange = { imageRef = it },
            label = { Text("Image reference (URI/path)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = prompt, onValueChange = { prompt = it },
            label = { Text("Prompt") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { scope.launch { result = repository.sendVisionRequest(prompt, listOf(imageRef)).data } },
            enabled = imageRef.isNotEmpty()
        ) { Text("Describe") }
        if (result != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = result!!, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
