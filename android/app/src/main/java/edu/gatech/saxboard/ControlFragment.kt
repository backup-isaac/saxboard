package edu.gatech.saxboard

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.android.synthetic.main.fragment_control.*

class ControlFragment : Fragment() {
    private var leftLedsEnabled = false
    private var rightLedsEnabled = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_control, container, false)
        val leftColorChooseButton = view.findViewById<Button>(R.id.leftColorSelectButton)
        leftColorChooseButton.setOnClickListener {
            ColorPickerDialog.Builder(view.context)
                .setTitle("Left LED Color")
                .setPositiveButton("Select", object: ColorEnvelopeListener {
                    override fun onColorSelected(envelope: ColorEnvelope, fromUser: Boolean) {
                        leftColorChooseButton.setBackgroundColor(envelope.color)
                    }
                }).setNegativeButton("Cancel") { di, _ ->
                    di.dismiss()
                }
                .attachAlphaSlideBar(false)
                .attachBrightnessSlideBar(true)
                .show()
        }
        val leftLedEnableButton = view.findViewById<Button>(R.id.leftLedEnableButton)
        leftLedEnableButton.setOnClickListener {
            leftLedsEnabled = !leftLedsEnabled
            leftLedEnableButton.text = if (leftLedsEnabled) {
                "Disable"
            } else {
                "Enable"
            }
        }
        val rightColorChooseButton = view.findViewById<Button>(R.id.rightColorSelectButton)
        rightColorChooseButton.setOnClickListener {
            ColorPickerDialog.Builder(view.context)
                .setTitle("Right LED Color")
                .setPositiveButton("Select", object: ColorEnvelopeListener {
                    override fun onColorSelected(envelope: ColorEnvelope, fromUser: Boolean) {
                        rightColorChooseButton.setBackgroundColor(envelope.color)
                    }
                }).setNegativeButton("Cancel") { di, _ ->
                    di.dismiss()
                }
                .attachAlphaSlideBar(false)
                .attachBrightnessSlideBar(true)
                .show()
        }
        val rightLedEnableButton = view.findViewById<Button>(R.id.rightLedEnableButton)
        rightLedEnableButton.setOnClickListener {
            rightLedsEnabled = !rightLedsEnabled
            rightLedEnableButton.text = if (rightLedsEnabled) {
                "Disable"
            } else {
                "Enable"
            }
        }
        return view
    }

    companion object {
        const val TAG = "saxboardControlFragment"
    }
}
