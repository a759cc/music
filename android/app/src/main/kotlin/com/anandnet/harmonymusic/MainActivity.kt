package com.anandnet.harmonymusic

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.ryanheise.audioservice.AudioServiceActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlin.math.roundToInt

class MainActivity : AudioServiceActivity() {
    private val CHANNEL = "com.anandnet.harmonymusic/island"
    private var methodChannel: MethodChannel? = null

    private var windowManager: WindowManager? = null
    private var islandView: FrameLayout? = null
    private var wmParams: WindowManager.LayoutParams? = null

    private var isExpanded = false
    private var isIslandShowing = false

    // State
    private var currentTitle = "Unknown"
    private var currentArtist = "Unknown"
    private var currentPositionMs = 0
    private var currentDurationMs = 0
    private var isPlaying = false

    // Views
    private var miniContainer: LinearLayout? = null
    private var expandedContainer: LinearLayout? = null
    private var waveformView: WaveformView? = null
    private var titleTextView: TextView? = null
    private var artistTextView: TextView? = null
    private var seekBar: SeekBar? = null
    private var playPauseButton: TextView? = null
    private var islandBackground: GradientDrawable? = null

    // Dimensions
    private val MINI_WIDTH_DP = 76f
    private val MINI_HEIGHT_DP = 24f
    private val MINI_RADIUS_DP = 12f

    private val EXPANDED_WIDTH_DP = 280f
    private val EXPANDED_HEIGHT_DP = 120f
    private val EXPANDED_RADIUS_DP = 24f

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
                    
                    runOnUiThread {
                        if (!Settings.canDrawOverlays(this)) {
                            requestOverlayPermission()
                        } else {
                            isPlaying = playing
                            currentTitle = title
                            currentArtist = artist
                            showIsland()
                        }
                    }
                    result.success(null)
                }
                "hideIsland" -> {
                    runOnUiThread { hideIsland() }
                    result.success(null)
                }
                "updateState" -> {
                    val playing = call.argument<Boolean>("isPlaying")
                    val positionMs = call.argument<Int>("positionMs")
                    val durationMs = call.argument<Int>("durationMs")
                    val title = call.argument<String>("title")
                    val artist = call.argument<String>("artist")
                    
                    runOnUiThread {
                        if (playing != null) isPlaying = playing
                        if (positionMs != null) currentPositionMs = positionMs
                        if (durationMs != null) currentDurationMs = durationMs
                        if (title != null) currentTitle = title
                        if (artist != null) currentArtist = artist
                        updateUI()
                    }
                    result.success(null)
                }
                else -> result.notImplemented()
            }
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

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).roundToInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showIsland() {
        if (isIslandShowing) {
            updateUI()
            return
        }
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        islandView = FrameLayout(this)
        islandBackground = GradientDrawable().apply {
            setColor(Color.parseColor("#000000"))
            cornerRadius = dpToPx(MINI_RADIUS_DP).toFloat()
            setStroke(dpToPx(1f), Color.parseColor("#22FFFFFF"))
        }
        islandView?.background = islandBackground

        // Setup Mini Container
        miniContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(4f), 0, dpToPx(8f), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val miniDisc = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1A1A1A"))
                setStroke(dpToPx(1f), Color.parseColor("#33FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(18f), dpToPx(18f))
        }

        // Spacer to push waveform to the right
        val miniSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        waveformView = WaveformView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(16f), dpToPx(12f))
        }

        miniContainer?.addView(miniDisc)
        miniContainer?.addView(miniSpacer)
        miniContainer?.addView(waveformView)


        // Setup Expanded Container
        expandedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val expandedDisc = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1A1A1A"))
                setStroke(dpToPx(1f), Color.parseColor("#33FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(36f), dpToPx(36f)).apply {
                rightMargin = dpToPx(12f)
            }
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        titleTextView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            isSingleLine = true
        }

        artistTextView = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 12f
            isSingleLine = true
        }

        textContainer.addView(titleTextView)
        textContainer.addView(artistTextView)

        topRow.addView(expandedDisc)
        topRow.addView(textContainer)

        seekBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12f)
                bottomMargin = dpToPx(12f)
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        methodChannel?.invokeMethod("seekTo", mapOf("positionMs" to progress))
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val prevBtn = TextView(this).apply {
            text = "◀"
            setTextColor(Color.WHITE)
            textSize = 24f
            setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
            setOnClickListener {
                methodChannel?.invokeMethod("mediaControl", mapOf("action" to "prev"))
            }
        }

        playPauseButton = TextView(this).apply {
            text = "⏸"
            setTextColor(Color.WHITE)
            textSize = 24f
            setPadding(dpToPx(24f), 0, dpToPx(24f), 0)
            setOnClickListener {
                methodChannel?.invokeMethod("mediaControl", mapOf("action" to "playPause"))
            }
        }

        val nextBtn = TextView(this).apply {
            text = "▶"
            setTextColor(Color.WHITE)
            textSize = 24f
            setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
            setOnClickListener {
                methodChannel?.invokeMethod("mediaControl", mapOf("action" to "next"))
            }
        }

        controlsRow.addView(prevBtn)
        controlsRow.addView(playPauseButton)
        controlsRow.addView(nextBtn)

        expandedContainer?.addView(topRow)
        expandedContainer?.addView(seekBar)
        expandedContainer?.addView(controlsRow)

        islandView?.addView(miniContainer)
        islandView?.addView(expandedContainer)

        wmParams = WindowManager.LayoutParams(
            dpToPx(MINI_WIDTH_DP),
            dpToPx(MINI_HEIGHT_DP),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dpToPx(6f) // Status bar internal padding
        }

        // Gesture handling
        val gestureHandler = Handler(Looper.getMainLooper())
        var longPressRunnable: Runnable? = null
        var downX = 0f
        var downY = 0f
        var isClickValid = false

        islandView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    isClickValid = true
                    longPressRunnable = Runnable {
                        if (isClickValid) {
                            // Long press action
                            isClickValid = false
                            val intent = Intent(this@MainActivity, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                        }
                    }
                    gestureHandler.postDelayed(longPressRunnable!!, 500)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(event.rawX - downX) > dpToPx(10f) || 
                        Math.abs(event.rawY - downY) > dpToPx(10f)) {
                        isClickValid = false
                        longPressRunnable?.let { gestureHandler.removeCallbacks(it) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let { gestureHandler.removeCallbacks(it) }
                    if (isClickValid) {
                        // Click action
                        if (!isExpanded) {
                            expandIsland()
                        } else {
                            collapseIsland()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { gestureHandler.removeCallbacks(it) }
                    isClickValid = false
                    true
                }
                else -> false
            }
        }

        // Prevent controls from collapsing the island
        controlsRow.setOnTouchListener { _, _ -> true }
        seekBar?.setOnTouchListener { _, event ->
            seekBar?.onTouchEvent(event)
            true
        }

        try {
            windowManager?.addView(islandView, wmParams)
            isIslandShowing = true
            updateUI()
        } catch (e: Exception) {
            Log.e("Island", "Error adding overlay", e)
        }
    }

    private fun hideIsland() {
        if (!isIslandShowing) return
        try {
            windowManager?.removeView(islandView)
        } catch (e: Exception) {
            Log.e("Island", "Error removing overlay", e)
        }
        isIslandShowing = false
        islandView = null
        wmParams = null
    }

    private fun updateUI() {
        if (!isIslandShowing) return

        titleTextView?.text = currentTitle
        artistTextView?.text = currentArtist
        
        playPauseButton?.text = if (isPlaying) "⏸" else "▶"
        waveformView?.setPlaying(isPlaying)

        seekBar?.max = currentDurationMs
        seekBar?.progress = currentPositionMs
    }

    private fun expandIsland() {
        if (isExpanded) return
        isExpanded = true

        miniContainer?.visibility = View.GONE
        expandedContainer?.visibility = View.VISIBLE
        expandedContainer?.alpha = 0f
        expandedContainer?.animate()?.alpha(1f)?.setDuration(150)?.start()

        animateIslandSize(
            dpToPx(MINI_WIDTH_DP), dpToPx(EXPANDED_WIDTH_DP),
            dpToPx(MINI_HEIGHT_DP), dpToPx(EXPANDED_HEIGHT_DP),
            dpToPx(MINI_RADIUS_DP), dpToPx(EXPANDED_RADIUS_DP)
        )
    }

    private fun collapseIsland() {
        if (!isExpanded) return
        isExpanded = false

        expandedContainer?.visibility = View.GONE
        miniContainer?.visibility = View.VISIBLE
        miniContainer?.alpha = 0f
        miniContainer?.animate()?.alpha(1f)?.setDuration(150)?.start()

        animateIslandSize(
            dpToPx(EXPANDED_WIDTH_DP), dpToPx(MINI_WIDTH_DP),
            dpToPx(EXPANDED_HEIGHT_DP), dpToPx(MINI_HEIGHT_DP),
            dpToPx(EXPANDED_RADIUS_DP), dpToPx(MINI_RADIUS_DP)
        )
    }

    private fun animateIslandSize(startW: Int, endW: Int, startH: Int, endH: Int, startR: Int, endR: Int) {
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                val width = startW + ((endW - startW) * fraction).toInt()
                val height = startH + ((endH - startH) * fraction).toInt()
                val radius = startR + ((endR - startR) * fraction)

                islandBackground?.cornerRadius = radius
                wmParams?.width = width
                wmParams?.height = height
                
                try {
                    windowManager?.updateViewLayout(islandView, wmParams)
                } catch (e: Exception) {
                    Log.e("Island", "Error updating layout", e)
                }
            }
        }
        animator.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideIsland()
    }

    // Custom WaveformView implementation
    class WaveformView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            strokeCap = Paint.Cap.ROUND
        }

        private val barCount = 4
        private val heights = FloatArray(barCount)
        private val targetHeights = FloatArray(barCount)
        private var isPlaying = false
        private val minHeight = 0.2f
        private val random = java.util.Random()

        private val updateTask = object : Runnable {
            override fun run() {
                if (isPlaying) {
                    for (i in 0 until barCount) {
                        if (Math.abs(heights[i] - targetHeights[i]) < 0.1f) {
                            targetHeights[i] = minHeight + random.nextFloat() * (1f - minHeight)
                        }
                        // Interpolate towards target
                        heights[i] += (targetHeights[i] - heights[i]) * 0.2f
                    }
                    invalidate()
                    postDelayed(this, 50)
                } else {
                    var animating = false
                    for (i in 0 until barCount) {
                        if (heights[i] > minHeight + 0.05f) {
                            heights[i] += (minHeight - heights[i]) * 0.2f
                            animating = true
                        } else {
                            heights[i] = minHeight
                        }
                    }
                    invalidate()
                    if (animating) {
                        postDelayed(this, 50)
                    }
                }
            }
        }

        init {
            for (i in 0 until barCount) {
                heights[i] = minHeight
                targetHeights[i] = minHeight
            }
        }

        fun setPlaying(playing: Boolean) {
            if (this.isPlaying != playing) {
                this.isPlaying = playing
                removeCallbacks(updateTask)
                post(updateTask)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            
            val gap = w / (barCount * 2)
            val barWidth = (w - (barCount - 1) * gap) / barCount
            
            paint.strokeWidth = barWidth

            for (i in 0 until barCount) {
                val x = i * (barWidth + gap) + barWidth / 2
                val barHeight = h * heights[i]
                val top = (h - barHeight) / 2
                val bottom = top + barHeight
                canvas.drawLine(x, top, x, bottom, paint)
            }
        }
    }
}
