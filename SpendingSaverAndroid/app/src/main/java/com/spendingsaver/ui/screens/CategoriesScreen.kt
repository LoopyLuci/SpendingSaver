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
    var color by remember { mutableStateOf("") }

    val colorScheme = MaterialTheme.colorScheme
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(colorScheme.surface, colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .padding(16.dp),
    ) {
        SectionHeader("Add category")
        Spacer(Modifier.height(8.dp))
        AppCard(elevation = 2.dp) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Category name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Category, null) },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = color,
                onValueChange = { color = it },
                label = { Text("Color (optional, e.g. #FF5722)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                "Add category",
                enabled = name.isNotBlank(),
                onClick = {
                    viewModel.addCategory(name.trim(), color.trim().ifBlank { null })
                    name = ""
                    color = ""
                },
            )
        }
        Spacer(Modifier.height(16.dp))

        SectionHeader("Categories")
        Spacer(Modifier.height(4.dp))
        if (categories.isEmpty()) {
            EmptyState(
                emoji = "🏷️",
                title = "No categories yet",
                message = "Use the form above to add your first category.",
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(categories) { category ->
                    CategoryRow(category.name, color = category.color, onDelete = { viewModel.deleteCategory(category) })
                }
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
