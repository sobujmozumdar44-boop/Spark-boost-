package com.sparkboost.tecno.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sparkboost.tecno.hardware.Spark40Optimizer
import kotlinx.coroutines.*

class FloatingBoosterOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: ComposeView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var optimizer: Spark40Optimizer

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentFps by mutableIntStateOf(60)
    private var frameCount = 0
    private var lastFpsTimestamp = System.nanoTime()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameCount++
            val elapsed = frameTimeNanos - lastFpsTimestamp
            if (elapsed >= 1_000_000_000L) {
                currentFps = ((frameCount * 1_000_000_000L) / elapsed).toInt()
                frameCount = 0
                lastFpsTimestamp = frameTimeNanos
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        optimizer = Spark40Optimizer(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        Choreographer.getInstance().postFrameCallback(frameCallback)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 220
        }

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBoosterOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBoosterOverlayService)
            setContent {
                FloatingBoosterHud(
                    fps = currentFps,
                    optimizer = optimizer,
                    onDrag = { dx, dy ->
                        layoutParams.x += dx.toInt()
                        layoutParams.y += dy.toInt()
                        windowManager.updateViewLayout(overlayView, layoutParams)
                    },
                    onToggleChat = {
                        startService(Intent(this@FloatingBoosterOverlayService, FloatingChatOverlayService::class.java))
                    },
                    onClose = { stopSelf() }
                )
            }
        }

        windowManager.addView(overlayView, layoutParams)
    }

    private fun startForegroundNotification() {
        val channelId = "sparkboost_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Game Booster Overlay", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SparkBoost Active")
            .setContentText("Floating FPS & CPU optimizer running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        startForeground(101, notification)
    }

    override fun onDestroy() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        serviceScope.cancel()
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun FloatingBoosterHud(
    fps: Int,
    optimizer: Spark40Optimizer,
    onDrag: (Float, Float) -> Unit,
    onToggleChat: () -> Unit,
    onClose: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var targetFps by remember { mutableIntStateOf(60) }
    var qualityTier by remember { mutableStateOf("Smooth") }
    var cpuLoad by remember { mutableIntStateOf(42) }
    var tempC by remember { mutableFloatStateOf(37.2f) }
    var ramUsed by remember { mutableLongStateOf(4100L) }
    var ramTotal by remember { mutableLongStateOf(8192L) }
    var optimizeStatus by remember { mutableStateOf("Ready") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            val cores = optimizer.readHelioCpuCores()
            cpuLoad = cores.map { it.loadPercent }.average().toInt()
            tempC = optimizer.readSocTempCelsius()
            val (used, total) = optimizer.readRamUsageMb()
            ramUsed = used
            ramTotal = total
            delay(1000L)
        }
    }

    if (!expanded) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            color = Color(0xE611151E),
            modifier = Modifier
                .border(1.dp, Color(0x55FF6B1A), RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("$fps FPS", color = Color(0xFFFF6B1A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("${cpuLoad}% CPU", color = Color.White, fontSize = 11.sp)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .width(290.dp)
                .background(Color(0xF211151E), RoundedCornerShape(18.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SparkBoost HUD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row {
                    TextButton(onClick = { expanded = false }) { Text("Min", color = Color.Gray) }
                    TextButton(onClick = onClose) { Text("Exit", color = Color(0xFFEF4444)) }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "FPS: $fps  ·  CPU: $cpuLoad%  ·  Temp: ${"%.1f".format(tempC)}°C",
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp
            )
            Text(
                "RAM: $ramUsed / $ramTotal MB",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )

            Spacer(Modifier.height(10.dp))
            Text("FPS Cap: $targetFps  ·  Graphics: $qualityTier", color = Color.White, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf(60, 90, 120).forEach { cap ->
                    OutlinedButton(
                        onClick = { targetFps = cap },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("${cap}Hz", color = if (targetFps == cap) Color(0xFFFF6B1A) else Color.White, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    scope.launch {
                        val packages = listOf("com.afmobi.palmplay", "com.transsion.ahagames", "com.instagram.android", "com.zhiliaoapp.musically")
                        optimizer.runOneTapOptimize(packages).collect { step ->
                            optimizeStatus = if (step.progress >= 1f) {
                                "Freed ${step.ramFreedMb}MB RAM · ${step.cacheClearedMb}MB Cache"
                            } else {
                                "Optimizing ${(step.progress * 100).toInt()}%"
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B1A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Optimize Now", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            Text(optimizeStatus, color = Color(0xFF10B981), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))

            TextButton(onClick = onToggleChat, modifier = Modifier.align(Alignment.End)) {
                Text("Open Floating Chat", color = Color(0xFF38BDF8), fontSize = 12.sp)
            }
        }
    }
}
