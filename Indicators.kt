package com.goldoverlay.app

import kotlin.math.sqrt

object Indicators {

    // Oddiy harakatlanuvchi o'rtacha (SMA)
    fun sma(data: List<Double>, period: Int): Double? {
        if (data.size < period) return null
        return data.takeLast(period).average()
    }

    // Eksponensial harakatlanuvchi o'rtacha (EMA) - butun seriya uchun
    fun emaSeries(data: List<Double>, period: Int): List<Double> {
        if (data.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1)
        val result = mutableListOf<Double>()
        var prevEma = data.first()
        result.add(prevEma)
        for (i in 1 until data.size) {
            val ema = data[i] * k + prevEma * (1 - k)
            result.add(ema)
            prevEma = ema
        }
        return result
    }

    data class BollingerBands(val upper: Double, val middle: Double, val lower: Double)

    fun bollingerBands(data: List<Double>, period: Int, deviation: Double): BollingerBands? {
        if (data.size < period) return null
        val window = data.takeLast(period)
        val mean = window.average()
        val variance = window.sumOf { (it - mean) * (it - mean) } / window.size
        val sd = sqrt(variance)
        return BollingerBands(
            upper = mean + deviation * sd,
            middle = mean,
            lower = mean - deviation * sd
        )
    }

    // RSI (Relative Strength Index)
    fun rsi(data: List<Double>, period: Int): Double? {
        if (data.size < period + 1) return null
        val recent = data.takeLast(period + 1)
        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1 until recent.size) {
            val diff = recent[i] - recent[i - 1]
            if (diff >= 0) gainSum += diff else lossSum -= diff
        }
        val avgGain = gainSum / period
        val avgLoss = lossSum / period
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100 - (100 / (1 + rs))
    }

    enum class Signal { BUY, SELL, NONE }

    // MA crossover + Bollinger/RSI birlashtirilgan signal mantiqi
    fun combinedSignal(
        prices: List<Double>,
        fastPeriod: Int = 20,
        slowPeriod: Int = 50,
        bbPeriod: Int = 20,
        bbDeviation: Double = 2.0,
        rsiPeriod: Int = 7,
        rsiOverbought: Double = 75.0,
        rsiOversold: Double = 25.0
    ): Signal {
        if (prices.size < slowPeriod + 2) return Signal.NONE

        val fastEma = emaSeries(prices, fastPeriod)
        val slowEma = emaSeries(prices, slowPeriod)
        val bb = bollingerBands(prices, bbPeriod, bbDeviation) ?: return Signal.NONE
        val rsiNow = rsi(prices, rsiPeriod) ?: return Signal.NONE
        val lastPrice = prices.last()

        val fastNow = fastEma.last()
        val slowNow = slowEma.last()
        val fastPrev = fastEma[fastEma.size - 2]
        val slowPrev = slowEma[slowEma.size - 2]

        val maCrossUp = fastPrev <= slowPrev && fastNow > slowNow
        val maCrossDown = fastPrev >= slowPrev && fastNow < slowNow

        val bbRsiBuy = lastPrice <= bb.lower && rsiNow <= rsiOversold
        val bbRsiSell = lastPrice >= bb.upper && rsiNow >= rsiOverbought

        // Ikkala strategiyadan kamida bittasi mos signal bersa, signal qaytaramiz.
        // Agar ikkalasi ham qarama-qarshi signal bersa — NONE (nizo holati).
        val buyVotes = (if (maCrossUp) 1 else 0) + (if (bbRsiBuy) 1 else 0)
        val sellVotes = (if (maCrossDown) 1 else 0) + (if (bbRsiSell) 1 else 0)

        return when {
            buyVotes > 0 && sellVotes == 0 -> Signal.BUY
            sellVotes > 0 && buyVotes == 0 -> Signal.SELL
            else -> Signal.NONE
        }
    }
}
