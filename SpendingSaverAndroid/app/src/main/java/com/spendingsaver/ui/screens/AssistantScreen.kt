package com.spendingsaver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.spendingsaver.ai.AssistantEngine
import com.spendingsaver.ai.LocalAssistant
import kotlinx.coroutines.launch

private data class Msg(val fromUser: Boolean, val text: String)

/** Chat with the on-device assistant. Answers come from this phone's own data; nothing is sent anywhere. */
@Composable
fun AssistantScreen(assistant: LocalAssistant) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val messages = remember {
        mutableStateListOf(Msg(false, "Hi! I run entirely on this phone and only read your own accounts, transactions and budgets. Ask me something or tap a suggestion."))
    }
    var input by remember { mutableStateOf("") }

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        messages.add(Msg(true, q))
        input = ""
        scope.launch {
            messages.add(Msg(false, assistant.ask(q)))
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).imePadding()) {
        Text(
            "On-device assistant · no internet needed",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (m.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (m.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 320.dp),
                    ) { Text(m.text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistantEngine.SUGGESTIONS.forEach { s ->
                SuggestionChip(onClick = { send(s) }, label = { Text(s) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask about your money…") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { send(input) }),
            )
            IconButton(onClick = { send(input) }, enabled = input.isNotBlank()) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}
