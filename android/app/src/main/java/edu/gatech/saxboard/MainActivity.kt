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

    fun readCallback(bytes: ByteArray, length: Int) {
        receivedChars.text = receivedChars.text.substring(length) + "\n" + bytes.toString(Charsets.US_ASCII)
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
        const val SKATEBOARD_NAME = "spaghet"
        const val TAG = "saxboardLogging"
    }

    inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val readBuffer = ByteArray(1024)

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
                while (true) {
                    numBytes = try {
                        socket.inputStream.read(readBuffer)
                    } catch (e: IOException) {
                        Log.d(TAG, "Input stream was disconnected", e)
                        break
                    }
                    runOnUiThread {
                        readCallback(readBuffer, numBytes)
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
}
