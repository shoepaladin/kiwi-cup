package com.example.wallpaperrotator

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var selectionToolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WallpaperAdapter
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var intervalInput: TextInputEditText
    private lateinit var rotationModeSpinner: Spinner
    private lateinit var changeOnUnlockCheck: MaterialCheckBox
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var emptyState: View
    private lateinit var progressContainer: View
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var progressText: TextView

    private lateinit var settingsCard: MaterialCardView
    private lateinit var settingsTitle: TextView
    private lateinit var intervalLabel: TextView
    private lateinit var intervalLayout: TextInputLayout
    private lateinit var modeLabel: TextView
    private lateinit var emptyStateText1: TextView
    private lateinit var emptyStateText2: TextView

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {}
            openCropActivity(it)
        }
    }

    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> uri?.let { viewModel.addFolderImages(it) } }

    private val cropActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> if (result.resultCode == RESULT_OK) viewModel.loadConfigs() }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.values.all { it }) {
            Snackbar.make(findViewById(android.R.id.content), "Permissions required", Snackbar.LENGTH_LONG).show()
        }
    }

    private val unlockReceiver = WallpaperReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupViews()
        setupRecyclerView()
        setupSpinners()
        setupObservers()
        setupButtons()
        requestPermissions()
        loadAndApplyColors()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val sharedPrefs = getSharedPreferences("colors", Context.MODE_PRIVATE)
        val tabBgColorStr = sharedPrefs.getString("tab_background", "#FF6200EE")!!
        val popupStyle = when (tabBgColorStr) {
            "#2196F3" -> R.style.PopupMenu_Blue
            "#9E9E9E" -> R.style.PopupMenu_Grey
            "#000000" -> R.style.PopupMenu_Black
            "#FF9800" -> R.style.PopupMenu_Orange
            else -> R.style.PopupMenu_Purple
        }
        val context = ContextThemeWrapper(this, popupStyle)
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val sharedPrefs = getSharedPreferences("colors", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()

        when (item.itemId) {
            R.id.font_white -> editor.putString("font_color", "#FFFFFF").apply()
            R.id.font_black -> editor.putString("font_color", "#000000").apply()
            R.id.font_red -> editor.putString("font_color", "#FF0000").apply()
            R.id.font_blue -> editor.putString("font_color", "#0000FF").apply()
            R.id.font_green -> editor.putString("font_color", "#00FF00").apply()

            R.id.tab_purple -> editor.putString("tab_background", "#FF6200EE").apply()
            R.id.tab_blue -> editor.putString("tab_background", "#2196F3").apply()
            R.id.tab_grey -> editor.putString("tab_background", "#9E9E9E").apply()
            R.id.tab_black -> editor.putString("tab_background", "#000000").apply()
            R.id.tab_orange -> editor.putString("tab_background", "#FF9800").apply()

            R.id.bg_black -> editor.putString("background_color", "#000000").apply()
            R.id.bg_white -> editor.putString("background_color", "#FFFFFF").apply()
            R.id.bg_dark_grey -> editor.putString("background_color", "#424242").apply()
            R.id.bg_light_grey -> editor.putString("background_color", "#E0E0E0").apply()
            R.id.bg_blue -> editor.putString("background_color", "#1565C0").apply()

            R.id.action_about -> {
                showAboutDialog()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
        loadAndApplyColors()
        invalidateOptionsMenu()
        return true
    }

    private fun showAboutDialog() {
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        AlertDialog.Builder(this)
            .setTitle("About")
            .setMessage("Version $versionName\n${getString(R.string.version_date)}")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun loadAndApplyColors() {
        val sharedPrefs = getSharedPreferences("colors", Context.MODE_PRIVATE)
        val fontColorStr = sharedPrefs.getString("font_color", "#FFFFFF")!!
        val tabBgColorStr = sharedPrefs.getString("tab_background", "#FF6200EE")!!
        val bgColorStr = sharedPrefs.getString("background_color", "#000000")!!

        val fontColor = Color.parseColor(fontColorStr)
        val tabBgColor = Color.parseColor(tabBgColorStr)
        val bgColor = Color.parseColor(bgColorStr)

        coordinatorLayout.setBackgroundColor(bgColor)

        appBar.setBackgroundColor(tabBgColor)
        toolbar.setBackgroundColor(tabBgColor)
        selectionToolbar.setBackgroundColor(tabBgColor)
        settingsCard.setCardBackgroundColor(tabBgColor)
        adapter.setSelectionColor(tabBgColor)

        toolbar.setTitleTextColor(fontColor)
        selectionToolbar.setTitleTextColor(fontColor)

        settingsTitle.setTextColor(fontColor)
        intervalLabel.setTextColor(fontColor)
        modeLabel.setTextColor(fontColor)
        changeOnUnlockCheck.setTextColor(fontColor)
        changeOnUnlockCheck.buttonTintList = android.content.res.ColorStateList.valueOf(fontColor)

        intervalInput.setTextColor(fontColor)
        intervalInput.setHintTextColor(fontColor)
        intervalLayout.hintTextColor = android.content.res.ColorStateList.valueOf(fontColor)
        intervalLayout.defaultHintTextColor = android.content.res.ColorStateList.valueOf(fontColor)
        intervalLayout.setBoxStrokeColor(fontColor)

        emptyStateText1.setTextColor(fontColor)
        emptyStateText2.setTextColor(fontColor)

        startButton.setTextColor(tabBgColor)
        startButton.backgroundTintList = android.content.res.ColorStateList.valueOf(fontColor)

        stopButton.setTextColor(fontColor)
        stopButton.strokeColor = android.content.res.ColorStateList.valueOf(fontColor)

        val popupStyle = when (tabBgColorStr) {
            "#2196F3" -> R.style.PopupMenu_Blue
            "#9E9E9E" -> R.style.PopupMenu_Grey
            "#000000" -> R.style.PopupMenu_Black
            "#FF9800" -> R.style.PopupMenu_Orange
            else -> R.style.PopupMenu_Purple
        }
        toolbar.popupTheme = popupStyle
        selectionToolbar.popupTheme = popupStyle
    }

    override fun onResume() {
        super.onResume()
        checkWallpaperStatus()
    }

    private fun checkWallpaperStatus() {
        val wm = WallpaperManager.getInstance(this)
        if (wm.wallpaperInfo != null) {
            AlertDialog.Builder(this)
                .setTitle("Live Wallpaper Active")
                .setMessage("Nova Launcher (or your system) is using a Live Wallpaper. This app only works with static wallpapers. Switch to a static wallpaper to enable rotation.")
                .setPositiveButton("Choose Wallpaper") { _, _ ->
                    startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
                }
                .setNegativeButton("Ignore", null)
                .show()
        }
    }

    private fun setupViews() {
        coordinatorLayout = findViewById(R.id.coordinator_layout)
        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        selectionToolbar = findViewById(R.id.selectionToolbar)
        recyclerView = findViewById(R.id.recyclerView)
        fab = findViewById(R.id.fab)
        intervalInput = findViewById(R.id.intervalInput)
        rotationModeSpinner = findViewById(R.id.rotationModeSpinner)
        changeOnUnlockCheck = findViewById(R.id.changeOnUnlockCheck)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        emptyState = findViewById(R.id.emptyState)
        progressContainer = findViewById(R.id.progressContainer)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)

        settingsCard = findViewById(R.id.settings_card)
        settingsTitle = findViewById(R.id.settingsTitle)
        intervalLabel = findViewById(R.id.intervalLabel)
        intervalLayout = findViewById(R.id.intervalLayout)
        modeLabel = findViewById(R.id.modeLabel)
        emptyStateText1 = findViewById(R.id.emptyStateText1)
        emptyStateText2 = findViewById(R.id.emptyStateText2)

        setSupportActionBar(toolbar)
        selectionToolbar.bringToFront()
        selectionToolbar.setNavigationOnClickListener { viewModel.clearSelection() }
        intervalInput.setText(viewModel.getRotationInterval().toString())

        selectionToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_select_all -> {
                    viewModel.selectAll()
                    true
                }
                R.id.action_delete -> {
                    viewModel.deleteSelectedConfigs()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = WallpaperAdapter(
            onItemClick = { openEditBottomSheet(it) },
            onItemLongClick = { viewModel.toggleSelection(it.id); true },
            isSelected = { viewModel.isSelected(it) }
        )
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter
    }

    private fun setupSpinners() {
        val modes = arrayOf("Sequential", "Random")
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        rotationModeSpinner.adapter = modeAdapter
        rotationModeSpinner.setSelection(if (viewModel.getRotationMode() == "random") 1 else 0)
        rotationModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                viewModel.saveRotationMode(if (pos == 1) "random" else "sequential")
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun setupObservers() {
        viewModel.configs.observe(this) { 
            adapter.submitList(it)
            emptyState.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.isLoading.observe(this) { progressContainer.visibility = if (it) View.VISIBLE else View.GONE }
        viewModel.loadingProgress.observe(this) { (c, t) ->
            if (t > 0) {
                progressBar.isIndeterminate = false
                progressBar.max = t
                progressBar.progress = c
                progressText.text = "Adding: $c / $t"
            } else {
                progressBar.isIndeterminate = true
                progressText.text = "Loading..."
            }
        }
        viewModel.selectionMode.observe(this) { active ->
            toolbar.visibility = if (active) View.GONE else View.VISIBLE
            selectionToolbar.visibility = if (active) View.VISIBLE else View.GONE
            if (active) {
                selectionToolbar.title = "${viewModel.getSelectedCount()} selected"
                selectionToolbar.menu.clear()
                try { selectionToolbar.inflateMenu(R.menu.selection_menu) } catch (e: Exception) {}
                fab.hide()
            } else fab.show()
            adapter.setSelectionMode(active)
        }
    }

    private fun setupButtons() {
        fab.setOnClickListener {
            val items = arrayOf("Single Image", "Folder")
            AlertDialog.Builder(this).setItems(items) { _, w ->
                if (w == 0) pickImageLauncher.launch(arrayOf("image/*")) else pickFolderLauncher.launch(null)
            }.show()
        }
        changeOnUnlockCheck.isChecked = viewModel.getChangeOnUnlock()
        changeOnUnlockCheck.setOnCheckedChangeListener { _, isChecked ->
            viewModel.saveChangeOnUnlock(isChecked)
            if (isChecked) registerUnlockReceiver() else unregisterUnlockReceiver()
        }
        startButton.setOnClickListener {
            val interval = intervalInput.text.toString().toIntOrNull() ?: 60
            viewModel.saveRotationInterval(interval)
            requestBatteryOptimizationExemption()
            scheduleWallpaperRotation()
            Snackbar.make(it, "Started", Snackbar.LENGTH_SHORT).show()
        }
        stopButton.setOnClickListener {
            WorkManager.getInstance(this).cancelUniqueWork("wallpaper_rotation")
            unregisterUnlockReceiver()
            Snackbar.make(it, "Stopped", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun openEditBottomSheet(config: WallpaperConfig) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_wallpaper_options, null)
        view.findViewById<View>(R.id.optionEdit).setOnClickListener {
            openCropActivity(Uri.parse(config.imageUri), config.id); dialog.dismiss()
        }
        view.findViewById<View>(R.id.optionDelete).setOnClickListener {
            viewModel.deleteConfig(config); dialog.dismiss()
        }
        dialog.setContentView(view); dialog.show()
    }

    private fun requestPermissions() {
        val ps = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (ps.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) permissionLauncher.launch(ps)
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= 23) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:$packageName") })
                } catch (e: Exception) {}
            }
        }
    }

    private fun openCropActivity(uri: Uri, id: Long? = null) {
        val intent = Intent(this, CropActivity::class.java).apply {
            putExtra("image_uri", uri.toString())
            id?.let { putExtra("config_id", it) }
        }
        cropActivityLauncher.launch(intent)
    }

    private fun scheduleWallpaperRotation() {
        val req = PeriodicWorkRequestBuilder<WallpaperWorker>(viewModel.getRotationInterval().toLong(), TimeUnit.MINUTES, 5, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("wallpaper_rotation", ExistingPeriodicWorkPolicy.UPDATE, req)
        sendBroadcast(Intent(this, WallpaperReceiver::class.java).apply { action = "com.example.wallpaperrotator.ROTATE_WALLPAPER" })
        if (viewModel.getChangeOnUnlock()) registerUnlockReceiver()
    }

    private fun registerUnlockReceiver() { try { registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT)) } catch (e: Exception) {} }

    // unregisterReceiver throws IllegalArgumentException if the receiver isn't
    // currently registered (e.g. unchecking the box without ever starting).
    private fun unregisterUnlockReceiver() { try { unregisterReceiver(unlockReceiver) } catch (e: Exception) {} }

    override fun onDestroy() {
        super.onDestroy()
        unregisterUnlockReceiver()
    }
}
