package edu.gatech.saxboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.androidplot.xy.LineAndPointFormatter
import com.androidplot.xy.SimpleXYSeries
import com.androidplot.xy.XYPlot


abstract class PlotFragment(private val layoutId: Int) : Fragment() {
    private lateinit var mainActivity: MainActivity
    protected lateinit var plot: XYPlot
    protected lateinit var xSeries: SimpleXYSeries
    protected lateinit var ySeries: SimpleXYSeries
    protected lateinit var zSeries: SimpleXYSeries
    private val resetToDefaults: () -> Unit = {
        xSeries = SimpleXYSeries(arrayListOf(0), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "X")
        ySeries = SimpleXYSeries(arrayListOf(0), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Y")
        zSeries = SimpleXYSeries(arrayListOf(0), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Z")
        first = true
        i = 0
    }

    var i = 0
    var first = true
    var firstTimestamp: Long = 0

    abstract val imuCallback: (ImuPacket) -> Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity = activity as MainActivity
        mainActivity.registerDisconnectedCallback(resetToDefaults)
        xSeries = SimpleXYSeries(arrayListOf(0), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "X")
        ySeries = SimpleXYSeries(arrayListOf(0), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Y")
        zSeries = SimpleXYSeries(arrayListOf(0), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Z")
        mainActivity.registerImuCallback(imuCallback)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(layoutId, container, false)
        plot = view.findViewById(R.id.plot)
        val xSeriesFormat = LineAndPointFormatter(Color.RED, Color.RED, Color.alpha(0), null)
        plot.addSeries(xSeries, xSeriesFormat)
        val ySeriesFormat = LineAndPointFormatter(Color.GREEN, Color.GREEN, Color.alpha(0), null)
        plot.addSeries(ySeries, ySeriesFormat)
        val zSeriesFormat = LineAndPointFormatter(Color.BLUE, Color.BLUE, Color.alpha(0), null)
        plot.addSeries(zSeries, zSeriesFormat)
        return view
    }

    override fun onDestroy() {
        mainActivity.unregisterDisconnectedCallback(resetToDefaults)
        mainActivity.unregisterImuCallback(imuCallback)
        super.onDestroy()
    }
}
