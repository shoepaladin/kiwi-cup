package com.example.wallpaperrotator

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    
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

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { openCropActivity(it) }
    }

    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.addFolderImages(it) }
    }

    private val cropActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.loadConfigs()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Snackbar.make(findViewById(android.R.id.content), 
                "Permissions required", Snackbar.LENGTH_LONG).show()
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
    }

    private fun setupViews() {
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

        setSupportActionBar(toolbar)
        
        // FIXED: Make selection toolbar appear ABOVE content
        selectionToolbar.bringToFront()
        
        selectionToolbar.setNavigationOnClickListener {
            viewModel.clearSelection()
        }

        // Load saved interval
        val savedInterval = viewModel.getRotationInterval()
        intervalInput.setText(savedInterval.toString())
    }

    private fun setupRecyclerView() {
        adapter = WallpaperAdapter(
            onItemClick = { config ->
                openEditBottomSheet(config)
            },
            onItemLongClick = { config ->
                viewModel.toggleSelection(config.id)
                true
            },
            isSelected = { id ->
                viewModel.isSelected(id)
            }
        )
        
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter
    }

    private fun setupSpinners() {
        // Rotation mode spinner
        val modes = arrayOf("Sequential", "Random")
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        rotationModeSpinner.adapter = modeAdapter

        val savedMode = viewModel.getRotationMode()
        rotationModeSpinner.setSelection(if (savedMode == "random") 1 else 0)

        rotationModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val mode = if (position == 1) "random" else "sequential"
                viewModel.saveRotationMode(mode)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupObservers() {
        viewModel.configs.observe(this) { configs ->
            adapter.submitList(configs)
            emptyState.visibility = if (configs.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (configs.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.isLoading.observe(this) { isLoading ->
            progressContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.loadingProgress.observe(this) { (current, total) ->
            if (total > 0) {
                progressBar.isIndeterminate = false
                progressBar.max = total
                progressBar.progress = current
                progressText.text = "Adding images: $current / $total"
            } else {
                progressBar.isIndeterminate = true
                progressText.text = "Loading..."
            }
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Snackbar.make(findViewById(android.R.id.content), it, Snackbar.LENGTH_LONG)
                    .setAction("Dismiss") { viewModel.clearError() }
                    .show()
                viewModel.clearError()
            }
        }

        viewModel.selectionMode.observe(this) { selectionMode ->
            if (selectionMode) {
                toolbar.visibility = View.GONE
                selectionToolbar.visibility = View.VISIBLE
                selectionToolbar.elevation = 8f
                selectionToolbar.title = "${viewModel.getSelectedCount()} selected"
                selectionToolbar.menu.clear()
                selectionToolbar.inflateMenu(R.menu.selection_menu)
                fab.hide()
            } else {
                toolbar.visibility = View.VISIBLE
                selectionToolbar.visibility = View.GONE
                fab.show()
            }
            adapter.setSelectionMode(selectionMode)
        }
    }

    private fun setupButtons() {
        fab.setOnClickListener {
            showAddMenu()
        }

        changeOnUnlockCheck.isChecked = viewModel.getChangeOnUnlock()
        changeOnUnlockCheck.setOnCheckedChangeListener { _, isChecked ->
            viewModel.saveChangeOnUnlock(isChecked)
            if (isChecked) {
                registerUnlockReceiver()
            } else {
                unregisterUnlockReceiver()
            }
        }

        startButton.setOnClickListener {
            val configs = viewModel.configs.value.orEmpty()
            if (configs.isEmpty()) {
                Snackbar.make(it, "Add wallpapers first", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate and save interval
            val intervalText = intervalInput.text.toString()
            val interval = intervalText.toIntOrNull()
            if (interval == null || interval < 1) {
                Snackbar.make(it, "Please enter a valid interval (1 or more minutes)", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.saveRotationInterval(interval)

            requestBatteryOptimizationExemption()
            scheduleWallpaperRotation()
            Snackbar.make(it, "Wallpaper rotation started", Snackbar.LENGTH_SHORT).show()
        }

        stopButton.setOnClickListener {
            cancelWallpaperRotation()
            Snackbar.make(it, "Wallpaper rotation stopped", Snackbar.LENGTH_SHORT).show()
        }

        selectionToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete -> {
                    showDeleteConfirmation()
                    true
                }
                R.id.action_select_all -> {
                    viewModel.selectAll()
                    true
                }
                else -> false
            }
        }
    }

    private fun showAddMenu() {
        val items = arrayOf("Single Image", "Folder")
        AlertDialog.Builder(this)
            .setTitle("Add Wallpapers")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> pickFolderLauncher.launch(null)
                }
            }
            .show()
    }

    private fun openEditBottomSheet(config: WallpaperConfig) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_wallpaper_options, null)
        
        view.findViewById<View>(R.id.optionEdit).setOnClickListener {
            openCropActivity(Uri.parse(config.imageUri), config.id)
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.optionDelete).setOnClickListener {
            showDeleteSingleConfirmation(config)
            dialog.dismiss()
        }
        
        // REMOVED: Preview option is no longer visible
        view.findViewById<View>(R.id.optionPreview)?.visibility = View.GONE
        
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showDeleteConfirmation() {
        val count = viewModel.getSelectedCount()
        AlertDialog.Builder(this)
            .setTitle("Delete $count wallpaper${if (count > 1) "s" else ""}?")
            .setMessage("This cannot be undone")
            .setPositiveButton("Delete") { _, _ ->
                val deletedConfigs = viewModel.configs.value.orEmpty()
                    .filter { viewModel.isSelected(it.id) }
                viewModel.deleteSelectedConfigs()
                showUndoSnackbar(deletedConfigs)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteSingleConfirmation(config: WallpaperConfig) {
        AlertDialog.Builder(this)
            .setTitle("Delete wallpaper?")
            .setMessage("This cannot be undone")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteConfig(config)
                showUndoSnackbar(listOf(config))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUndoSnackbar(deletedConfigs: List<WallpaperConfig>) {
        Snackbar.make(
            findViewById(android.R.id.content),
            "${deletedConfigs.size} wallpaper${if (deletedConfigs.size > 1) "s" else ""} deleted",
            Snackbar.LENGTH_LONG
        ).setAction("Undo") {
            deletedConfigs.forEach { viewModel.addConfig(it) }
        }.show()
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Battery Optimization")
                    .setMessage("For reliable wallpaper rotation, please disable battery optimization.")
                    .setPositiveButton("Settings") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            Snackbar.make(findViewById(android.R.id.content),
                                "Please disable manually", Snackbar.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("Skip", null)
                    .show()
            }
        }
    }

    private fun openCropActivity(uri: Uri, configId: Long? = null) {
        val intent = Intent(this, CropActivity::class.java).apply {
            putExtra("image_uri", uri.toString())
            configId?.let { putExtra("config_id", it) }
        }
        cropActivityLauncher.launch(intent)
    }

    private fun scheduleWallpaperRotation() {
        val intervalMinutes = viewModel.getRotationInterval()
        
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)
            .setRequiresDeviceIdle(false)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(0, TimeUnit.SECONDS)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                15, TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "wallpaper_rotation",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        val intent = Intent(this, WallpaperReceiver::class.java).apply {
            action = "com.example.wallpaperrotator.ROTATE_WALLPAPER"
        }
        sendBroadcast(intent)

        if (viewModel.getChangeOnUnlock()) {
            registerUnlockReceiver()
        }
    }

    private fun cancelWallpaperRotation() {
        WorkManager.getInstance(this).cancelUniqueWork("wallpaper_rotation")
        unregisterUnlockReceiver()
    }

    private fun registerUnlockReceiver() {
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        registerReceiver(unlockReceiver, filter)
    }

    private fun unregisterUnlockReceiver() {
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!viewModel.getChangeOnUnlock()) {
            unregisterUnlockReceiver()
        }
    }

    override fun onBackPressed() {
        if (viewModel.selectionMode.value == true) {
            viewModel.clearSelection()
        } else {
            super.onBackPressed()
        }
    }
}
