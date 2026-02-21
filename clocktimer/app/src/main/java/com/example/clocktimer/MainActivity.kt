package com.example.clocktimer

import android.content.Context
import android.media.RingtoneManager
import android.media.Ringtone
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                VisualTimerApp()
            }
        }
    }
}

enum class TimerMode { TIMER, STOPWATCH }

@Composable
fun VisualTimerApp() {
    val context = LocalContext.current
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    var mode by remember { mutableStateOf(TimerMode.TIMER) }
    var isRunning by remember { mutableStateOf(false) }
    var targetTimeInSeconds by remember { mutableStateOf(60) }
    var timerBase by remember { mutableStateOf(0L) }
    var stopwatchBase by remember { mutableStateOf(0L) }
    var sessionCount by remember { mutableStateOf(123) }
    var showMinutes by remember { mutableStateOf(true) }
    var showSettingsMenu by remember { mutableStateOf(false) }

    // Color customization
    var customBackgroundColor by remember { mutableStateOf(Color(0xFFF5F5F5)) }
    var customTimerColor by remember { mutableStateOf(Color(0xFFEA4335)) }
    var customStopwatchColor by remember { mutableStateOf(Color(0xFF1A73E8)) }

    var currentTimeInSeconds by remember { mutableStateOf(60) }

    // Alarm with looping
    var alarmUri by remember {
        mutableStateOf(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
    }
    var ringtone by remember { mutableStateOf<Ringtone?>(null) }
    var showAlarmDialog by remember { mutableStateOf(false) }

    val ringtonePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                alarmUri = uri
            }
        }

    LaunchedEffect(isRunning, mode) {
        while (isRunning) {
            val elapsed = (SystemClock.elapsedRealtime() - if (mode == TimerMode.TIMER) timerBase else stopwatchBase) / 1000

            if (mode == TimerMode.TIMER) {
                val remaining = (targetTimeInSeconds - elapsed).toInt()
                if (remaining <= 0) {
                    currentTimeInSeconds = 0
                    isRunning = false
                    ringtone = playLoopingAlarm(context, alarmUri)
                    showAlarmDialog = true
                } else {
                    currentTimeInSeconds = remaining
                }
            } else {
                currentTimeInSeconds = elapsed.toInt()
            }
            delay(100)
        }
    }

    val backgroundColor = customBackgroundColor
    val accentColor = if (mode == TimerMode.TIMER) customTimerColor else customStopwatchColor
    val textColor = Color(0xFF5F6368)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = if (mode == TimerMode.TIMER) Arrangement.SpaceBetween else Arrangement.Start
            ) {
                if (mode == TimerMode.TIMER) {
                    PresetButton("+1 MIN") {
                        if (!isRunning) {
                            targetTimeInSeconds += 60
                            currentTimeInSeconds = targetTimeInSeconds
                            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                    PresetButton(if (showMinutes) "MINUTES" else "SECONDS") {
                        if (!isRunning) {
                            showMinutes = !showMinutes
                            targetTimeInSeconds = if (showMinutes) 60 else 30
                            currentTimeInSeconds = targetTimeInSeconds
                            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            val mins = currentTimeInSeconds / 60
            val secs = currentTimeInSeconds % 60

            Text(
                text = String.format("%d:%02d", mins, secs),
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                color = if (mode == TimerMode.STOPWATCH) accentColor else textColor,
                letterSpacing = (-1).sp
            )

            Spacer(modifier = Modifier.height(60.dp))

            Box(
                modifier = Modifier
                    .size(320.dp)
                    .pointerInput(Unit) {
                        if (mode == TimerMode.TIMER && !isRunning) {
                            var lastMinutes = -1
                            detectDragGestures { change, _ ->
                                change.consume()
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val touchPos = change.position
                                val dx = touchPos.x - center.x
                                val dy = touchPos.y - center.y
                                var angle = atan2(dy, dx) + PI.toFloat() / 2f
                                if (angle < 0) angle += (2 * PI).toFloat()

                                val maxUnits = if (showMinutes) 60 else 60
                                val units = ((angle / (2 * PI)) * maxUnits).toInt().coerceIn(0, maxUnits)

                                if (units != lastMinutes) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                                    lastMinutes = units
                                }

                                targetTimeInSeconds = if (showMinutes) units * 60 else units
                                currentTimeInSeconds = targetTimeInSeconds
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                TimerClockFace(mode, currentTimeInSeconds, targetTimeInSeconds, showMinutes, accentColor, textColor)

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(3.dp, if (mode == TimerMode.STOPWATCH) accentColor else textColor.copy(alpha = 0.5f), CircleShape)
                        .background(Color.White.copy(alpha = 0.95f))
                        .clickable {
                            if (!isRunning) {
                                mode = if (mode == TimerMode.TIMER) TimerMode.STOPWATCH else TimerMode.TIMER
                                currentTimeInSeconds = if (mode == TimerMode.TIMER) 60 else 0
                                targetTimeInSeconds = 60
                            }
                        }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                ResetButton {
                    isRunning = false
                    if (mode == TimerMode.TIMER) {
                        currentTimeInSeconds = 60
                        targetTimeInSeconds = 60
                    } else {
                        currentTimeInSeconds = 0
                    }
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }

            Spacer(modifier = Modifier.height(80.dp))

            FloatingActionButton(
                onClick = {
                    if (!isRunning) {
                        if (mode == TimerMode.TIMER) timerBase = SystemClock.elapsedRealtime()
                        else stopwatchBase = SystemClock.elapsedRealtime()
                    }
                    isRunning = !isRunning
                },
                containerColor = accentColor,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isRunning) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        IconButton(
            onClick = {
                showSettingsMenu = !showSettingsMenu
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.size(24.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(textColor, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(textColor, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(textColor, RoundedCornerShape(2.dp)))
            }
        }

        if (showSettingsMenu) {
            AlertDialog(
                onDismissRequest = { showSettingsMenu = false },
                title = { Text("Settings") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Background", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColorCircle(Color(0xFFF5F5F5), customBackgroundColor) { customBackgroundColor = Color(0xFFF5F5F5) }
                            ColorCircle(Color(0xFFFFFFFF), customBackgroundColor) { customBackgroundColor = Color(0xFFFFFFFF) }
                            ColorCircle(Color(0xFF1A1A1A), customBackgroundColor) { customBackgroundColor = Color(0xFF1A1A1A) }
                            ColorCircle(Color(0xFF000000), customBackgroundColor) { customBackgroundColor = Color(0xFF000000) }
                        }

                        Text("Timer Color", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColorCircle(Color(0xFFEA4335), customTimerColor) { customTimerColor = Color(0xFFEA4335) }
                            ColorCircle(Color(0xFFFF9800), customTimerColor) { customTimerColor = Color(0xFFFF9800) }
                            ColorCircle(Color(0xFF4CAF50), customTimerColor) { customTimerColor = Color(0xFF4CAF50) }
                            ColorCircle(Color(0xFF9C27B0), customTimerColor) { customTimerColor = Color(0xFF9C27B0) }
                        }

                        Text("Stopwatch Color", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColorCircle(Color(0xFF1A73E8), customStopwatchColor) { customStopwatchColor = Color(0xFF1A73E8) }
                            ColorCircle(Color(0xFF00BCD4), customStopwatchColor) { customStopwatchColor = Color(0xFF00BCD4) }
                            ColorCircle(Color(0xFF4CAF50), customStopwatchColor) { customStopwatchColor = Color(0xFF4CAF50) }
                            ColorCircle(Color(0xFFE91E63), customStopwatchColor) { customStopwatchColor = Color(0xFFE91E63) }
                        }

                        Text("Alarm Sound", fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, alarmUri)
                                }
                                ringtonePickerLauncher.launch(intent)
                            }
                        ) {
                            Text("Choose Alarm Sound")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsMenu = false }) {
                        Text("Done")
                    }
                }
            )
        }

        // Alarm dialog
        if (showAlarmDialog) {
            Dialog(onDismissRequest = {}) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Timer Finished!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = {
                                ringtone?.stop()
                                ringtone = null
                                showAlarmDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("STOP ALARM", fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColorCircle(color: Color, selectedColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (color == selectedColor) 3.dp else 1.dp, if (color == selectedColor) Color.Black else Color.Gray, CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
fun PresetButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
        shadowElevation = 1.dp
    ) {
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF202124))
        }
    }
}

@Composable
fun ResetButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
        shadowElevation = 1.dp
    ) {
        Row(modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color(0xFF5F6368), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("RESET", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF5F6368))
        }
    }
}

@Composable
fun TimerClockFace(mode: TimerMode, currentTimeInSeconds: Int, targetTimeInSeconds: Int, showMinutes: Boolean, accentColor: Color, textColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        for (i in 0 until 60) {
            val angle = (i * 6 - 90) * PI / 180
            val isMajor = i % 5 == 0
            val markRadius = radius - if (isMajor) 32.dp.toPx() else 24.dp.toPx()
            val markLength = if (isMajor) 16.dp.toPx() else 12.dp.toPx()
            val markWidth = if (isMajor) 3.dp.toPx() else 2.dp.toPx()

            drawLine(
                color = if (isMajor) Color(0xFF202124) else textColor,
                start = Offset(center.x + cos(angle).toFloat() * markRadius, center.y + sin(angle).toFloat() * markRadius),
                end = Offset(center.x + cos(angle).toFloat() * (markRadius + markLength), center.y + sin(angle).toFloat() * (markRadius + markLength)),
                strokeWidth = markWidth
            )
        }

        if (mode == TimerMode.TIMER) {
            val maxTime = if (!showMinutes) 60f else 3600f
            val displayTime = if (currentTimeInSeconds > 0) currentTimeInSeconds else targetTimeInSeconds
            val progress = displayTime.toFloat() / maxTime
            val sweepAngle = 360f * progress

            drawArc(color = accentColor, startAngle = -90f, sweepAngle = sweepAngle, useCenter = true, topLeft = Offset.Zero, size = size)
        }

        if (mode == TimerMode.STOPWATCH && currentTimeInSeconds > 0) {
            val secondsInCurrentMinute = currentTimeInSeconds % 60
            val angle = (secondsInCurrentMinute * 6 - 90) * PI / 180
            val handLength = radius - 40.dp.toPx()

            drawLine(color = accentColor, start = center, end = Offset(center.x + cos(angle).toFloat() * handLength, center.y + sin(angle).toFloat() * handLength), strokeWidth = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(color = accentColor, radius = 10.dp.toPx(), center = center)
        }
    }
}

fun playLoopingAlarm(context: Context, uri: Uri): Ringtone? {
    return try {
        val ringtone = RingtoneManager.getRingtone(context, uri)
        ringtone?.audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone?.isLooping = true
        ringtone?.play()
        ringtone
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}