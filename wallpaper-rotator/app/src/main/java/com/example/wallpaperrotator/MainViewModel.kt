package com.example.wallpaperrotator

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val configManager = ConfigManager(application)

    private val _configs = MutableLiveData<List<WallpaperConfig>>()
    val configs: LiveData<List<WallpaperConfig>> = _configs

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _loadingProgress = MutableLiveData<Pair<Int, Int>>() // current, total
    val loadingProgress: LiveData<Pair<Int, Int>> = _loadingProgress

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _selectionMode = MutableLiveData<Boolean>()
    val selectionMode: LiveData<Boolean> = _selectionMode

    private val selectedConfigs = mutableSetOf<Long>()

    init {
        loadConfigs()
    }

    fun loadConfigs() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val configs = withContext(Dispatchers.IO) {
                    configManager.getConfigs()
                }
                _configs.value = configs
            } catch (e: Exception) {
                _error.value = "Failed to load configurations: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addConfig(config: WallpaperConfig) {
        viewModelScope.launch {
            try {
                val currentConfigs = _configs.value.orEmpty().toMutableList()
                currentConfigs.add(config)
                withContext(Dispatchers.IO) {
                    configManager.saveConfigs(currentConfigs)
                }
                _configs.value = currentConfigs
            } catch (e: Exception) {
                _error.value = "Failed to add wallpaper: ${e.message}"
            }
        }
    }

    fun deleteConfig(config: WallpaperConfig) {
        viewModelScope.launch {
            try {
                val currentConfigs = _configs.value.orEmpty().toMutableList()
                currentConfigs.removeAll { it.id == config.id }
                withContext(Dispatchers.IO) {
                    configManager.saveConfigs(currentConfigs)
                }
                _configs.value = currentConfigs
            } catch (e: Exception) {
                _error.value = "Failed to delete wallpaper: ${e.message}"
            }
        }
    }

    fun deleteSelectedConfigs() {
        viewModelScope.launch {
            try {
                val currentConfigs = _configs.value.orEmpty().toMutableList()
                currentConfigs.removeAll { selectedConfigs.contains(it.id) }
                withContext(Dispatchers.IO) {
                    configManager.saveConfigs(currentConfigs)
                }
                _configs.value = currentConfigs
                clearSelection()
            } catch (e: Exception) {
                _error.value = "Failed to delete wallpapers: ${e.message}"
            }
        }
    }

    fun updateConfig(config: WallpaperConfig) {
        viewModelScope.launch {
            try {
                val currentConfigs = _configs.value.orEmpty().toMutableList()
                val index = currentConfigs.indexOfFirst { it.id == config.id }
                if (index != -1) {
                    currentConfigs[index] = config
                    withContext(Dispatchers.IO) {
                        configManager.saveConfigs(currentConfigs)
                    }
                    _configs.value = currentConfigs
                }
            } catch (e: Exception) {
                _error.value = "Failed to update wallpaper: ${e.message}"
            }
        }
    }

    fun addFolderImages(folderUri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                val context = getApplication<Application>()
                
                // Take persistable permission
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            folderUri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: SecurityException) {
                        // Permission might already be granted
                    }
                }

                val folder = DocumentFile.fromTreeUri(context, folderUri)
                if (folder == null) {
                    _error.value = "Cannot access folder"
                    _isLoading.value = false
                    return@launch
                }

                val imageFiles = withContext(Dispatchers.IO) {
                    folder.listFiles().filter { file ->
                        file.isFile && file.type?.startsWith("image/") == true
                    }
                }

                if (imageFiles.isEmpty()) {
                    _error.value = "No images found in folder"
                    _isLoading.value = false
                    return@launch
                }

                val currentConfigs = _configs.value.orEmpty().toMutableList()
                val totalImages = imageFiles.size

                imageFiles.forEachIndexed { index, file ->
                    _loadingProgress.value = Pair(index + 1, totalImages)
                    
                    file.uri?.let { uri ->
                        val config = WallpaperConfig(
                            imageUri = uri.toString(),
                            cropRect = android.graphics.RectF(0f, 0f, 1f, 1f),
                            rotation = 0f,
                            forLockScreen = false,
                            forHomeScreen = true
                        )
                        currentConfigs.add(config)
                    }
                }

                withContext(Dispatchers.IO) {
                    configManager.saveConfigs(currentConfigs)
                }
                _configs.value = currentConfigs
                
            } catch (e: Exception) {
                _error.value = "Failed to add folder: ${e.message}"
            } finally {
                _isLoading.value = false
                _loadingProgress.value = Pair(0, 0)
            }
        }
    }

    fun toggleSelection(configId: Long) {
        if (selectedConfigs.contains(configId)) {
            selectedConfigs.remove(configId)
        } else {
            selectedConfigs.add(configId)
        }
        _selectionMode.value = selectedConfigs.isNotEmpty()
    }

    fun isSelected(configId: Long): Boolean = selectedConfigs.contains(configId)

    fun getSelectedCount(): Int = selectedConfigs.size

    fun clearSelection() {
        selectedConfigs.clear()
        _selectionMode.value = false
    }

    fun selectAll() {
        selectedConfigs.clear()
        _configs.value?.forEach { selectedConfigs.add(it.id) }
        _selectionMode.value = true
    }

    fun getRotationInterval(): Int = configManager.getRotationInterval()
    fun saveRotationInterval(minutes: Int) = configManager.saveRotationInterval(minutes)
    
    fun getRotationMode(): String = configManager.getRotationMode()
    fun saveRotationMode(mode: String) = configManager.saveRotationMode(mode)
    
    fun getChangeOnUnlock(): Boolean = configManager.getChangeOnUnlock()
    fun saveChangeOnUnlock(enabled: Boolean) = configManager.saveChangeOnUnlock(enabled)

    fun clearError() {
        _error.value = null
    }
}
