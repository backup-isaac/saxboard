package edu.gatech.saxboard

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlin.text.Charsets.US_ASCII

class ControlFragment : Fragment() {
    private var leftLedsEnabled = false
    private var rightLedsEnabled = false
    private var playingSong: String? = null
    private var queuedSong: String? = null
    private val leftRgb = arrayOf(0xff, 0xc1, 0x07)
    private val rightRgb = arrayOf(0x03, 0xa9, 0xf4)
    private lateinit var songsAdapter: ArrayAdapter<String>
    private lateinit var leftColorChooseButton: Button
    private lateinit var rightColorChooseButton: Button
    private lateinit var leftLedEnableButton: Button
    private lateinit var rightLedEnableButton: Button
    private lateinit var soundPlayButton: Button
    private lateinit var volumeSlider: SeekBar
    private lateinit var mainActivity: MainActivity

    private val resetToDefaults: () -> Unit = {
        leftLedsEnabled = false
        rightLedsEnabled = false
        playingSong = null
        queuedSong = null
        leftRgb[0] = 0xff
        leftRgb[1] = 0xc1
        leftRgb[2] = 0x07
        rightRgb[0] = 0x03
        rightRgb[1] = 0xa9
        rightRgb[2] = 0xf4
        leftColorChooseButton.setBackgroundColor(Color.rgb(leftRgb[0],leftRgb[1],leftRgb[2]))
        rightColorChooseButton.setBackgroundColor(Color.rgb(rightRgb[0],rightRgb[1],rightRgb[2]))
        leftLedEnableButton.text = "Enable"
        rightLedEnableButton.text = "Enable"
        volumeSlider.progress = 50
        soundPlayButton.text = "Play"
        songsAdapter.clear()
        songsAdapter.add(DEFAULT_SELECTION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity = activity as MainActivity
        mainActivity.registerDisconnectedCallback(resetToDefaults)
        mainActivity.registerAudioFileCallback {
            songsAdapter.add(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_control, container, false)
        leftColorChooseButton = view.findViewById(R.id.leftColorChooseButton)
        leftColorChooseButton.setOnClickListener {
            ColorPickerDialog.Builder(view.context)
                .setTitle("Left LED Color")
                .setPositiveButton("Select", object: ColorEnvelopeListener {
                    override fun onColorSelected(envelope: ColorEnvelope, fromUser: Boolean) {
                        leftColorChooseButton.setBackgroundColor(envelope.color)
                        for (i in 0 until 3) {
                            leftRgb[i] = envelope.argb[i+1]
                        }
                    }
                }).setNegativeButton("Cancel") { di, _ ->
                    di.dismiss()
                }
                .attachAlphaSlideBar(false)
                .attachBrightnessSlideBar(true)
                .show()
        }
        val leftColorSelectButton = view.findViewById<Button>(R.id.leftColorSetButton)
        leftColorSelectButton.setOnClickListener {
            sendLedColorCommand(false, leftRgb[0], leftRgb[1], leftRgb[2])
            if (!leftLedsEnabled && sendLedEnableCommand(false, !leftLedsEnabled)) {
                leftLedsEnabled = !leftLedsEnabled
                leftLedEnableButton.text = if (leftLedsEnabled) {
                    "Disable"
                } else {
                    "Enable"
                }
            }
        }
        leftLedEnableButton = view.findViewById(R.id.leftLedEnableButton)
        leftLedEnableButton.setOnClickListener {
            if (sendLedEnableCommand(false, !leftLedsEnabled)) {
                leftLedsEnabled = !leftLedsEnabled
                leftLedEnableButton.text = if (leftLedsEnabled) {
                    "Disable"
                } else {
                    "Enable"
                }
            }
        }
        rightColorChooseButton = view.findViewById(R.id.rightColorChooseButton)
        rightColorChooseButton.setOnClickListener {
            ColorPickerDialog.Builder(view.context)
                .setTitle("Right LED Color")
                .setPositiveButton("Select", object: ColorEnvelopeListener {
                    override fun onColorSelected(envelope: ColorEnvelope, fromUser: Boolean) {
                        rightColorChooseButton.setBackgroundColor(envelope.color)
                        for (i in 0 until 3) {
                            rightRgb[i] = envelope.argb[i+1]
                        }
                    }
                }).setNegativeButton("Cancel") { di, _ ->
                    di.dismiss()
                }
                .attachAlphaSlideBar(false)
                .attachBrightnessSlideBar(true)
                .show()
        }
        val rightColorSelectButton = view.findViewById<Button>(R.id.rightColorSetButton)
        rightColorSelectButton.setOnClickListener {
            sendLedColorCommand(true, rightRgb[0], rightRgb[1], rightRgb[2])
            if (!rightLedsEnabled && sendLedEnableCommand(true, !rightLedsEnabled)) {
                rightLedsEnabled = !rightLedsEnabled
                rightLedEnableButton.text = if (rightLedsEnabled) {
                    "Disable"
                } else {
                    "Enable"
                }
            }
        }
        rightLedEnableButton = view.findViewById(R.id.rightLedEnableButton)
        rightLedEnableButton.setOnClickListener {
            if (sendLedEnableCommand(true, !rightLedsEnabled)) {
                rightLedsEnabled = !rightLedsEnabled
                rightLedEnableButton.text = if (rightLedsEnabled) {
                    "Disable"
                } else {
                    "Enable"
                }
            }
        }
        soundPlayButton = view.findViewById(R.id.playButton)
        soundPlayButton.setOnClickListener {
            if (queuedSong == null && playingSong == null) {
                return@setOnClickListener
            }
            if (playingSong != null && (queuedSong == null || queuedSong == playingSong)) {
                if (sendStopSongCommand()) {
                    playingSong = null
                    soundPlayButton.text = "Play"
                }
            } else {
                if (sendPlaySongCommand(queuedSong!!)) {
                    playingSong = queuedSong
                    soundPlayButton.text = "Stop"
                }
            }
        }
        volumeSlider = view.findViewById(R.id.volumeSlider)
        volumeSlider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) {
                    sendVolumeCommand(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sendVolumeCommand(seekBar.progress)
            }
        })
        val songSpinner = view.findViewById<Spinner>(R.id.songSpinner)
        songsAdapter = ArrayAdapter<String>(
            view.context,
            android.R.layout.simple_spinner_item,
            arrayListOf(DEFAULT_SELECTION)
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            songSpinner.adapter = it
        }
        songSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selection = parent?.getItemAtPosition(position) as? String?
                queuedSong = if (selection != null && selection != DEFAULT_SELECTION)  {
                    selection
                } else {
                    null
                }
                if (queuedSong != null && queuedSong != playingSong) {
                    soundPlayButton.text = "Play"
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        return view
    }

    override fun onDestroy() {
        mainActivity.unregisterDisconnectedCallback(resetToDefaults)
        mainActivity.unregisterAudioFileCallback()
        super.onDestroy()
    }

    private fun sendLedColorCommand(isRightLed: Boolean, red: Int, green: Int, blue: Int) {
        val command = "LC" + if (isRightLed) "R" else "L"
        val bytes = ByteArray(3)
        bytes[0] = red.toByte()
        bytes[1] = green.toByte()
        bytes[2] = blue.toByte()
        mainActivity.sendCommand(command, bytes)
    }

    private fun sendLedEnableCommand(isRightLed: Boolean, isEnable: Boolean): Boolean {
        val command = "L" + (if (isEnable) "E" else "D") + (if (isRightLed) "R" else "L")
        return mainActivity.sendCommand(command, ByteArray(0))
    }

    private fun sendPlaySongCommand(song: String): Boolean {
        return mainActivity.sendCommand("PLS", song.toByteArray(US_ASCII))
    }

    private fun sendStopSongCommand(): Boolean {
        return mainActivity.sendCommand("STS", ByteArray(0));
    }

    private fun sendVolumeCommand(volume: Int): Boolean {
        val volToSend: Byte = if (volume < 0) 0 else if (volume > 100) 100 else volume.toByte()
        return mainActivity.sendCommand("VOL", ByteArray(1).also { it[0] = volToSend })
    }

    companion object {
        const val TAG = "saxboardControlFragment"
        const val DEFAULT_SELECTION = "Select a song..."
    }
}
