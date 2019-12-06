package edu.gatech.saxboard

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var skateboard: BluetoothDevice? = null
    private var connectThread: ConnectThread? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                val device: BluetoothDevice =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!
                Log.d(TAG, "Found device $device")
                if (device.name == SKATEBOARD_NAME) {
                    skateboard = device
                    bluetoothAdapter!!.cancelDiscovery()
                }
            }
        }
    }

    fun imuCallback(data: ImuPacket) {
        receivedChars.text = receivedChars.text.toString() + "IMU x: ${data.first}, y: ${data.second}, z: ${data.third}\n"
    }

    private lateinit var receivedChars: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val searchButton = findViewById<Button>(R.id.searchButton)
        searchButton.setOnClickListener {
            if (bluetoothAdapter == null) {
                Log.d(TAG, "bluetooth not exist")
            } else if (skateboard == null) {
                val startedDiscovery = bluetoothAdapter!!.startDiscovery()
                Log.d(TAG, "Began discovery? $startedDiscovery")
            } else {
                Log.d(TAG, "skateboard already exists")
            }
        }
        val connectButton = findViewById<Button>(R.id.connectButton)
        connectButton.setOnClickListener {
            if (skateboard == null) {
                Log.d(TAG, "skateboard not exist")
                return@setOnClickListener
            }
            connectThread = ConnectThread(skateboard!!)
            connectButton.isEnabled = false
            connectThread!!.start()
        }
        val disconnectButton = findViewById<Button>(R.id.disconnectButton)
        disconnectButton.setOnClickListener {
            if (skateboard == null) {
                Log.d(TAG, "skateboard not exist")
                return@setOnClickListener
            }
            connectThread!!.cancel()
            connectButton.isEnabled = true
        }
        val sendButton = findViewById<Button>(R.id.writeButton)
        val sendText = findViewById<EditText>(R.id.charsToSend)
        sendButton.setOnClickListener {
            if (connectThread == null) {
                Log.d(TAG, "not connected")
                return@setOnClickListener
            }
            val err = connectThread!!.write(sendText.text.toString().toByteArray(Charsets.US_ASCII))
            if (err) {
                Log.d(TAG, "write failed")
            }
        }
        receivedChars = findViewById(R.id.readCharsView)
        val clearButton = findViewById<Button>(R.id.clearButton)
        clearButton.setOnClickListener {
            receivedChars.text = ""
        }
        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (bluetoothAdapter == null) {
            Log.d(TAG, "bluetooth not exist")
            return
        }
        bluetoothAdapter.takeIf { !it!!.isEnabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->
            if (device.name == SKATEBOARD_NAME) {
                skateboard = device
                Log.d(TAG, "Already paired to device $skateboard")
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    companion object {
        const val REQUEST_ENABLE_BT = 1
        const val SKATEBOARD_NAME = "ESP32test"
        const val TAG = "saxboardLogging"
        val PACKET_LENGTHS = mapOf(
            "IMU" to 12
        )
    }

    inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val readBuffer = ByteArray(256)

        private val socket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(device.uuids[0].uuid)
        }
        private var outStream: OutputStream? = null

        override fun run() {
            bluetoothAdapter!!.cancelDiscovery()
            socket?.use { socket ->
                socket.connect()
                outStream = socket.outputStream
                var numBytes: Int
                Log.d(TAG, "Connected")

                var parserState = ParserState.Waiting
                val packetBuffer = ByteArray(32)
                var packetBufferIdx = 0
                var packetId = ""
                var packetLength: Int? = null
                var packetReady = false
                while (true) {
                    numBytes = try {
                        socket.inputStream.read(readBuffer)
                    } catch (e: IOException) {
                        Log.d(TAG, "Input stream was disconnected", e)
                        break
                    }
                    Log.d(TAG, "Received $numBytes bytes")
                    for (i in 0 until numBytes) {
                        if (parserState == ParserState.Waiting) {
                            if (readBuffer[i] == '!'.toByte()) {
                                parserState = ParserState.PacketBegun
                                packetId = ""
                                Log.d(TAG, "Began packet")
                            } else {
                                Log.w(TAG, "Malformed packet, '!' expected but ${readBuffer[i]} present")
                                break
                            }
                        } else if (parserState == ParserState.PacketBegun) {
                            packetId += readBuffer[i].toChar()
                            Log.d(TAG, "Packet ID $packetId")
                            if (packetId.length == 3) {
                                packetLength = PACKET_LENGTHS[packetId]
                                if (packetLength == null) {
                                    Log.w(TAG, "Unrecognized packet type $packetId")
                                    parserState = ParserState.Waiting
                                    break
                                }
                                parserState = ParserState.IdReceived
                                packetBufferIdx = 0
                            }
                        } else if (parserState == ParserState.IdReceived) {
                            packetBuffer[packetBufferIdx++] = readBuffer[i]
                            if (packetBufferIdx >= packetLength!!) {
                                packetReady = true
                                parserState = ParserState.Waiting
                                break
                            }
                            if (packetBufferIdx >= 32) {
                                Log.w(TAG, "Invalid packet length $packetLength")
                                parserState = ParserState.Waiting
                                break
                            }
                        }
                    }
                    if (packetReady) {
                        packetReady = false
                        when (packetId) {
                            "IMU" -> {
                                val imuPacket = parseImuPacket(packetBuffer)
                                runOnUiThread {
                                    imuCallback(imuPacket)
                                }
                            }
                        }
                    }
                }
            }
        }

        // false if success, true if error
        fun write(bytes: ByteArray): Boolean {
            return try {
                outStream?.write(bytes)
                false
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)
                true
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }
    enum class ParserState {
        Waiting,
        PacketBegun,
        IdReceived,
    }

    fun parseImuPacket(packetBuffer: ByteArray) : ImuPacket {
        // flip byte endianness from what the esp32 sent
        for (i in 0 until packetBuffer.lastIndex step 4) {
            var temp = packetBuffer[i]
            packetBuffer[i] = packetBuffer[i+3]
            packetBuffer[i+3] = temp
            temp = packetBuffer[i+1]
            packetBuffer[i+1] = packetBuffer[i+2]
            packetBuffer[i+2] = temp
        }
        val wbuf = ByteBuffer.wrap(packetBuffer)
        return ImuPacket(
            wbuf.getFloat(0),
            wbuf.getFloat(4),
            wbuf.getFloat(8)
        )
    }
}

typealias ImuPacket = Triple<Float, Float, Float>