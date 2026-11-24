package com.chartdesk.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chartdesk.app.BuildConfig
import com.chartdesk.app.data.layouts.SavedLayout
import com.chartdesk.app.feature.account.AccountViewModel
import com.chartdesk.app.feature.layouts.LayoutsViewModel
import com.chartdesk.app.ui.components.CdChip
import com.chartdesk.app.ui.components.ScreenHeader
import com.chartdesk.app.ui.components.SectionLabel
import com.chartdesk.app.ui.theme.ChartDeskType
import com.chartdesk.app.ui.theme.LocalChartDeskPalette

@Composable
fun MoreScreen(
    onOpenChart: () -> Unit,
    layoutsVm: LayoutsViewModel = hiltViewModel(),
    accountVm: AccountViewModel = hiltViewModel(),
) {
    val layouts by layoutsVm.layouts.collectAsStateWithLifecycle()
    val account by accountVm.account.collectAsStateWithLifecycle()
    val ui by accountVm.ui.collectAsStateWithLifecycle()
    val palette = LocalChartDeskPalette.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var showSave by remember { mutableStateOf(false) }
    var showAuth by remember { mutableStateOf(false) }
    var authMode by remember { mutableStateOf("login") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { accountVm.export(context.contentResolver, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { accountVm.import(context.contentResolver, it) } }

    LaunchedEffect(ui.message, ui.error) {
        val text = ui.error ?: ui.message
        if (text != null) {
            snackbar.showSnackbar(text)
            accountVm.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = "More", subtitle = "Account, layouts, backup and attribution")
            LazyColumn(Modifier.fillMaxSize()) {
                item { SectionLabel("Account", Modifier.padding(horizontal = 14.dp)) }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (account.signedIn) account.email ?: "Signed in" else "Not signed in",
                                style = ChartDeskType.price,
                                color = palette.textPrimary,
                            )
                            Text(
                                "Local account · password hashed on device",
                                style = ChartDeskType.label,
                                color = palette.textSecondary,
                            )
                        }
                        if (account.signedIn) {
                            TextButton(onClick = accountVm::signOut) { Text("Sign out", color = palette.textSecondary) }
                        } else {
                            TextButton(onClick = {
                                authMode = "login"
                                showAuth = true
                            }) { Text("Sign in", color = palette.accent) }
                        }
                    }
                }

                item { SectionLabel("Sync & backup", Modifier.padding(horizontal = 14.dp)) }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                        Text(
                            "Export every watchlist, layout, script, drawing, indicator and alert to one JSON bundle. " +
                                "Import it on another device to sync.",
                            style = ChartDeskType.label,
                            color = palette.textSecondary,
                        )
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CdChip("Export backup") { exportLauncher.launch("chartdesk-backup.json") }
                            CdChip("Import backup") { importLauncher.launch(arrayOf("application/json", "text/plain")) }
                        }
                    }
                }

                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Saved layouts", style = ChartDeskType.price, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        TextButton(onClick = { showSave = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                            Text(" Save current", color = palette.accent)
                        }
                    }
                }
                if (layouts.isEmpty()) {
                    item {
                        Text(
                            "No layouts yet. Set up a chart (symbol, timeframe, 2/4 grid) and save it here.",
                            style = ChartDeskType.label,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    items(layouts, key = { it.id }) { layout ->
                        LayoutRow(
                            layout = layout,
                            onApply = {
                                layoutsVm.apply(layout)
                                onOpenChart()
                            },
                            onDelete = { layoutsVm.delete(layout.id) },
                        )
                    }
                }

                item { SectionLabel("About", Modifier.padding(horizontal = 14.dp)) }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                        Text("ChartDesk ${BuildConfig.VERSION_NAME}", style = ChartDeskType.price, color = palette.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Charts by TradingView · lightweight-charts (Apache-2.0). ChartDesk is an independent project, " +
                                "not affiliated with TradingView or LuxAlgo.",
                            style = ChartDeskType.label,
                            color = palette.textSecondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Market data: Binance spot public endpoints. AI assistant: your own key, stored on device.",
                            style = ChartDeskType.label,
                            color = palette.textSecondary,
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }

    if (showSave) {
        var name by remember { mutableStateOf("My layout") }
        AlertDialog(
            onDismissRequest = { showSave = false },
            containerColor = palette.surface,
            title = { Text("Save layout", color = palette.textPrimary) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    layoutsVm.save(name)
                    showSave = false
                }) { Text("Save", color = palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showSave = false }) { Text("Cancel", color = palette.textSecondary) }
            },
        )
    }

    if (showAuth) {
        AuthDialog(
            mode = authMode,
            busy = ui.busy,
            error = ui.error,
            onMode = { authMode = it },
            onConfirm = { email, password ->
                if (authMode == "register") accountVm.register(email, password) else accountVm.login(email, password)
            },
            onDismiss = {
                showAuth = false
                accountVm.clearMessage()
            },
        )
    }
}

@Composable
private fun AuthDialog(
    mode: String,
    busy: Boolean,
    error: String?,
    onMode: (String) -> Unit,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalChartDeskPalette.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text(if (mode == "register") "Create account" else "Sign in", color = palette.textPrimary) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CdChip("Sign in", selected = mode == "login") { onMode("login") }
                    CdChip("Create", selected = mode == "register") { onMode("register") }
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    singleLine = true,
                    label = { Text("Email", style = ChartDeskType.label) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("Password", style = ChartDeskType.label) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (error != null) {
                    Text(error, style = ChartDeskType.label, color = palette.down, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(email, password) }, enabled = !busy) {
                Text(if (mode == "register") "Create" else "Sign in", color = palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) }
        },
    )
}

@Composable
private fun LayoutRow(layout: SavedLayout, onApply: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalChartDeskPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onApply).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(layout.name, style = ChartDeskType.price, color = palette.textPrimary)
            Text(
                "${layout.symbol} · ${layout.timeframe} · ${layout.grid}-chart · ${layout.chartType}",
                style = ChartDeskType.label,
                color = palette.textSecondary,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete layout", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}
