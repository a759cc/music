package com.anandnet.harmonymusic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.ryanheise.audioservice.AudioServiceActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : AudioServiceActivity() {
    private val CHANNEL = "com.anandnet.harmonymusic/island"
    private var methodChannel: MethodChannel? = null

    private val islandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DynamicIslandOverlayService.BROADCAST_CONTROL) {
                val action = intent.getStringExtra(DynamicIslandOverlayService.EXTRA_CONTROL_ACTION)
                if (action == "seekTo") {
                    val posMs = intent.getIntExtra(DynamicIslandOverlayService.EXTRA_SEEK_POSITION, 0)
                    methodChannel?.invokeMethod("seekTo", mapOf("positionMs" to posMs))
                } else if (action != null) {
                    methodChannel?.invokeMethod("mediaControl", mapOf("action" to action))
                }
            }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "checkPermission" -> {
                    result.success(Settings.canDrawOverlays(this))
                }
                "requestPermission" -> {
                    requestOverlayPermission()
                    result.success(null)
                }
                "showIsland" -> {
                    val playing = call.argument<Boolean>("isPlaying") ?: false
                    val title = call.argument<String>("title") ?: ""
                    val artist = call.argument<String>("artist") ?: ""
                    val artUri = call.argument<String>("artUri") ?: ""

                    if (!Settings.canDrawOverlays(this)) {
                        requestOverlayPermission()
                    } else {
                        val serviceIntent = Intent(this, DynamicIslandOverlayService::class.java).apply {
                            action = DynamicIslandOverlayService.ACTION_SHOW
                            putExtra(DynamicIslandOverlayService.EXTRA_IS_PLAYING, playing)
                            putExtra(DynamicIslandOverlayService.EXTRA_TITLE, title)
                            putExtra(DynamicIslandOverlayService.EXTRA_ARTIST, artist)
                            putExtra(DynamicIslandOverlayService.EXTRA_ART_URI, artUri)
                        }
                        startService(serviceIntent)
                    }
                    result.success(null)
                }
                "hideIsland" -> {
                    val serviceIntent = Intent(this, DynamicIslandOverlayService::class.java).apply {
                        action = DynamicIslandOverlayService.ACTION_HIDE
                    }
                    startService(serviceIntent)
                    result.success(null)
                }
                "updateState" -> {
                    val serviceIntent = Intent(this, DynamicIslandOverlayService::class.java).apply {
                        action = DynamicIslandOverlayService.ACTION_UPDATE
                        if (call.hasArgument("isPlaying")) {
                            putExtra(DynamicIslandOverlayService.EXTRA_IS_PLAYING, call.argument<Boolean>("isPlaying"))
                        }
                        if (call.hasArgument("positionMs")) {
                            putExtra(DynamicIslandOverlayService.EXTRA_POSITION_MS, call.argument<Int>("positionMs"))
                        }
                        if (call.hasArgument("durationMs")) {
                            putExtra(DynamicIslandOverlayService.EXTRA_DURATION_MS, call.argument<Int>("durationMs"))
                        }
                        if (call.hasArgument("title")) {
                            putExtra(DynamicIslandOverlayService.EXTRA_TITLE, call.argument<String>("title"))
                        }
                        if (call.hasArgument("artist")) {
                            putExtra(DynamicIslandOverlayService.EXTRA_ARTIST, call.argument<String>("artist"))
                        }
                        if (call.hasArgument("artUri")) {
                            putExtra(DynamicIslandOverlayService.EXTRA_ART_URI, call.argument<String>("artUri"))
                        }
                    }
                    startService(serviceIntent)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }

        // Register BroadcastReceiver for media controls from DynamicIslandOverlayService
        val filter = IntentFilter(DynamicIslandOverlayService.BROADCAST_CONTROL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(islandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(islandReceiver, filter)
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(islandReceiver)
        } catch (e: Exception) {}
    }
}
