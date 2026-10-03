package com.goldoverlay.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class ChartView(context: Context) : View(context) {

    private var prices: List<Double> = emptyList()
    private var fastMA: List<Double> = emptyList()
    private var slowMA: List<Double> = emptyList()
    private var bbUpper: Double? = null
    private var bbLower: Double? = null
    private var signal: Indicators.Signal = Indicators.Signal.NONE
    private var lastPriceText: String = "--"

    private val bgPaint = Paint().apply {
        color = Color.argb(210, 20, 20, 25)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.argb(255, 90, 90, 100)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val pricePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val fastMAPaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 2.5f
        isAntiAlias = true
    }
    private val slowMAPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 2.5f
        isAntiAlias = true
    }
    private val bbPaint = Paint().apply {
        color = Color.argb(150, 150, 150, 255)
        strokeWidth = 1.5f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        isAntiAlias = true
    }
    private val buyPaint = Paint().apply {
        color = Color.parseColor("#00C853")
        textSize = 28f
        isAntiAlias = true
        isFakeBoldText = true
    }
    private val sellPaint = Paint().apply {
        color = Color.parseColor("#FF1744")
        textSize = 28f
        isAntiAlias = true
        isFakeBoldText = true
    }

    fun updateData(
        prices: List<Double>,
        fastMA: List<Double>,
        slowMA: List<Double>,
        bbUpper: Double?,
        bbLower: Double?,
        signal: Indicators.Signal,
        lastPrice: Double?
    ) {
        this.prices = prices
        this.fastMA = fastMA
        this.slowMA = slowMA
        this.bbUpper = bbUpper
        this.bbLower = bbLower
        this.signal = signal
        this.lastPriceText = lastPrice?.let { String.format("%.2f", it) } ?: "--"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Fon va chegara
        canvas.drawRoundRect(0f, 0f, w, h, 16f, 16f, bgPaint)
        canvas.drawRoundRect(1f, 1f, w - 1f, h - 1f, 16f, 16f, borderPaint)

        // Sarlavha va narx
        canvas.drawText("XAUUSD  $lastPriceText", 16f, 28f, textPaint)

        if (prices.size >= 2) {
            val chartTop = 40f
            val chartBottom = h - 36f
            val chartLeft = 12f
            val chartRight = w - 12f

            val allValues = mutableListOf<Double>()
            allValues.addAll(prices)
            bbUpper?.let { allValues.add(it) }
            bbLower?.let { allValues.add(it) }

            val maxV = allValues.max()
            val minV = allValues.min()
            val range = (maxV - minV).let { if (it == 0.0) 1.0 else it }

            fun yFor(v: Double): Float {
                val ratio = (v - minV) / range
                return chartBottom - (ratio * (chartBottom - chartTop)).toFloat()
            }

            fun xFor(i: Int, total: Int): Float {
                if (total <= 1) return chartLeft
                return chartLeft + (chartRight - chartLeft) * i / (total - 1)
            }

            // Bollinger chiziqlari (gorizontal, oxirgi qiymat asosida)
            bbUpper?.let {
                val y = yFor(it)
                canvas.drawLine(chartLeft, y, chartRight, y, bbPaint)
            }
            bbLower?.let {
                val y = yFor(it)
                canvas.drawLine(chartLeft, y, chartRight, y, bbPaint)
            }

            // Narx chizig'i
            drawSeries(canvas, prices, ::xFor, ::yFor, pricePaint)
            // MA chiziqlari
            if (fastMA.size >= 2) drawSeries(canvas, fastMA, ::xFor, ::yFor, fastMAPaint)
            if (slowMA.size >= 2) drawSeries(canvas, slowMA, ::xFor, ::yFor, slowMAPaint)
        }

        // BUY/SELL belgisi
        val signalText = when (signal) {
            Indicators.Signal.BUY -> "▲ BUY"
            Indicators.Signal.SELL -> "▼ SELL"
            Indicators.Signal.NONE -> "… kutilmoqda"
        }
        val paint = when (signal) {
            Indicators.Signal.BUY -> buyPaint
            Indicators.Signal.SELL -> sellPaint
            Indicators.Signal.NONE -> textPaint
        }
        canvas.drawText(signalText, 16f, h - 10f, paint)
    }

    private fun drawSeries(
        canvas: Canvas,
        data: List<Double>,
        xFor: (Int, Int) -> Float,
        yFor: (Double) -> Float,
        paint: Paint
    ) {
        for (i in 0 until data.size - 1) {
            val x1 = xFor(i, data.size)
            val y1 = yFor(data[i])
            val x2 = xFor(i + 1, data.size)
            val y2 = yFor(data[i + 1])
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
    }
}
