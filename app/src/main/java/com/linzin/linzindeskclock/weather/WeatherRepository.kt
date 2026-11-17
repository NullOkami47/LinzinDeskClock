package com.linzin.linzindeskclock.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener
import android.os.Bundle
import android.os.Looper
import androidx.core.os.ConfigurationCompat
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

/** Reject the invalid zero fix some older location providers return while starting. */
internal fun isUsableWeatherCoordinates(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        (kotlin.math.abs(latitude) > 0.001 || kotlin.math.abs(longitude) > 0.001)

private fun Location.isUsableWeatherLocation(): Boolean = isUsableWeatherCoordinates(latitude, longitude)

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
        runCatching { locationManager.getLastKnownLocation(it) }.getOrNull()?.takeIf(Location::isUsableWeatherLocation)
    }.maxByOrNull { it.time }

    if (lastLocation != null && (System.currentTimeMillis() - lastLocation.time) < 10 * 60 * 1000L) {
        return@withContext lastLocation
    }
    val activeProviders = providers.filter {
        it != LocationManager.PASSIVE_PROVIDER && runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false)
    }
    if (activeProviders.isEmpty()) return@withContext lastLocation

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            withTimeoutOrNull(5000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cancellationSignal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { cancellationSignal.cancel() }
                    locationManager.getCurrentLocation(activeProviders.first(), cancellationSignal,
                        ContextCompat.getMainExecutor(context)) { loc ->
                        if (cont.isActive) cont.resume(loc?.takeIf(Location::isUsableWeatherLocation) ?: lastLocation)
                    }
                }
            } ?: lastLocation
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            lastLocation
        }
    } else {
        // Android 5-10: request a bounded fresh fix and remove every listener on all completion paths.
        var registeredListener: LocationListener? = null
        try {
        withTimeoutOrNull(6000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (!location.isUsableWeatherLocation()) return
                        runCatching { locationManager.removeUpdates(this) }
                        if (cont.isActive) cont.resume(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                registeredListener = listener
                cont.invokeOnCancellation { runCatching { locationManager.removeUpdates(listener) } }
                try {
                    activeProviders.forEach { provider ->
                        locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    }
                } catch (_: Exception) {
                    runCatching { locationManager.removeUpdates(listener) }
                    if (cont.isActive) cont.resume(lastLocation)
                }
            }
        } ?: lastLocation
        } finally {
            registeredListener?.let { runCatching { locationManager.removeUpdates(it) } }
        }
    }
}

/** Hardware fix, last valid fix, then indoor IP fallback; never downgrade location requests to HTTP. */
suspend fun fetchAndApplyWeather(
    context: Context,
    currentLocationStr: String,
    onResult: (WeatherInfo?) -> Unit
) = withContext(Dispatchers.IO) {
    val data = try {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) {
            withContext(Dispatchers.Main) { onResult(null) }
            return@withContext
        }
        val prefs = context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
        val location = fetchBestLocation(context)
        var detectedCity: String? = null
        val coordinates = if (location != null) {
            prefs.edit().putString("pref_last_valid_lat", location.latitude.toString())
                .putString("pref_last_valid_lon", location.longitude.toString()).apply()
            location.latitude to location.longitude
        } else {
            val savedLat = prefs.getString("pref_last_valid_lat", null)?.toDoubleOrNull()
            val savedLon = prefs.getString("pref_last_valid_lon", null)?.toDoubleOrNull()
            if (savedLat != null && savedLon != null && isUsableWeatherCoordinates(savedLat, savedLon)) {
                savedLat to savedLon
            } else {
                val ipLocation = fetchIpLocationFallback()
                detectedCity = ipLocation?.third
                ipLocation?.let { it.first to it.second }
            }
        }
        val (lat, lon) = coordinates ?: (24.838 to 121.017)
        val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
        try {
            val geocoder = Geocoder(context, locale)
            @Suppress("DEPRECATION")
            val address = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
            val city = address?.locality ?: address?.subAdminArea ?: address?.subLocality
            if (!city.isNullOrBlank()) detectedCity = city.trim()
        } catch (_: Exception) { }
        if (detectedCity.isNullOrBlank()) detectedCity = fetchCityNameFallback(lat, lon, locale.toLanguageTag())
        fetchOpenMeteoWeather(lat, lon, detectedCity?.takeIf(String::isNotBlank) ?: currentLocationStr)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.e("DeskClockWeather", "Failed to fetch and apply weather", error)
        null
    }
    withContext(Dispatchers.Main) { onResult(data) }
}

private fun requestWeatherJson(url: String, timeoutMillis: Int): JSONObject {
    require(url.startsWith("https://"))
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = timeoutMillis
        readTimeout = timeoutMillis
        useCaches = false
        setRequestProperty("Connection", "close")
        setRequestProperty("User-Agent", "LinzinDeskClock-Android")
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) throw java.io.IOException("HTTP status $code")
        return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    } finally {
        connection.disconnect()
    }
}

private fun fetchIpLocationFallback(): Triple<Double, Double, String?>? = try {
    val json = requestWeatherJson("https://ipwho.is/?fields=success,city,latitude,longitude", 5000)
    val lat = json.optDouble("latitude", Double.NaN)
    val lon = json.optDouble("longitude", Double.NaN)
    if (json.optBoolean("success") && isUsableWeatherCoordinates(lat, lon)) {
        Triple(lat, lon, json.optString("city").trim().takeIf(String::isNotBlank))
    } else null
} catch (_: Exception) { null }

private fun fetchCityNameFallback(lat: Double, lon: Double, languageTag: String): String? = try {
    val language = java.net.URLEncoder.encode(languageTag, "UTF-8")
    val json = requestWeatherJson("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=$language", 6000)
    val country = json.optString("countryName").trim()
    val subdivision = json.optString("principalSubdivision").trim()
    val directCity = listOf(json.optString("locality").trim(), json.optString("city").trim())
        .firstOrNull { it.isNotBlank() && it != country && it != subdivision }
    directCity ?: run {
        val administrative = json.optJSONObject("localityInfo")?.optJSONArray("administrative")
        var city: String? = null
        if (administrative != null) for (i in administrative.length() - 1 downTo 0) {
            val entry = administrative.optJSONObject(i) ?: continue
            val candidate = entry.optString("name").trim()
            if (entry.optInt("adminLevel") in 5..8 && candidate.isNotBlank() && candidate != country && candidate != subdivision) {
                city = candidate
                break
            }
        }
        city
    }
} catch (_: Exception) { null }

/**
 * 查詢 Open-Meteo 天氣預報 API 並解析
 */
suspend fun fetchOpenMeteoWeather(lat: Double, lon: Double, cityName: String): WeatherInfo? = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        val urlStr = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code&forecast_days=2&timezone=auto"
        connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.useCaches = false
        connection.setRequestProperty("Connection", "close")
        connection.setRequestProperty("User-Agent", "LinzinDeskClock-Android")

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
