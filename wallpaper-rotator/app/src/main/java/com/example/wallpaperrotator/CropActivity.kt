package com.example.wallpaperrotator

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.slider.Slider

class CropActivity : AppCompatActivity() {

    private lateinit var cropView: CropView
    private lateinit var lockScreenCheck: MaterialCheckBox
    private lateinit var homeScreenCheck: MaterialCheckBox
    private lateinit var saveButton: MaterialButton
    private lateinit var rotateButton: MaterialButton
    private lateinit var rotationSlider: Slider
    private lateinit var imageUri: Uri
    private var bitmap: Bitmap? = null
    private var editingConfigId: Long? = null

    // Original (undecoded) image dimensions. The preview bitmap may be a subsampled
    // decode; saved transforms are always converted to original-pixel space so they
    // are independent of whatever resolution happened to be decoded.
    private var srcWidth = 0
    private var srcHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

        cropView = findViewById(R.id.cropView)
        lockScreenCheck = findViewById(R.id.lockScreenCheck)
        homeScreenCheck = findViewById(R.id.homeScreenCheck)
        saveButton = findViewById(R.id.saveButton)
        rotateButton = findViewById(R.id.rotateButton)
        rotationSlider = findViewById(R.id.rotationSlider)

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
            // Defer until the CropView has been laid out, otherwise the screen frame
            // is empty and the transform/crop cannot be reconstructed.
            cropView.post { loadExistingConfig(editingConfigId!!) }
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

            // Bounds-only decode for the original dimensions.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            srcWidth = bounds.outWidth
            srcHeight = bounds.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) {
                Toast.makeText(this, "Cannot access image file", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // Subsampled decode for the preview: enough resolution for the screen,
            // without loading a potentially huge photo at full size (OOM risk).
            val screen = ScreenUtils.getRealSize(this)
            val options = BitmapFactory.Options().apply {
                inSampleSize = WallpaperGeometry.calculateInSampleSize(
                    srcWidth, srcHeight, screen.x, screen.y
                )
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            bitmap = contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

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

        // Fine rotation via slider (-180°..180°).
        rotationSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                cropView.setImageRotation(value)
            }
        }

        // Quick 90° clockwise step; keeps the slider in sync (wrapped to its range).
        rotateButton.setOnClickListener {
            val newAngle = wrapDegrees(cropView.getImageRotation() + 90f)
            cropView.setImageRotation(newAngle)
            rotationSlider.value = newAngle
        }
    }

    /** Wraps an angle into the slider's (-180°, 180°] range. */
    private fun wrapDegrees(deg: Float): Float {
        var d = deg % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }
    
    private fun loadExistingConfig(configId: Long) {
        val configManager = ConfigManager(this)
        val config = configManager.getConfigs().find { it.id == configId }
        if (config != null) {
            lockScreenCheck.isChecked = config.forLockScreen
            homeScreenCheck.isChecked = config.forHomeScreen
            val transform = config.transform
            if (transform != null) {
                cropView.setTransform(sourceToPreviewTransform(transform))
            } else {
                cropView.setCropRect(config.cropRect)
            }
            rotationSlider.value = wrapDegrees(cropView.getImageRotation())
        }
    }

    /**
     * Converts a stored transform (ORIGINAL image px -> normalized [0,1]) into the
     * preview bitmap's pixel space so CropView can restore the framing. The preview
     * may be a subsampled decode with smaller pixel dimensions than the original.
     */
    private fun sourceToPreviewTransform(values: FloatArray): FloatArray {
        val bmp = bitmap ?: return values
        if (srcWidth <= 0 || srcHeight <= 0) return values
        if (bmp.width == srcWidth && bmp.height == srcHeight) return values
        val m = Matrix()
        m.setValues(values) // original px -> normalized
        // Map preview px -> original px first.
        m.preScale(srcWidth.toFloat() / bmp.width, srcHeight.toFloat() / bmp.height)
        return FloatArray(9).also { m.getValues(it) }
    }

    /** Inverse of [sourceToPreviewTransform]: preview px space -> original px space. */
    private fun previewToSourceTransform(values: FloatArray): FloatArray {
        val bmp = bitmap ?: return values
        if (srcWidth <= 0 || srcHeight <= 0) return values
        if (bmp.width == srcWidth && bmp.height == srcHeight) return values
        val m = Matrix()
        m.setValues(values) // preview px -> normalized
        // Map original px -> preview px first.
        m.preScale(bmp.width.toFloat() / srcWidth, bmp.height.toFloat() / srcHeight)
        return FloatArray(9).also { m.getValues(it) }
    }

    private fun saveConfiguration() {
        if (!lockScreenCheck.isChecked && !homeScreenCheck.isChecked) {
            Toast.makeText(this, "Select at least one screen type", Toast.LENGTH_SHORT).show()
            return
        }

        val config = WallpaperConfig(
            imageUri = imageUri.toString(),
            cropRect = cropView.getCropRect(),   // legacy fallback
            rotation = cropView.getImageRotation(),
            forLockScreen = lockScreenCheck.isChecked,
            forHomeScreen = homeScreenCheck.isChecked,
            id = editingConfigId ?: System.currentTimeMillis(),
            // Exact zoom + pan + rotation, stored in ORIGINAL-pixel space so it is
            // valid at any decode resolution (WallpaperSetter subsamples too).
            transform = previewToSourceTransform(cropView.getTransform())
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
        // CropView owns the bitmap once handed to it; cleanup() recycles it.
        // Do NOT recycle `bitmap` again here — that double-recycles the same object.
        cropView.cleanup()
        bitmap = null
    }
}
