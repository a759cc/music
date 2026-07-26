package com.anandnet.harmonymusic

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import com.ryanheise.audioservice.AudioServiceActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : AudioServiceActivity() {
    private val CHANNEL = "com.anandnet.harmonymusic/island"
    private var windowManager: WindowManager? = null
    private var islandOverlayView: View? = null
    private var waveformView: WaveformView? = null
    private var isIslandShowing = false

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "checkPermission" -> {
                    result.success(checkOverlayPermission())
                }
                "requestPermission" -> {
                    requestOverlayPermission()
                    result.success(true)
                }
                "showIsland" -> {
                    val isPlaying = call.argument<Boolean>("isPlaying") ?: true
                    showDynamicIsland(isPlaying)
                    result.success(true)
                }
                "hideIsland" -> {
                    hideDynamicIsland()
                    result.success(true)
                }
                "updateState" -> {
                    val isPlaying = call.argument<Boolean>("isPlaying") ?: false
                    updateDynamicIsland(isPlaying)
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun showDynamicIsland(isPlaying: Boolean) {
        if (!checkOverlayPermission()) {
            requestOverlayPermission()
            return
        }

        runOnUiThread {
            if (isIslandShowing && islandOverlayView != null) {
                updateDynamicIsland(isPlaying)
                return@runOnUiThread
            }

            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val density = resources.displayMetrics.density
            val widthPx = (148 * density).toInt()
            val heightPx = (34 * density).toInt()

            val wmParams = WindowManager.LayoutParams(
                widthPx,
                heightPx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (4 * density).toInt()
            }

            val capsuleContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((6 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())

                background = GradientDrawable().apply {
                    setColor(Color.BLACK)
                    cornerRadius = 17 * density
                    setStroke((0.8 * density).toInt(), Color.parseColor("#33FFFFFF"))
                }
            }

            // 左侧：黑胶唱片微缩图
            val discSize = (24 * density).toInt()
            val discView = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1A1A1A"))
                    setStroke((1 * density).toInt(), Color.parseColor("#44FFFFFF"))
                }
            }
            capsuleContainer.addView(discView, LinearLayout.LayoutParams(discSize, discSize).apply {
                marginEnd = (8 * density).toInt()
            })

            // 中间弹性占位
            val spacer = View(this)
            capsuleContainer.addView(spacer, LinearLayout.LayoutParams(0, 1, 1.0f))

            // 右侧：白色跳动音浪
            val waveWidth = (22 * density).toInt()
            val waveHeight = (16 * density).toInt()
            waveformView = WaveformView(this).apply {
                setPlaying(isPlaying)
            }
            capsuleContainer.addView(waveformView, LinearLayout.LayoutParams(waveWidth, waveHeight))

            // 点击胶囊回到应用
            capsuleContainer.setOnClickListener {
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }

            islandOverlayView = capsuleContainer
            try {
                windowManager?.addView(islandOverlayView, wmParams)
                isIslandShowing = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateDynamicIsland(isPlaying: Boolean) {
        runOnUiThread {
            waveformView?.setPlaying(isPlaying)
        }
    }

    private fun hideDynamicIsland() {
        runOnUiThread {
            if (isIslandShowing && islandOverlayView != null) {
                try {
                    windowManager?.removeView(islandOverlayView)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                islandOverlayView = null
                waveformView = null
                isIslandShowing = false
            }
        }
    }
}

// 极简纯白动态跳动音浪波形 Custom View
class WaveformView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var isPlaying = false

    init {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
        }
    }

    fun setPlaying(playing: Boolean) {
        if (isPlaying == playing) return
        isPlaying = playing
        if (isPlaying) {
            if (animator?.isStarted != true) {
                animator?.start()
            }
        } else {
            animator?.cancel()
            progress = 0f
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val barWidth = 2.5f * density
        val space = 2.0f * density
        val count = 4
        val totalWidth = count * barWidth + (count - 1) * space
        val startX = (width - totalWidth) / 2f

        val heights = if (isPlaying) {
            floatArrayOf(
                0.35f + 0.65f * sin(progress * Math.PI).toFloat(),
                0.85f - 0.65f * abs(cos(progress * Math.PI * 1.4).toFloat()),
                0.45f + 0.55f * sin(progress * Math.PI * 1.8).toFloat(),
                0.25f + 0.75f * cos(progress * Math.PI).toFloat()
            )
        } else {
            floatArrayOf(0.3f, 0.3f, 0.3f, 0.3f)
        }

        for (i in 0 until count) {
            val x = startX + i * (barWidth + space)
            val currentHeight = Math.max(3.5f * density, height * heights[i])
            val y = (height - currentHeight) / 2f

            val rect = RectF(x, y, x + barWidth, y + currentHeight)
            canvas.drawRoundRect(rect, 1.5f * density, 1.5f * density, paint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
