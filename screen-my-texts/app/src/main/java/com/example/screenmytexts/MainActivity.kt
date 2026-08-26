package com.example.screenmytexts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.screenmytexts.ui.theme.ScreenMyTextsTheme
import org.json.JSONArray
import org.json.JSONObject

data class Filter(val pattern: String, val isRegex: Boolean) {
    /** True if [text] matches this rule. Invalid regex never matches. */
    fun matches(text: String): Boolean = if (isRegex) {
        try { Regex(pattern).containsMatchIn(text) } catch (e: Exception) { false }
    } else {
        text.contains(pattern, ignoreCase = true)
    }
}
data class HistoryItem(val message: String, val timestamp: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScreenMyTextsTheme {
                FilterScreen()
            }
        }
    }
}

// Storage keys for the two rule lists. The block list screens (dismisses) matching
// messages; the allow list overrides screening so trusted messages (OTPs, banks,
// known contacts) are never hidden — mirroring "Allowed Senders" in peer apps.
const val BLOCK_LIST_KEY = "filters_list"
const val ALLOW_LIST_KEY = "allowlist_list"

fun saveFilters(context: Context, filters: List<Filter>, key: String = BLOCK_LIST_KEY) {
    val prefs = context.getSharedPreferences("filters_prefs", Context.MODE_PRIVATE)
    val array = JSONArray()
    filters.forEach {
        val obj = JSONObject()
        obj.put("pattern", it.pattern)
        obj.put("isRegex", it.isRegex)
        array.put(obj)
    }
    prefs.edit().putString(key, array.toString()).apply()
}

fun loadFilters(context: Context, key: String = BLOCK_LIST_KEY): List<Filter> {
    val prefs = context.getSharedPreferences("filters_prefs", Context.MODE_PRIVATE)
    val json = prefs.getString(key, null) ?: return emptyList()
    val array = JSONArray(json)
    val list = mutableListOf<Filter>()
    for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        list.add(Filter(obj.getString("pattern"), obj.getBoolean("isRegex")))
    }
    return list
}

fun loadHistory(context: Context): List<HistoryItem> {
    val prefs = context.getSharedPreferences("filters_prefs", Context.MODE_PRIVATE)
    val json = prefs.getString("history_list", null) ?: return emptyList()
    val array = JSONArray(json)
    val list = mutableListOf<HistoryItem>()
    for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        list.add(HistoryItem(obj.getString("message"), obj.getLong("timestamp")))
    }
    return list.sortedByDescending { it.timestamp }
}

fun clearHistory(context: Context) {
    context.getSharedPreferences("filters_prefs", Context.MODE_PRIVATE)
        .edit().remove("history_list").apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen() {
    val context = LocalContext.current
    var filters by remember { mutableStateOf(loadFilters(context, BLOCK_LIST_KEY)) }
    var allowFilters by remember { mutableStateOf(loadFilters(context, ALLOW_LIST_KEY)) }
    var history by remember { mutableStateOf(loadHistory(context)) }
    var newFilterText by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    // 0 = Block rules, 1 = Allow rules, 2 = History
    var selectedTab by remember { mutableIntStateOf(0) }
    // Overflow (three-dot) menu + About dialog state
    var menuExpanded by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    
    val isRegexValid = remember(newFilterText, isRegex) {
        if (isRegex && newFilterText.isNotBlank()) {
            try { Regex(newFilterText); true } catch (e: Exception) { false }
        } else true
    }

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isNotificationListenerEnabled by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasSmsPermission = it }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                history = loadHistory(context)
                hasSmsPermission =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                isNotificationListenerEnabled =
                    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("About") },
                            onClick = { menuExpanded = false; showAbout = true }
                        )
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize()) {
            if (showAbout) {
                val pkgInfo = remember {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                val versionName = pkgInfo.versionName ?: "—"
                val updatedDate = remember(pkgInfo) {
                    java.text.DateFormat.getDateInstance().format(java.util.Date(pkgInfo.lastUpdateTime))
                }
                AlertDialog(
                    onDismissRequest = { showAbout = false },
                    confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } },
                    title = { Text("About ${stringResource(R.string.app_name)}") },
                    text = {
                        Column {
                            Text("Version $versionName")
                            Text("Updated $updatedDate")
                        }
                    }
                )
            }
            if (!hasSmsPermission) {
                ErrorMessage("SMS permission needed to detect spam.", "Grant Permission") { smsLauncher.launch(Manifest.permission.RECEIVE_SMS) }
            } else if (!isNotificationListenerEnabled) {
                ErrorMessage("Notification Access needed to hide alerts.", "Enable Access") {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            } else {
                val isAllowTab = selectedTab == 1
                // Input Section — hidden on the History tab. On the Block/Allow tabs
                // the Add button targets whichever rule list is currently shown.
                if (selectedTab != 2) {
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            TextField(
                                value = newFilterText,
                                onValueChange = { newFilterText = it },
                                modifier = Modifier.fillMaxWidth(),
                                isError = !isRegexValid,
                                label = { Text(if (isRegex) "Regex Pattern" else "Text Filter") },
                                supportingText = { if (!isRegexValid) Text("Invalid Regular Expression", color = MaterialTheme.colorScheme.error) }
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = isRegex, onCheckedChange = { isRegex = it })
                                Text("Use Regex")
                                Spacer(modifier = Modifier.weight(1f))
                                if (editingIndex != null) {
                                    TextButton(onClick = { newFilterText = ""; isRegex = false; editingIndex = null }) { Text("Cancel") }
                                }
                                Button(
                                    onClick = {
                                        if (newFilterText.isNotBlank() && isRegexValid) {
                                            val target = if (isAllowTab) allowFilters else filters
                                            val newList = target.toMutableList()
                                            if (editingIndex != null) newList[editingIndex!!] = Filter(newFilterText, isRegex)
                                            else newList.add(Filter(newFilterText, isRegex))
                                            val key = if (isAllowTab) ALLOW_LIST_KEY else BLOCK_LIST_KEY
                                            if (isAllowTab) allowFilters = newList else filters = newList
                                            saveFilters(context, newList, key)
                                            newFilterText = ""; isRegex = false; editingIndex = null
                                        }
                                    },
                                    enabled = newFilterText.isNotBlank() && isRegexValid
                                ) {
                                    Text(if (editingIndex != null) "Save" else if (isAllowTab) "Add Allow" else "Add Block")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Tabs/Sections
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0; newFilterText = ""; isRegex = false; editingIndex = null }, text = { Text("Block") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; newFilterText = ""; isRegex = false; editingIndex = null }, text = { Text("Allow") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("History") })
                }

                when (selectedTab) {
                    0, 1 -> {
                        val list = if (isAllowTab) allowFilters else filters
                        val key = if (isAllowTab) ALLOW_LIST_KEY else BLOCK_LIST_KEY
                        if (list.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    if (isAllowTab) "No allow rules. Matching messages are always kept."
                                    else "No block rules yet. Add a keyword or regex above.",
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                itemsIndexed(list) { index, filter ->
                                    FilterItem(filter, onDelete = {
                                        val newList = list.toMutableList().apply { removeAt(index) }
                                        if (isAllowTab) allowFilters = newList else filters = newList
                                        saveFilters(context, newList, key)
                                    }, onEdit = {
                                        newFilterText = filter.pattern; isRegex = filter.isRegex; editingIndex = index
                                    })
                                }
                            }
                        }
                    }
                    else -> {
                        Column(modifier = Modifier.weight(1f)) {
                            if (history.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No history yet.", color = Color.Gray)
                                }
                            } else {
                                Button(onClick = { clearHistory(context); history = emptyList() }, modifier = Modifier.align(Alignment.End).padding(8.dp)) {
                                    Text("Clear All")
                                }
                                LazyColumn {
                                    itemsIndexed(history) { _, item ->
                                        HistoryCard(item)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryCard(item: HistoryItem) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.message, style = MaterialTheme.typography.bodyMedium)
            Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.timestamp)), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun ErrorMessage(message: String, buttonText: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onClick) { Text(buttonText) }
    }
}

@Composable
fun FilterItem(filter: Filter, onDelete: () -> Unit, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(filter.pattern)
                Text(if (filter.isRegex) "Regex" else "Literal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
}
