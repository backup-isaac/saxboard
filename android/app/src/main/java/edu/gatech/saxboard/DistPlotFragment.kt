package edu.gatech.saxboard

import kotlin.math.sqrt

class DistPlotFragment : PlotFragment(R.layout.fragment_distplot) {
    private val accelerationHistory = arrayListOf<Pair<Long, ImuPacket>>()
    private var lastHallTime = -1L
    private var xDistanceTraveled = 0.0
    private var yDistanceTraveled = 0.0
    private var zDistanceTraveled = 0.0

    override val resetToDefaults: () -> Unit = {
        super.resetToDefaults()
        accelerationHistory.clear()
        xDistanceTraveled = 0.0
        yDistanceTraveled = 0.0
        zDistanceTraveled = 0.0
    }

    override val imuCallback: (ImuPacket) -> Unit = {
        val t = System.currentTimeMillis()
        accelerationHistory.add(t to it)
    }

    // it is the distance
    override val hallCallback: (Double) -> Unit = {
        if (accelerationHistory.size != 0) {
            val t = System.currentTimeMillis()
            if (first) {
                firstTimestamp = t
                lastHallTime = accelerationHistory[0].first
            }
            for (i in 1 until accelerationHistory.lastIndex) {
                val dt = accelerationHistory[i].first - accelerationHistory[i - 1].first
                val x = accelerationHistory[i - 1].second.first
                val y = accelerationHistory[i - 1].second.second
                val z = accelerationHistory[i - 1].second.third
                val r = it * dt / (t - lastHallTime) / 1000
                xDistanceTraveled += r * z / sqrt(1 - x * x)
                yDistanceTraveled += -1 * r * y / sqrt(1 - x * x)
                zDistanceTraveled += r * x / sqrt(1 - z * z)
            }
            val dt = t - accelerationHistory.last().first
            val x = accelerationHistory.last().second.first
            val y = accelerationHistory.last().second.second
            val z = accelerationHistory.last().second.third
            val r = it * dt / (t - lastHallTime) / 1000
            xDistanceTraveled += r * z / sqrt(1 - x * x)
            yDistanceTraveled += -1 * r * y / sqrt(1 - x * x)
            zDistanceTraveled += r * x / sqrt(1 - z * z)
            xSeries.addLast((t - firstTimestamp) / 1000, xDistanceTraveled)
            ySeries.addLast((t - firstTimestamp) / 1000, yDistanceTraveled)
            zSeries.addLast((t - firstTimestamp) / 1000, zDistanceTraveled)
            if (first) {
                first = false
                xSeries.removeFirst()
                ySeries.removeFirst()
                zSeries.removeFirst()
            }
            plot.redraw()
            val lastPacket = accelerationHistory[accelerationHistory.lastIndex].second
            accelerationHistory.clear()
            accelerationHistory.add(t to lastPacket)
        }
    }
}