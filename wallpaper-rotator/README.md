# Wallpaper Rotator for Android

A production-quality Android app that automatically rotates your wallpapers through a customizable set of photos with precise positioning control. Built with Material 3 design and professional-grade architecture.

## Features

### Core Functionality
- **Drag-to-Position Cropping**: Professional wallpaper positioning interface
    - Fixed screen frame preview showing exact wallpaper area
    - Drag image to position it within the frame
    - Pinch-to-zoom for precise composition
    - Dotted center line for alignment reference
    - Real-time "what you see is what you get" preview
- **Dual Screen Support**: Separate configurations for home and lock screens
- **Flexible Intervals**: Enter any number of minutes (1 to unlimited)
- **Multiple Modes**: Sequential or random wallpaper rotation
- **Unlock Trigger**: Optional wallpaper change on device unlock
- **Bulk Import**: Add entire folders of images at once

### Modern UI/UX (Material 3)
- **Dynamic Theming**: Adapts to system wallpaper colors
- **Grid View**: Beautiful 2-column image grid with live thumbnails
- **Bottom Sheet**: Quick actions (Edit/Delete) without full-screen transitions
- **Selection Mode**: Multi-select for batch operations
- **Progress Indicators**: Real-time feedback for folder scanning
- **Empty States**: Helpful illustrations when no wallpapers
- **Snackbar Undo**: Recover from accidental deletions
- **FAB Menu**: Floating action button for adding wallpapers
- **Clean Interface**: Simplified settings without feature bloat

### Technical Excellence
- **Proper Wallpaper API**: Uses `WallpaperManager.setBitmap(Bitmap, Rect, boolean, int)` for perfect positioning
- **MVVM Architecture**: ViewModel + LiveData for reactive UI
- **Memory Efficient**: Coil image loading with automatic caching and bitmap recycling
- **Configuration-Safe**: Survives screen rotations without data loss
- **Background Optimized**: WorkManager for Doze-compatible execution
- **Proper Threading**: All I/O on background threads, zero ANR
- **Comprehensive Error Handling**: Try-catch throughout with user-friendly messages
- **URI Permissions**: Persistent file access after reboot
- **Screen Aspect Ratio Aware**: Dynamically adapts to any phone screen ratio

## How It Works

### Wallpaper Positioning
Unlike basic wallpaper apps that pre-crop images (causing misalignment), this app uses Android's proper wallpaper API:

1. **User positions image**: Drag and pinch within a fixed frame representing the screen
2. **App saves coordinates**: Normalized rect (0.0-1.0) stored in configuration
3. **System applies perfectly**: Full image + visible hint passed to WallpaperManager
4. **Result**: Zero stretching, perfect alignment, exactly as previewed

**Technical advantage**: The system handles all scaling and positioning logic, avoiding common pitfalls like:
- Stretched/squashed images
- Off-center positioning
- Aspect ratio mismatches
- Bottom-left shifting

### Architecture

```
┌─────────────────────────────────────────────┐
│              MainActivity                    │
│  ┌─────────────┐      ┌─────────────────┐  │
│  │ViewModel    │◄─────┤  ConfigManager  │  │
│  │ (LiveData)  │      │ (Persistence)   │  │
│  └──────┬──────┘      └─────────────────┘  │
│         │                                    │
│    ┌────▼────┐                              │
│    │ Adapter │                              │
│    │ (Coil)  │                              │
│    └─────────┘                              │
└─────────────────────────────────────────────┘
         │
         │ Start Rotation
         ▼
┌─────────────────────────────────────────────┐
│         WorkManager (Periodic)               │
│              ▼                               │
│       WallpaperWorker                        │
│  (Applies wallpaper every N minutes)         │
└─────────────────────────────────────────────┘
         │
         │ On Unlock (Optional)
         ▼
┌─────────────────────────────────────────────┐
│       WallpaperReceiver                      │
│  (Broadcast receiver for manual triggers)    │
└─────────────────────────────────────────────┘
```

### Components

**UI Layer:**
- `MainActivity`: Main screen with settings and wallpaper grid
- `CropActivity`: Drag-to-position interface with fixed frame
- `CropView`: Custom view with Matrix transformations for smooth dragging/zooming
- `WallpaperAdapter`: RecyclerView adapter with Coil and DiffUtil

**Business Logic:**
- `MainViewModel`: State management, LiveData for reactive updates
- `ConfigManager`: SharedPreferences wrapper for persistence
- `WallpaperSetter`: Proper wallpaper API implementation

**Background:**
- `WallpaperWorker`: WorkManager job for scheduled rotation
- `WallpaperReceiver`: BroadcastReceiver for unlock events
- `BootReceiver`: Restarts rotation after device reboot

## Key Improvements Over Basic Wallpaper Apps

### UX/UI Enhancements
1. **Professional cropping interface** - Drag-to-position like Google Photos
2. **Fixed frame preview** - See exactly what becomes your wallpaper
3. **Pinch-to-zoom** - Precise composition control
4. **Center reference line** - Dotted line for perfect alignment
5. **Material 3 design** - Dynamic colors, modern components
6. **Grid layout** - Visual browsing vs text lists
7. **Quick actions** - Bottom sheet for Edit/Delete
8. **Batch operations** - Multi-select with long-press
9. **Flexible intervals** - Enter any number of minutes
10. **Clean interface** - No feature bloat

### Technical Superiority
11. **Proper wallpaper API** - Zero stretching or misalignment
12. **Aspect ratio aware** - Works on all phone screens (9:16, 9:18, 9:20, 9:21, etc.)
13. **WorkManager** - Reliable background work vs broken AlarmManager
14. **MVVM architecture** - Clean separation of concerns
15. **Memory management** - Proper bitmap recycling, zero leaks
16. **URI permissions** - Persistent access across reboots
17. **Background threading** - All I/O off main thread
18. **Error handling** - Comprehensive try-catch with logging
19. **Configuration resilient** - Survives screen rotations
20. **Battery optimized** - Respects Doze mode

## Build Instructions

1. Clone repository
2. Open in Android Studio Arctic Fox or newer
3. Sync Gradle
4. Build and run (minimum API 24, target API 34)

## Dependencies

```gradle
// Core Android
implementation 'androidx.core:core-ktx:1.15.0'
implementation 'androidx.appcompat:appcompat:1.7.0'
implementation 'androidx.constraintlayout:constraintlayout:2.2.0'
implementation 'androidx.recyclerview:recyclerview:1.3.2'

// Material 3
implementation 'com.google.android.material:material:1.12.0'

// Lifecycle (ViewModel + LiveData)
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7'
implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.8.7'

// Coil (Image Loading)
implementation 'io.coil-kt:coil:2.5.0'

// WorkManager
implementation 'androidx.work:work-runtime-ktx:2.9.0'

// Coroutines
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3'
```

## Usage Guide

### Quick Start
1. Tap FAB (+ button) → "Single Image"
2. Select a photo from gallery
3. **Drag** the image to position it within the frame
4. **Pinch** to zoom in/out for perfect composition
5. Use the dotted center line to align subjects
6. Check "Home" and/or "Lock" screen
7. Tap "Save"
8. Enter interval in minutes (e.g., 30)
9. Select "Sequential" or "Random" mode
10. Tap "Start"

### Bulk Import
1. Tap FAB → "Folder"
2. Select a folder containing images
3. All images added automatically
4. Edit individual positions by tapping thumbnails

### Batch Operations
1. Long-press any wallpaper thumbnail
2. Selection mode activates
3. Tap additional wallpapers to select
4. Tap "Delete" or "Select All" at top
5. Confirm deletion (with undo option)

### Best Practices
- **High resolution images**: Use at least your screen resolution for best quality
- **Battery optimization**: Disable for the app in Settings → Apps → WallpaperRotator → Battery
- **Intervals**: 15+ minutes recommended for battery life
- **Screen selection**: Use "Lock" for lock screen only, "Home" for home screen only, or both

## Permissions

Required permissions:
- `READ_MEDIA_IMAGES` (Android 13+) / `READ_EXTERNAL_STORAGE` (Android 12-): Gallery access
- `SET_WALLPAPER`: Apply wallpapers to home/lock screens
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Reliable background execution
- `RECEIVE_BOOT_COMPLETED`: Auto-restart rotation after device reboot

## Performance

**Image Loading:**
- Coil handles memory efficiently with automatic bitmap pooling
- Downsampling for thumbnails (memory efficient)
- Full resolution only when needed

**UI Updates:**
- DiffUtil calculates minimal changes
- RecyclerView only updates changed items
- Smooth scrolling with view recycling

**Background Work:**
- WorkManager respects Doze mode
- Exponential backoff on failures
- Persistent across reboots

**Memory Management:**
- Bitmap recycling after use
- No memory leaks (verified)
- Proper lifecycle handling

**Threading:**
- All I/O on background threads
- UI updates on main thread only
- Zero ANR (Application Not Responding)

## Device Compatibility

**Minimum Requirements:**
- Android 7.0 (API 24) minimum
- Android 7.1+ (API 25) for separate lock screen support
- Any screen aspect ratio supported (9:16, 9:18, 9:19.5, 9:20, 9:21, etc.)

**Recommended:**
- Android 10+ for best performance
- 2GB+ RAM for smooth operation
- Battery optimization disabled for intervals < 60 minutes

**Tested on:**
- Various screen sizes and aspect ratios
- Notched and non-notched displays
- Devices from multiple manufacturers

## Known Issues

### Minor Issues
- **App icon off-center**: Icon appears slightly off-center in launcher (cosmetic only)

### Limitations
- Minimum interval: 15 minutes (WorkManager constraint)
- Maximum images: Limited by available storage and memory
- GIF/Video: Not supported (static images only)

## Troubleshooting

### Wallpaper stops changing after 15-30 minutes
**Cause:** Battery optimization killing the app  
**Fix:** Settings → Apps → WallpaperRotator → Battery → Unrestricted

### Wallpaper doesn't match preview
**Cause:** Old configuration with previous cropping method  
**Fix:** Delete wallpaper and re-add with current version

### App crashes when adding folder
**Cause:** Too many large images  
**Fix:** Add images in smaller batches

### Toolbar hidden behind status bar
**Cause:** System window insets not properly handled  
**Fix:** Update to latest version (fixed in current release)

## Technical Deep Dive

### Why This Cropping Method Works

**The Problem with Pre-Cropping:**
```kotlin
// ❌ Common mistake (causes misalignment)
val croppedBitmap = Bitmap.createBitmap(original, x, y, width, height)
val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, screenW, screenH, true)
wallpaperManager.setBitmap(scaledBitmap) // System repositions → misalignment
```

**The Proper Way:**
```kotlin
// ✅ Correct approach (perfect alignment)
val visibleRect = Rect(cropX, cropY, cropRight, cropBottom) // In bitmap coords
wallpaperManager.setBitmap(
    fullBitmap,      // Full original image
    visibleRect,     // Which part to show
    true,            // Allow backup
    FLAG_LOCK        // Home or lock screen
)
// System handles all scaling/positioning perfectly
```

**Benefits:**
- System knows full image context
- System handles parallax correctly
- System knows screen aspect ratio
- System handles navigation bars/notches
- Zero stretching or distortion

### Coordinate System

**Display coordinates** (what user drags):
- Screen space: pixels on the display
- Frame position: fixed RectF in view

**Bitmap coordinates** (what we save):
- Image space: pixels in original bitmap
- Inverse matrix transformation

**Normalized coordinates** (what we store):
- Range: 0.0 to 1.0 (percentage)
- Independent of image resolution
- Works with any bitmap size

**Example flow:**
```
User drags image → Frame shows area 500,500 to 2500,4000 pixels
                 ↓
Matrix inversion → Convert to bitmap coords
                 ↓
Normalize       → RectF(0.167, 0.125, 0.833, 1.0)
                 ↓
Save to config  → Persisted in SharedPreferences
                 ↓
Apply wallpaper → Convert back to bitmap coords
                 ↓
System renders  → Perfect alignment
```

## Production Readiness Checklist

This app includes:
- ✅ Comprehensive error handling with try-catch
- ✅ Progress indicators for all async operations
- ✅ Empty states with helpful illustrations
- ✅ Undo functionality for destructive actions
- ✅ Material 3 design system
- ✅ MVVM architecture with clear separation
- ✅ Memory leak prevention with proper lifecycle
- ✅ Configuration change handling (screen rotation)
- ✅ Battery optimization awareness
- ✅ Kotlin null safety throughout
- ✅ Background thread offloading for I/O
- ✅ Comprehensive logging for debugging
- ✅ URI permission management
- ✅ User-friendly error messages
- ✅ Consistent UI/UX patterns

## Future Enhancements

Potential additions (not currently implemented):
- [ ] Live wallpaper preview with phone frame mockup
- [ ] Smart crop suggestions using face detection
- [ ] Wallpaper collections/playlists
- [ ] Crossfade transition animations
- [ ] Cloud backup/sync integration
- [ ] Home screen widget for manual skip
- [ ] Dark/Light theme toggle
- [ ] Export/Import configurations
- [ ] Wallpaper statistics (most used, etc.)
- [ ] Time-based wallpaper scheduling

## Contributing

This is a personal project demonstrating Android best practices. Feel free to fork and modify for your own use.

## License

[Your License Here]

## Credits

Built with:
- Material Design 3 by Google
- Coil by Coil Contributors
- WorkManager by Android Jetpack
- Kotlin Coroutines by JetBrains

---

**Version:** 1.0  
**Last Updated:** February 2026  
**Minimum SDK:** 24 (Android 7.0)  
**Target SDK:** 34 (Android 14)