package com.spendingsaver

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spendingsaver.ai.LocalAssistant
import com.spendingsaver.repository.SettingsRepository
import com.spendingsaver.ui.screens.*
import com.spendingsaver.ui.theme.SpendingSaverTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var assistant: LocalAssistant
    @Inject lateinit var updates: com.spendingsaver.update.UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppRoot(settingsRepo, assistant, updates)
        }
    }
}

@Composable
fun AppRoot(settingsRepo: SettingsRepository, assistant: LocalAssistant, updates: com.spendingsaver.update.UpdateManager) {
    val settings by settingsRepo.settings.collectAsState()
    SpendingSaverTheme(
        themeMode = settings.themeMode,
        accentColor = settings.accentColor,
        textScale = settings.textScale,
    ) {
        AppNavHost(settingsRepo, assistant)
        // Automatic modes only: look for an update once when the app opens. Manual mode does nothing here.
        LaunchedEffect(Unit) { updates.autoRun() }
        val prompt by updates.prompt.collectAsState()
        prompt?.let { InstallDialog(updates, it.release, it.file) { updates.dismissPrompt() } }
    }
}

data class NavItem(val route: String, val title: String, val icon: ImageVector)

val NAV_ITEMS = listOf(
    NavItem("dashboard", "Dashboard", Icons.Default.Home),
    NavItem("transactions", "Transactions", Icons.Default.ReceiptLong),
    NavItem("accounts", "Accounts", Icons.Default.AccountBalance),
    NavItem("budgets", "Budgets", Icons.Default.Savings),
    NavItem("categories", "Categories", Icons.Default.Category),
    NavItem("networth", "Net Worth", Icons.Default.TrendingUp),
    NavItem("import", "Import / Export", Icons.Default.FileUpload),
    NavItem("tax", "Tax", Icons.Default.Business),
    NavItem("assistant", "Assistant", Icons.Default.SmartToy),
    NavItem("pair", "Pair with desktop", Icons.Default.QrCodeScanner),
    NavItem("updates", "App updates", Icons.Default.SystemUpdate),
    NavItem("settings", "Settings", Icons.Default.Settings),
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    settingsRepo: SettingsRepository,
    assistant: LocalAssistant,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentItem = NAV_ITEMS.find { it.route == currentRoute }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
              // Scrollable: on a phone held sideways the list is taller than the screen.
              Column(Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("SpendingSaver", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Personal finance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                NAV_ITEMS.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.title) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo("dashboard") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
              }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentItem?.title ?: "SpendingSaver", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        // Always a menu button so the user can switch screens
                        // from anywhere (instead of drilling back each time).
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        // No Settings button on the Settings screen itself.
                        if (currentRoute != "settings") {
                            IconButton(onClick = { navController.navigate("settings") }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                NavHost(navController, startDestination = "dashboard") {
                    composable("dashboard") { DashboardScreen(navController) }
                    composable("transactions") { TransactionsScreen(navController) }
                    composable("budgets") { BudgetsScreen(navController) }
                    composable("accounts") { AccountsScreen(navController) }
                    composable("categories") { CategoriesScreen(navController) }
                    composable("networth") { NetWorthScreen(navController) }
                    composable("import") { ImportExportScreen(navController) }
                    composable("settings") { SettingsScreen(navController, settingsRepo) }
                    composable("assistant") { AssistantScreen(assistant) }
                    composable("pair") { PairScreen() }
                    composable("tax") { TaxScreen() }
                    composable("updates") { UpdatesScreen() }
                }
            }
        }
    }
}
