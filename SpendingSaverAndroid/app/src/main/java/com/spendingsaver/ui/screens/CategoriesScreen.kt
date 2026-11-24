package com.spendingsaver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.spendingsaver.ui.components.*
import com.spendingsaver.ui.viewmodel.MainViewModel

@Composable
fun CategoriesScreen(navController: NavHostController, viewModel: MainViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsState()

    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var colorName by remember { mutableStateOf("") }

    val colorScheme = MaterialTheme.colorScheme
    val backgroundGradient = Brush.verticalGradient(colors = listOf(colorScheme.surface, colorScheme.surfaceVariant.copy(alpha = 0.3f)))
    // Typing "Teal" or "#009688" both work; the dropdown lists the named colours.
    fun resolveColor(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        Choices.COLORS.firstOrNull { it.first.equals(t, ignoreCase = true) }?.let { return it.second }
        val hex = if (t.startsWith("#")) t else "#$t"
        return if (Regex("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})").matches(hex)) hex else "invalid"
    }
    val colorInvalid = resolveColor(colorName) == "invalid"

    LazyColumn(
        Modifier.fillMaxSize().background(backgroundGradient).imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Add category") }
        item {
            AppCard(elevation = 2.dp) {
                ComboField(
                    label = "Category name (pick an idea or type your own)",
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    suggestions = Choices.CATEGORY_IDEAS.filter { idea -> categories.none { it.name.equals(idea, ignoreCase = true) } },
                    leadingIcon = { Icon(Icons.Default.Category, null) },
                    isError = nameError != null,
                    supportingText = nameError,
                )
                Spacer(Modifier.height(8.dp))
                ComboField(
                    label = "Colour (pick one or type #RRGGBB)",
                    value = colorName,
                    onValueChange = { colorName = it },
                    suggestions = Choices.COLORS.map { it.first },
                    isError = colorInvalid,
                    supportingText = if (colorInvalid) "Use a name from the list or a hex code like #FF5722" else null,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    "Add category",
                    onClick = {
                        val n = name.trim()
                        nameError = when {
                            n.isEmpty() -> "Give the category a name"
                            categories.any { it.name.equals(n, ignoreCase = true) } -> "That category already exists"
                            else -> null
                        }
                        if (nameError != null || colorInvalid) return@PrimaryButton
                        viewModel.addCategory(n, resolveColor(colorName))
                        name = ""; colorName = ""
                    },
                )
            }
        }
        item { SectionHeader("Categories") }
        if (categories.isEmpty()) {
            item { EmptyState(emoji = "🏷️", title = "No categories yet", message = "Add one above, or pick from the ideas list.") }
        } else {
            items(categories, key = { it.id }) { category ->
                CategoryRow(category.name, color = category.color, onDelete = { viewModel.deleteCategory(category) })
            }
        }
    }
}

@Composable
fun CategoryRow(name: String, color: String?, onDelete: () -> Unit) {
    val swatch = try {
        if (color.isNullOrBlank()) null else androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(color))
    } catch (_: IllegalArgumentException) {
        null
    } ?: MaterialTheme.colorScheme.primaryContainer
    AppCard(Modifier.fillMaxWidth(), elevation = 1.dp) {
        ListItem(
            title = name,
            onDelete = onDelete,
            leadingIcon = { Box(Modifier.size(24.dp).clip(CircleShape).background(swatch)) },
        )
    }
}
