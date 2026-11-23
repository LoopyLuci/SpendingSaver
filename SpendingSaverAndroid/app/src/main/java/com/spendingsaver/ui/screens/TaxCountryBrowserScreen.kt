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
fun TaxCountryBrowserScreen(repository: TaxRepository) {
    val countries = remember { mutableStateOf<List<TaxCountry>>(emptyList()) }
    LaunchedEffect(Unit) {
        repository.observeCountries().collect { countries.value = it }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(text = "Tax Countries", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        if (countries.value.isEmpty()) {
            Text(text = "No tax countries loaded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn {
                items(countries.value) { country ->
                    ListItem(
                        title = country.country_name,
                        subtitle = country.country_code,
                        trailing = if (country.is_oecd) "OECD" else ""
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
