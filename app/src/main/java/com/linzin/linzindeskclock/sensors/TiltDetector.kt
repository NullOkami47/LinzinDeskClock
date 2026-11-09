package com.linzin.linzindeskclock.sensors

import android.content.Context
import android.graphics.Point
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs

/** Owned by a bounded scan. No listener exists outside start/stop. */
class TiltDetector(context: Context, private val onReading: (TiltReading) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val display = context.getSystemService(WindowManager::class.java).defaultDisplay
    private var filtered: FloatArray? = null
    private val timing = SensorTiming()
    private var stablePose = false
    private var reliableAccuracy = true
    val available: Boolean get() = sensor != null
    fun start(): Boolean {
        stop(); reliableAccuracy = true
        return sensor?.let { manager.registerListener(this, it, 50_000, 0) } ?: false
    }
    fun stop() { manager.unregisterListener(this); filtered = null; timing.reset(); stablePose = false }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        reliableAccuracy = accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
        if (!reliableAccuracy) { filtered = null; stablePose = false; onReading(TiltReading.INVALID) }
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.size < 3 || !reliableAccuracy || event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) { onReading(TiltReading.INVALID); return }
        when(timing.classify(event.timestamp,SystemClock.elapsedRealtimeNanos())) {
            SensorContinuity.REJECT -> {
                filtered=null; stablePose=false; onReading(TiltReading.INVALID); return
            }
            SensorContinuity.NEW_SEGMENT -> {
                filtered=null; stablePose=false; onReading(TiltReading.INVALID)
            }
            SensorContinuity.CONTINUOUS -> Unit
        }
        val size = Point(); display.getRealSize(size)
        val naturalPortrait = if (display.rotation == Surface.ROTATION_0 || display.rotation == Surface.ROTATION_180)
            size.y >= size.x else size.x >= size.y
        val dominance = if(stablePose) 1.2f else 1.35f
        val raw = GravityOrientation.measure(event.values[0],event.values[1],event.values[2],naturalPortrait,dominance)
        if (!raw.reliable) { filtered = null; stablePose = false; onReading(TiltReading.INVALID); return }
        val old = filtered
        val next = if (old == null) event.values.copyOf(3) else FloatArray(3) { old[it] + .3f * (event.values[it] - old[it]) }
        filtered = next
        val smoothed = GravityOrientation.measure(next[0],next[1],next[2],naturalPortrait,dominance)
        // Landscape-axis hysteresis reduces edge flicker without narrowing the required 30–75 tilt range.
        // Raw out-of-range/motion cannot be masked by the filter.
        stablePose = raw.isLandscape && smoothed.isLandscape && raw.tiltDegrees in 30f..75f &&
            smoothed.tiltDegrees in 30f..75f &&
            abs(raw.tiltDegrees - smoothed.tiltDegrees) <= 5f
        onReading(smoothed.copy(reliable = stablePose))
    }
}
