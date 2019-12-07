package edu.gatech.saxboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.androidplot.xy.*


class PlotFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_plot, container, false)
        val plot = view.findViewById<XYPlot>(R.id.accelerationPlot)

        val series1Numbers = listOf(1, 4, 2, 8, 4, 16, 8, 32, 16, 69)

        val series1: XYSeries = SimpleXYSeries(
            series1Numbers, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Series1"
        )
        val series1Format = LineAndPointFormatter(Color.RED, Color.GREEN, Color.BLUE, null)
        plot.addSeries(series1, series1Format)
        return view
    }
}
