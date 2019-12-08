package edu.gatech.saxboard

class DistPlotFragment : PlotFragment(R.layout.fragment_distplot) {
    override val imuCallback: (ImuPacket) -> Unit = {
        val t = System.currentTimeMillis() / 1000
        if (first) {
            firstTimestamp = t
        }
        xSeries.addLast(t - firstTimestamp, it.first)
        ySeries.addLast(t - firstTimestamp, it.second)
        zSeries.addLast(t - firstTimestamp, it.third)
        if (first) {
            first = false
            xSeries.removeFirst()
            ySeries.removeFirst()
            zSeries.removeFirst()
        }
        if (++i % 4 == 0) {
            plot.redraw()
        }
    }
}