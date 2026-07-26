package com.anandnet.harmonymusic

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import com.ryanheise.audioservice.AudioServiceActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
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
    private var currentArtUri = ""
    private var currentPositionMs = 0
    private var currentDurationMs = 0
    private var isPlaying = false
    private var cachedBitmap: Bitmap? = null

    // Views
    private var miniContainer: LinearLayout? = null
    private var expandedContainer: LinearLayout? = null
    private var waveformViewMini: WaveformView? = null
    private var waveformViewExpanded: WaveformView? = null

    private var miniDiscImageView: ImageView? = null
    private var expandedArtImageView: ImageView? = null
    private var titleTextView: TextView? = null
    private var artistTextView: TextView? = null
    private var posTextView: TextView? = null
    private var durTextView: TextView? = null
    private var seekBar: SeekBar? = null
    private var playPauseButton: ImageView? = null
    private var islandBackground: GradientDrawable? = null

    private val executor = Executors.newSingleThreadExecutor()

    // 动态居中适配与尺寸参数（微调缩小）
    private var MINI_WIDTH_DP = 138f
    private var MINI_HEIGHT_DP = 30f
    private var MINI_RADIUS_DP = 15f

    private val EXPANDED_WIDTH_DP = 330f
    private val EXPANDED_HEIGHT_DP = 160f
    private val EXPANDED_RADIUS_DP = 26f

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

                    runOnUiThread {
                        if (!Settings.canDrawOverlays(this)) {
                            requestOverlayPermission()
                        } else {
                            isPlaying = playing
                            currentTitle = title
                            currentArtist = artist
                            if (currentArtUri != artUri) {
                                currentArtUri = artUri
                                loadAlbumArt(artUri)
                            }
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
                    val artUri = call.argument<String>("artUri")

                    runOnUiThread {
                        if (playing != null) isPlaying = playing
                        if (positionMs != null) currentPositionMs = positionMs
                        if (durationMs != null) currentDurationMs = durationMs
                        if (title != null) currentTitle = title
                        if (artist != null) currentArtist = artist
                        if (artUri != null && currentArtUri != artUri) {
                            currentArtUri = artUri
                            loadAlbumArt(artUri)
                        }
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

    /**
     * 动态获取当前设备系统状态栏高度 (Status Bar Height)
     * 从而将灵动岛完美包裹在顶部摄像头挖孔中央，不受静态硬编码限制
     */
    private fun getStatusBarHeightPx(): Int {
        var statusBarHeight = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = resources.getDimensionPixelSize(resourceId)
        }
        if (statusBarHeight <= 0) {
            statusBarHeight = dpToPx(28f)
        }
        return statusBarHeight
    }

    private fun formatTime(ms: Int): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun loadAlbumArt(artUri: String) {
        if (artUri.isEmpty()) {
            cachedBitmap = null
            updateArtViews()
            return
        }
        executor.execute {
            try {
                val bitmap: Bitmap? = if (artUri.startsWith("http://") || artUri.startsWith("https://")) {
                    val url = URL(artUri)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.doInput = true
                    conn.connect()
                    BitmapFactory.decodeStream(conn.inputStream)
                } else if (artUri.startsWith("file://") || artUri.startsWith("/")) {
                    val path = if (artUri.startsWith("file://")) artUri.substring(7) else artUri
                    BitmapFactory.decodeFile(path)
                } else {
                    null
                }
                cachedBitmap = bitmap
                runOnUiThread { updateArtViews() }
            } catch (e: Exception) {
                cachedBitmap = null
                runOnUiThread { updateArtViews() }
            }
        }
    }

    private fun updateArtViews() {
        val bmp = cachedBitmap
        if (bmp != null) {
            val circularBmp = getCircularBitmap(bmp)
            miniDiscImageView?.setImageBitmap(circularBmp)

            val roundedBmp = getRoundedCornerBitmap(bmp, dpToPx(8f))
            expandedArtImageView?.setImageBitmap(roundedBmp)
        } else {
            miniDiscImageView?.setImageBitmap(null)
            expandedArtImageView?.setImageBitmap(null)
        }
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = Math.min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, pixels: Int): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawRoundRect(rectF, pixels.toFloat(), pixels.toFloat(), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
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
            setColor(Color.parseColor("#0F1015"))
            cornerRadius = dpToPx(MINI_RADIUS_DP).toFloat()
            setStroke(dpToPx(0.8f), Color.parseColor("#33FFFFFF"))
        }
        islandView?.background = islandBackground

        // --- MINI CONTAINER (收起态: 138dp x 30dp) ---
        miniContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(5f), dpToPx(3f), dpToPx(7f), dpToPx(3f))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val discSize = dpToPx(24f)
        miniDiscImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1A1A1A"))
                setStroke(dpToPx(1f), Color.parseColor("#44FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(discSize, discSize)
        }

        val miniSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        waveformViewMini = WaveformView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(20f), dpToPx(14f))
        }

        miniContainer?.addView(miniDiscImageView)
        miniContainer?.addView(miniSpacer)
        miniContainer?.addView(waveformViewMini)

        // --- EXPANDED CONTAINER (展开态卡片: 330dp x 160dp) ---
        expandedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }

        // Top Row: Album Art + Song Title/Artist + Waveform
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val artSize = dpToPx(44f)
        expandedArtImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(8f).toFloat()
                setColor(Color.parseColor("#1A1A1A"))
                setStroke(dpToPx(1f), Color.parseColor("#33FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(artSize, artSize).apply {
                rightMargin = dpToPx(10f)
            }
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        titleTextView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        artistTextView = TextView(this).apply {
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 12f
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        textContainer.addView(titleTextView)
        textContainer.addView(artistTextView)

        waveformViewExpanded = WaveformView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(20f), dpToPx(14f))
        }

        topRow.addView(expandedArtImageView)
        topRow.addView(textContainer)
        topRow.addView(waveformViewExpanded)

        // Middle Row: Position - SeekBar - Duration
        val progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(10f)
                bottomMargin = dpToPx(6f)
            }
        }

        posTextView = TextView(this).apply {
            setTextColor(Color.parseColor("#8E8E93"))
            textSize = 11f
            text = "00:00"
        }

        seekBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dpToPx(6f)
                rightMargin = dpToPx(6f)
            }
            progressDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#40FFFFFF"))
                cornerRadius = dpToPx(2f).toFloat()
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

        durTextView = TextView(this).apply {
            setTextColor(Color.parseColor("#8E8E93"))
            textSize = 11f
            text = "00:00"
        }

        progressRow.addView(posTextView)
        progressRow.addView(seekBar)
        progressRow.addView(durTextView)

        // Bottom Row: Media Controls (Prev, Play/Pause circle, Next, App Switcher / Collapse)
        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(4f)
            }
        }

        val prevBtn = TextView(this).apply {
            text = "⏮"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                methodChannel?.invokeMethod("mediaControl", mapOf("action" to "prev"))
            }
        }

        val playBtnSize = dpToPx(40f)
        val playBtnWrapper = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        playPauseButton = ImageView(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            setPadding(dpToPx(9f), dpToPx(9f), dpToPx(9f), dpToPx(9f))
            setColorFilter(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(playBtnSize, playBtnSize, Gravity.CENTER)
            setOnClickListener {
                methodChannel?.invokeMethod("mediaControl", mapOf("action" to "playPause"))
            }
        }
        playBtnWrapper.addView(playPauseButton)

        val nextBtn = TextView(this).apply {
            text = "⏭"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                methodChannel?.invokeMethod("mediaControl", mapOf("action" to "next"))
            }
        }

        val collapseBtn = TextView(this).apply {
            text = "🔼"
            setTextColor(Color.parseColor("#A0A0A0"))
            textSize = 18f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                collapseIsland()
            }
        }

        controlsRow.addView(prevBtn)
        controlsRow.addView(playBtnWrapper)
        controlsRow.addView(nextBtn)
        controlsRow.addView(collapseBtn)

        expandedContainer?.addView(topRow)
        expandedContainer?.addView(progressRow)
        expandedContainer?.addView(controlsRow)

        islandView?.addView(miniContainer)
        islandView?.addView(expandedContainer)

        // 动态垂直偏移逻辑：计算当前设备状态栏高度，实现绝佳居中
        val statusBarHeightPx = getStatusBarHeightPx()
        val miniHeightPx = dpToPx(MINI_HEIGHT_DP)
        val yOffsetPx = Math.max(dpToPx(2f), (statusBarHeightPx - miniHeightPx) / 2)

        wmParams = WindowManager.LayoutParams(
            dpToPx(MINI_WIDTH_DP),
            miniHeightPx,
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
            y = yOffsetPx // 完美对齐摄像头与状态栏中心
        }

        // --- 核心修复：彻底解决点击无响应问题 ---
        // 使用 Android 原生防抖动 Standard ClickListener 和 LongClickListener
        islandView?.setOnClickListener {
            if (!isExpanded) {
                expandIsland()
            } else {
                collapseIsland()
            }
        }

        islandView?.setOnLongClickListener {
            val intent = Intent(this@MainActivity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            true
        }

        try {
            windowManager?.addView(islandView, wmParams)
            isIslandShowing = true
            updateUI()
            updateArtViews()
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
        posTextView?.text = formatTime(currentPositionMs)
        durTextView?.text = formatTime(currentDurationMs)

        if (isPlaying) {
            playPauseButton?.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            playPauseButton?.setImageResource(android.R.drawable.ic_media_play)
        }

        waveformViewMini?.setPlaying(isPlaying)
        waveformViewExpanded?.setPlaying(isPlaying)

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

    // WaveformView
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
        private val minHeight = 0.25f
        private val random = java.util.Random()

        private val updateTask = object : Runnable {
            override fun run() {
                if (isPlaying) {
                    for (i in 0 until barCount) {
                        if (Math.abs(heights[i] - targetHeights[i]) < 0.1f) {
                            targetHeights[i] = minHeight + random.nextFloat() * (1f - minHeight)
                        }
                        heights[i] += (targetHeights[i] - heights[i]) * 0.25f
                    }
                    invalidate()
                    postDelayed(this, 40)
                } else {
                    var animating = false
                    for (i in 0 until barCount) {
                        if (heights[i] > minHeight + 0.05f) {
                            heights[i] += (minHeight - heights[i]) * 0.25f
                            animating = true
                        } else {
                            heights[i] = minHeight
                        }
                    }
                    invalidate()
                    if (animating) {
                        postDelayed(this, 40)
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
