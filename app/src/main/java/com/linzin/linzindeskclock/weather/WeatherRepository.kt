package com.linzin.linzindeskclock.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.linzin.linzindeskclock.HourlyWeather
import com.linzin.linzindeskclock.WeatherInfo
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * 主動獲取最新定位（優先嘗試高時效快取，若無則在 Android 11+ 主動單次定位）
 */
@SuppressLint("MissingPermission")
suspend fun fetchBestLocation(context: Context): Location? = withContext(Dispatchers.IO) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return@withContext null

    val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    if (!granted) return@withContext null
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .filter { it in locationManager.allProviders }
    val lastLocation = providers.mapNotNull {
        runCatching { locationManager.getLastKnownLocation(it) }.getOrNull()
    }.maxByOrNull { it.time }

    // 若快取在 10 分鐘內，直接視為有效位置返回以節省電量
    if (lastLocation != null && (System.currentTimeMillis() - lastLocation.time) < 10 * 60 * 1000L) {
        return@withContext lastLocation
    }

    // 2. Android 11+ (API 30+) 發起主動單次定位（加 5 秒逾時保護，避免無衛星時卡住）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            withTimeoutOrNull(5000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cancellationSignal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { cancellationSignal.cancel() }

                    val provider = providers.firstOrNull {
                        it != LocationManager.PASSIVE_PROVIDER && locationManager.isProviderEnabled(it)
                    }
                    if (provider == null) {
                        cont.resume(lastLocation)
                        return@suspendCancellableCoroutine
                    }

                    locationManager.getCurrentLocation(
                        provider,
                        cancellationSignal,
                        ContextCompat.getMainExecutor(context)
                    ) { loc ->
                        if (cont.isActive) {
                            cont.resume(loc ?: lastLocation)
                        }
                    }
                }
            } ?: lastLocation
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            lastLocation
        }
    } else {
        lastLocation
    }
}

/**
 * 整合抓取定位、反向地理編碼與 Open-Meteo 天氣
 */
suspend fun fetchAndApplyWeather(
    context: Context,
    currentLocationStr: String,
    onResult: (WeatherInfo?) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val location = fetchBestLocation(context)
        val lat = location?.latitude ?: 24.838
        val lon = location?.longitude ?: 121.017

        // 預設採用「目前位置」，即便 Geocoder 逾時或失敗也不會死板卡在「新竹市」
        var detectedCity = currentLocationStr
        try {
            val geocoder = Geocoder(context, context.resources.configuration.locales[0])
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                detectedCity = addr.locality
                    ?: addr.subAdminArea
                            ?: addr.adminArea
                            ?: currentLocationStr
            }
        } catch (e: Exception) {
            detectedCity = currentLocationStr
        }

        val data = fetchOpenMeteoWeather(lat, lon, detectedCity)
        withContext(Dispatchers.Main) {
            onResult(data)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Log.e("DeskClockWeather", "Failed to fetch and apply weather", e)
    }
}

/**
 * 查詢 Open-Meteo 天氣預報 API 並解析
 */
suspend fun fetchOpenMeteoWeather(lat: Double, lon: Double, cityName: String): WeatherInfo? = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        val urlStr = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code&timezone=auto"
        connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000

        val responseText = connection.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(responseText)

        val currentObj = root.getJSONObject("current")
        val currentTemp = currentObj.getDouble("temperature_2m").toFloat()
        val currentCode = currentObj.getInt("weather_code")

        val hourlyObj = root.getJSONObject("hourly")
        val times = hourlyObj.getJSONArray("time")
        val temps = hourlyObj.getJSONArray("temperature_2m")
        val codes = hourlyObj.getJSONArray("weather_code")

        val nowCal = Calendar.getInstance()
        val currentHourInt = nowCal.get(Calendar.HOUR_OF_DAY)

        val hourlyResult = mutableListOf<HourlyWeather>()
        val inFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)

        for (i in 0 until times.length()) {
            val tStr = times.getString(i)
            val date = inFormat.parse(tStr)
            if (date != null) {
                val cal = Calendar.getInstance().apply { time = date }
                if (cal.get(Calendar.HOUR_OF_DAY) >= currentHourInt && cal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)) {
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val t = temps.getDouble(i).toFloat()
                    val c = codes.getInt(i)
                    hourlyResult.add(HourlyWeather(hourOfDay = hour, tempC = t, weatherCode = c))
                    if (hourlyResult.size == 4) break
                }
            }
        }

        var fallbackIdx = 0
        while (hourlyResult.size < 4 && fallbackIdx < times.length()) {
            val tStr = times.getString(fallbackIdx)
            val date = inFormat.parse(tStr)
            if (date != null) {
                val cal = Calendar.getInstance().apply { time = date }
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val t = temps.getDouble(fallbackIdx).toFloat()
                val c = codes.getInt(fallbackIdx)
                if (hourlyResult.none { it.hourOfDay == hour }) {
                    hourlyResult.add(HourlyWeather(hourOfDay = hour, tempC = t, weatherCode = c))
                }
            }
            fallbackIdx++
        }

        WeatherInfo(
            cityName = cityName,
            currentTempC = currentTemp,
            currentWeatherCode = currentCode,
            hourlyList = hourlyResult
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Log.e("DeskClockWeather", "Failed to fetch Open-Meteo", e)
        null
    } finally {
        connection?.disconnect()
    }
}
