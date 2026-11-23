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
fun TaxSearchScreen(repository: TaxRepository) {
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateOf<List<SearchableTax>>(emptyList()) }
    LaunchedEffect(query) {
        repository.searchTaxableEntities(query).collect { results.value = it }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search countries / states / towns") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (results.value.isEmpty()) {
            Text(text = "No matches.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn {
                items(results.value) { entity ->
                    ListItem(
                        title = entity.country_name,
                        subtitle = entity.state_name ?: entity.town_name,
                        trailing = entity.town_name ?: entity.state_name
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
