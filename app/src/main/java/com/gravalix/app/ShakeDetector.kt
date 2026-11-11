package com.gravalix.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/** Detecta o celular sendo agitado (3 sacudidas fortes em até 1,5 s). */
class ShakeDetector(context: Context, private val onShake: () -> Unit) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var count = 0
    private var firstMs = 0L
    private var lastMs = 0L

    fun start() {
        val s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sm.unregisterListener(this)
        count = 0
    }

    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt(
            e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]
        ) / SensorManager.GRAVITY_EARTH
        if (g < 2.7f) return

        val now = System.currentTimeMillis()
        if (now - lastMs < 250) return // ignora a mesma sacudida
        lastMs = now

        if (count == 0 || now - firstMs > 1500) {
            count = 1
            firstMs = now
            return
        }
        count++
        if (count >= 3) {
            count = 0
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
