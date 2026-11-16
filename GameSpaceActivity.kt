package com.sparkboost.tecno.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkboost.tecno.hardware.Spark40Optimizer
import com.sparkboost.tecno.overlay.FloatingBoosterOverlayService
import kotlinx.coroutines.launch

data class InstalledGame(val title: String, val packageName: String, val targetFps: Int)

class GameSpaceActivity : ComponentActivity() {

    private lateinit var optimizer: Spark40Optimizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        optimizer = Spark40Optimizer(this)

        setContent {
            val scope = rememberCoroutineScope()
            var optimizeProgress by remember { mutableFloatStateOf(0f) }
            var optimizeMessage by remember { mutableStateOf("TECNO Spark 40 Ready") }
            val games = remember {
                mutableStateListOf(
                    InstalledGame("PUBG Mobile", "com.tencent.ig", 60),
                    InstalledGame("Mobile Legends", "com.mobile.legends", 90),
                    InstalledGame("Call of Duty Mobile", "com.activision.callofduty.shooter", 60)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090B10))
                    .padding(20.dp)
            ) {
                Text("SparkBoost Game Space", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("TECNO SPARK 40 · Helio G-Series Optimizer", color = Color(0xFF94A3B8), fontSize = 13.sp)

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val selected = listOf("com.afmobi.palmplay", "com.transsion.ahagames", "com.instagram.android")
                            optimizer.runOneTapOptimize(selected).collect { step ->
                                optimizeProgress = step.progress
                                optimizeMessage = if (step.progress >= 1f) {
                                    "Freed ${step.ramFreedMb}MB RAM · Cleared ${step.cacheClearedMb}MB Cache (${step.essentialSkippedCount} system apps protected)"
                                } else {
                                    "${step.phase}: ${step.currentPackage}"
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B1A)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Optimize Now", fontWeight = FontWeight.SemiBold, color = Color.White)
                }

                if (optimizeProgress > 0f && optimizeProgress < 1f) {
                    LinearProgressIndicator(
                        progress = { optimizeProgress },
                        color = Color(0xFFFF6B1A),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
                Text(optimizeMessage, color = Color(0xFF10B981), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))

                Spacer(Modifier.height(20.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(games) { game ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF11151E), RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(game.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text("${game.packageName} · ${game.targetFps} FPS", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                            Button(onClick = { launchGameWithOverlay(game.packageName) }) {
                                Text("Play")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchGameWithOverlay(packageName: String) {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, FloatingBoosterOverlayService::class.java))
        packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
    }
}
