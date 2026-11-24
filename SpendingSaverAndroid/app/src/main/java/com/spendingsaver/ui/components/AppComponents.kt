package com.spendingsaver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared, production-grade UI building blocks.
 * Every screen composes these so the app looks and feels consistent.
 */

/** Themed card with optional tonal elevation. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    elevation: androidx.compose.ui.unit.Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) { Column(Modifier.padding(16.dp), content = content) }
}

/** Hero gradient card for the Net Worth / headline figures. */
@Composable
fun HeroCard(
    title: String,
    value: String,
    subtitle: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    onAccent: Color = MaterialTheme.colorScheme.onPrimary,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = accent),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.8f))))
                .padding(22.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = onAccent.copy(alpha = 0.85f), letterSpacing = 1.2.sp)
                Spacer(Modifier.height(8.dp))
                // Long figures shrink to fit on one line instead of being cut off.
                Text(
                    value,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = fitSize(value, 36, 8)),
                    fontWeight = FontWeight.Bold,
                    color = onAccent,
                    maxLines = 1,
                    softWrap = false,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = onAccent.copy(alpha = 0.9f))
                }
            }
        }
    }
}

/** Small stat tile used in metric rows. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: @Composable (() -> Unit)? = null,
) {
    AppCard(modifier, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { icon() }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = fitSize(value, 22, 8)),
                    fontWeight = FontWeight.SemiBold,
                    color = valueColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** Section header with optional trailing action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

/** Friendly empty state with icon + guidance. */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    AppCard(modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (action != null) { Spacer(Modifier.height(12.dp)); action() }
        }
    }
}

/** Primary full-width action button. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
    ) {
        if (icon != null) { icon(); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** A row list item with leading icon, title, subtitle, trailing value. */
@Composable
fun ListItem(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    var confirming by remember { mutableStateOf(false) }
    if (confirming && onDelete != null) {
        ConfirmDeleteDialog(
            what = title,
            onConfirm = { confirming = false; onDelete() },
            onDismiss = { confirming = false },
        )
    }
    Row(
        modifier = modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { leadingIcon() }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = trailingColor, maxLines = 1, softWrap = false)
        }
        if (onDelete != null) {
            IconButton(onClick = { confirming = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete $title", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Destructive actions always ask first; nothing in the app deletes on a single tap. */
@Composable
fun ConfirmDeleteDialog(what: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$what\"?") },
        text = { Text("This can't be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Compact category chip for filters. */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            labelColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

/** Font size that keeps a figure on one line: full [base] size up to [comfortable] characters, then shrinking. */
internal fun fitSize(text: String, base: Int, comfortable: Int): androidx.compose.ui.unit.TextUnit {
    val extra = (text.length - comfortable).coerceAtLeast(0)
    return (base - extra * base / 22.0).coerceAtLeast(base * 0.5).sp
}
