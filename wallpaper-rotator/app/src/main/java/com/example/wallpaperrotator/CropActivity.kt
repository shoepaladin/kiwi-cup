package com.example.wallpaperrotator

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox

class CropActivity : AppCompatActivity() {

    private lateinit var cropView: CropView
    private lateinit var lockScreenCheck: MaterialCheckBox
    private lateinit var homeScreenCheck: MaterialCheckBox
    private lateinit var saveButton: MaterialButton
    private lateinit var imageUri: Uri
    private var bitmap: Bitmap? = null
    private var editingConfigId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

        cropView = findViewById(R.id.cropView)
        lockScreenCheck = findViewById(R.id.lockScreenCheck)
        homeScreenCheck = findViewById(R.id.homeScreenCheck)
        saveButton = findViewById(R.id.saveButton)

        val uriString = intent.getStringExtra("image_uri") ?: run {
            finish()
            return
        }
        imageUri = Uri.parse(uriString)
        
        if (intent.hasExtra("config_id")) {
            editingConfigId = intent.getLongExtra("config_id", -1)
        }

        loadImage()
        setupControls()
        
        if (editingConfigId != null) {
            loadExistingConfig(editingConfigId!!)
        }
    }

    private fun loadImage() {
        try {
            try {
                contentResolver.takePersistableUriPermission(
                    imageUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Permission might already be granted
            }

            val inputStream = contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Toast.makeText(this, "Cannot access image file", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap == null) {
                Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            cropView.setBitmap(bitmap!!)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupControls() {
        homeScreenCheck.isChecked = true

        saveButton.setOnClickListener {
            saveConfiguration()
        }
    }
    
    private fun loadExistingConfig(configId: Long) {
        val configManager = ConfigManager(this)
        val config = configManager.getConfigs().find { it.id == configId }
        if (config != null) {
            lockScreenCheck.isChecked = config.forLockScreen
            homeScreenCheck.isChecked = config.forHomeScreen
            cropView.setCropRect(config.cropRect)
        }
    }

    private fun saveConfiguration() {
        if (!lockScreenCheck.isChecked && !homeScreenCheck.isChecked) {
            Toast.makeText(this, "Select at least one screen type", Toast.LENGTH_SHORT).show()
            return
        }

        val config = WallpaperConfig(
            imageUri = imageUri.toString(),
            cropRect = cropView.getCropRect(),
            rotation = 0f,
            forLockScreen = lockScreenCheck.isChecked,
            forHomeScreen = homeScreenCheck.isChecked,
            id = editingConfigId ?: System.currentTimeMillis()
        )

        val configManager = ConfigManager(this)
        val configs = configManager.getConfigs().toMutableList()
        if (editingConfigId != null) {
            val index = configs.indexOfFirst { it.id == editingConfigId }
            if (index != -1) {
                configs[index] = config
            }
        } else {
            configs.add(config)
        }
        configManager.saveConfigs(configs)

        val resultIntent = Intent().apply {
            putExtra("config_added", true)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cropView.cleanup()
        bitmap?.recycle()
    }
}
