package com.spendingsaver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.spendingsaver.ui.components.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendingsaver.tax.TaxCountry
import com.spendingsaver.tax.TaxRepository
import com.spendingsaver.tax.TaxState
import com.spendingsaver.tax.SearchableTax
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


@Composable
fun TaxStatesScreen(repository: TaxRepository, countryCode: String) {
    val states = remember { mutableStateOf<List<TaxState>>(emptyList()) }
    LaunchedEffect(countryCode) {
        repository.observeStatesByCountry(countryCode).collect { states.value = it }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(text = "Tax States", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        if (states.value.isEmpty()) {
            Text(text = "No tax states for this country.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn {
                items(states.value) { state ->
                    ListItem(
                        title = state.state_name,
                        subtitle = state.state_code,
                        trailing = if (state.is_state) "state" else "province"
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
