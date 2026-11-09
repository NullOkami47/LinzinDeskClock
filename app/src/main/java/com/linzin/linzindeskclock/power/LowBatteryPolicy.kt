package com.linzin.linzindeskclock.power

enum class LowBatteryAction { OFF, DIM, EXIT }

object LowBatteryPolicy {
    fun action(level:Int, powered:Boolean, threshold:Int, selected:LowBatteryAction):LowBatteryAction =
        if(level in 0..100 && !powered && level < threshold.coerceIn(5,50)) selected else LowBatteryAction.OFF

    fun brightness(requested:Float, systemBrightness:Float, action:LowBatteryAction):Float {
        if(action != LowBatteryAction.DIM) return requested
        val current=if(requested in 0f..1f) requested else systemBrightness.coerceIn(0.01f,1f)
        return minOf(current,0.08f)
    }
}
