package com.spendingsaver.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** One entry in a [SelectField]: [key] is stored, [label] is shown. */
data class Option(val key: String, val label: String)

/**
 * A pick-from-a-list field (account, category, ...). Tapping opens a menu. When [allowCustom] is on,
 * a final "Other…" entry switches the field to free typing so anything can still be entered by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectField(
    label: String,
    options: List<Option>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
    noneLabel: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    emptyHint: String? = null,
    onEmptyClick: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = options.find { it.key == selectedKey }?.label ?: if (selectedKey == null && noneLabel != null) noneLabel else placeholder,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            isError = isError,
            supportingText = { if (isError && errorText != null) Text(errorText) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (noneLabel != null) {
                DropdownMenuItem(text = { Text(noneLabel) }, onClick = { onSelect(null); expanded = false })
            }
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o.label) }, onClick = { onSelect(o.key); expanded = false })
            }
            if (options.isEmpty() && emptyHint != null) {
                DropdownMenuItem(text = { Text(emptyHint) }, onClick = { expanded = false; onEmptyClick?.invoke() })
            }
        }
    }
}

/**
 * A text field that also offers a dropdown of suggestions. The user can pick a suggestion or type
 * any value by hand; suggestions narrow as they type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComboField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = remember(value, suggestions) {
        val typed = value.trim()
        val filtered = if (typed.isEmpty() || suggestions.any { it.equals(typed, ignoreCase = true) }) suggestions
        else suggestions.filter { it.contains(typed, ignoreCase = true) }
        filtered.distinct().take(40)
    }
    ExposedDropdownMenuBox(expanded = expanded && shown.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text(label) },
            singleLine = singleLine,
            leadingIcon = leadingIcon,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && shown.isNotEmpty()) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            isError = isError,
            supportingText = { if (supportingText != null) Text(supportingText) },
        )
        ExposedDropdownMenu(expanded = expanded && shown.isNotEmpty(), onDismissRequest = { expanded = false }) {
            shown.forEach { s ->
                DropdownMenuItem(text = { Text(s) }, onClick = { onValueChange(s); expanded = false })
            }
        }
    }
}

/** yyyy-MM-dd text field with a calendar picker. Typing a date by hand works too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        placeholder = { Text("yyyy-MM-dd") },
        modifier = modifier,
        isError = isError,
        supportingText = { if (isError && errorText != null) Text(errorText) },
        trailingIcon = {
            IconButton(onClick = { open = true }) { Icon(Icons.Default.DateRange, contentDescription = "Pick date") }
        },
    )
    if (open) {
        val initial = runCatching { LocalDate.parse(value.trim()) }.getOrDefault(LocalDate.now())
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onValueChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** Parses a user-typed money amount, accepting a comma decimal separator and thousands separators. */
fun parseAmount(text: String): Double? {
    val t = text.trim().replace(" ", "").removePrefix("$").removePrefix("€").removePrefix("£")
    if (t.isEmpty()) return null
    val normalized = when {
        t.contains(',') && t.contains('.') -> if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.') else t.replace(",", "")
        t.contains(',') -> if (Regex(",\\d{3}$").containsMatchIn(t)) t.replace(",", "") else t.replace(',', '.')
        else -> t
    }
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** Common choices offered in dropdowns; every one of them can still be replaced by typing. */
object Choices {
    val ACCOUNT_TYPES = listOf("Checking", "Savings", "Credit card", "Cash", "Investment", "Retirement", "Loan", "Mortgage", "Other")
    val CURRENCIES = listOf(
        "USD", "EUR", "GBP", "CAD", "AUD", "NZD", "JPY", "CNY", "INR", "CHF", "SEK", "NOK", "DKK", "PLN", "CZK", "HUF",
        "BRL", "MXN", "ARS", "CLP", "COP", "ZAR", "NGN", "KES", "EGP", "AED", "SAR", "ILS", "TRY", "RUB", "UAH",
        "KRW", "SGD", "HKD", "TWD", "THB", "IDR", "MYR", "PHP", "VND", "PKR", "BDT",
    )
    val DATE_FORMATS = listOf("yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy", "dd.MM.yyyy", "d MMM yyyy", "MMM d, yyyy")
    val COLORS = listOf(
        "Teal" to "#009688", "Blue" to "#2196F3", "Green" to "#4CAF50", "Purple" to "#9C27B0",
        "Orange" to "#FF9800", "Red" to "#F44336", "Pink" to "#E91E63", "Yellow" to "#FFC107",
        "Brown" to "#795548", "Grey" to "#9E9E9E",
    )
    val PERIODS = listOf("weekly", "monthly", "yearly")
    val CATEGORY_IDEAS = listOf(
        "Food", "Groceries", "Dining out", "Housing", "Utilities", "Transportation", "Fuel", "Healthcare", "Insurance",
        "Entertainment", "Subscriptions", "Shopping", "Clothing", "Travel", "Education", "Gifts", "Charity", "Pets",
        "Personal care", "Fees", "Taxes", "Savings", "Income",
    )
}
