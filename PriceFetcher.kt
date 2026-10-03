package com.goldoverlay.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Jonli XAUUSD (Oltin/Dollar) narxini olib keladi.
 *
 * ESLATMA: Pastdagi manba (goldprice.org'ning ochiq, kalitsiz endpointi) ko'p ochiq
 * loyihalarda ishlatiladi, lekin har qanday kalitsiz ochiq API kabi u o'zgarishi yoki
 * bloklanishi mumkin. Agar ishlamay qolsa, RapidAPI yoki goldapi.io'dan bepul API
 * kalit olib, BASE_URL va parsePrice() funksiyasini shunga moslang.
 */
object PriceFetcher {

    private const val PRIMARY_URL = "https://data-asg.goldprice.org/dbXRates/USD"

    fun fetchGoldPriceUsd(): Double? {
        return try {
            val conn = URL(PRIMARY_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android GoldOverlayApp)")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                conn.disconnect()
                return null
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            parsePrice(body)
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePrice(json: String): Double? {
        return try {
            val obj = JSONObject(json)
            val items = obj.getJSONArray("items")
            val first = items.getJSONObject(0)
            // "xauPrice" maydoni 1 untsiya oltin narxini USD'da beradi
            first.getDouble("xauPrice")
        } catch (e: Exception) {
            null
        }
    }
}
