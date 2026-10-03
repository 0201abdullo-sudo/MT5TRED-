package com.goldoverlay.app

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var chartView: ChartView
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val priceHistory = mutableListOf<Double>()

    companion object {
        private const val CHANNEL_ID = "gold_overlay_channel"
        private const val NOTIFICATION_ID = 1
        private const val MAX_HISTORY = 200
        private const val UPDATE_INTERVAL_MS = 10_000L // 10 soniyada bir yangilanadi

        private const val FAST_PERIOD = 20
        private const val SLOW_PERIOD = 50
        private const val BB_PERIOD = 20
        private const val BB_DEVIATION = 2.0
        private const val RSI_PERIOD = 7
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        setupOverlay()
        startUpdating()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        chartView = ChartView(this)

        val overlayType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

        layoutParams = WindowManager.LayoutParams(
            600, // kenglik (px) - xohlasangiz moslashtiring
            260, // balandlik (px)
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 150
        }

        // Overlay'ni ushlab, erkin sudrab ko'chirish uchun
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        chartView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - touchX).toInt()
                    layoutParams.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(chartView, layoutParams)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(chartView, layoutParams)
    }

    private fun startUpdating() {
        serviceScope.launch {
            while (isActive) {
                val price = withContext(Dispatchers.IO) { PriceFetcher.fetchGoldPriceUsd() }
                if (price != null) {
                    priceHistory.add(price)
                    if (priceHistory.size > MAX_HISTORY) priceHistory.removeAt(0)
                    refreshChart(price)
                }
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun refreshChart(lastPrice: Double) {
        val fastSeries = Indicators.emaSeries(priceHistory, FAST_PERIOD)
        val slowSeries = Indicators.emaSeries(priceHistory, SLOW_PERIOD)
        val bb = Indicators.bollingerBands(priceHistory, BB_PERIOD, BB_DEVIATION)
        val signal = Indicators.combinedSignal(
            prices = priceHistory,
            fastPeriod = FAST_PERIOD,
            slowPeriod = SLOW_PERIOD,
            bbPeriod = BB_PERIOD,
            bbDeviation = BB_DEVIATION,
            rsiPeriod = RSI_PERIOD
        )

        // Grafikda oxirgi N nuqtani ko'rsatamiz (tiqilib qolmasligi uchun)
        val displayCount = 60
        val displayPrices = priceHistory.takeLast(displayCount)
        val displayFast = fastSeries.takeLast(displayCount)
        val displaySlow = slowSeries.takeLast(displayCount)

        chartView.updateData(
            prices = displayPrices,
            fastMA = displayFast,
            slowMA = displaySlow,
            bbUpper = bb?.upper,
            bbLower = bb?.lower,
            signal = signal,
            lastPrice = lastPrice
        )
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gold Overlay ishlamoqda")
            .setContentText("XAUUSD tahlil fon rejimida davom etmoqda")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gold Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (::chartView.isInitialized && ::windowManager.isInitialized) {
            try {
                windowManager.removeView(chartView)
            } catch (e: Exception) {
                // view allaqachon olib tashlangan bo'lishi mumkin
            }
        }
    }
}
