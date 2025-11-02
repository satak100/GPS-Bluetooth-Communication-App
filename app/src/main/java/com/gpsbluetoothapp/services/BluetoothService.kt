package com.gpsbluetoothapp.services

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.gpsbluetoothapp.config.BluetoothConfig
import java.io.IOException
import java.io.OutputStream
import java.util.*

class BluetoothService : Service() {
    
    companion object {
        private const val TAG = "BluetoothService"
        private val HC06_UUID = UUID.fromString(BluetoothConfig.SPP_UUID)
        const val HC06_MAC_ADDRESS = BluetoothConfig.HC06_MAC_ADDRESS
        const val HC06_DEVICE_NAME = "HC-06" // Common HC-06 device name
    }
    
    private val binder = LocalBinder()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var connectionListener: ConnectionListener? = null
    private var connectedDeviceName: String? = null
    private var dataReceiveThread: Thread? = null
    
    interface ConnectionListener {
        fun onConnectionStateChanged(isConnected: Boolean, deviceName: String? = null)
        fun onDataSent(data: String)
        fun onDataReceived(data: String)
        fun onError(error: String)
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        Log.d(TAG, "BluetoothService created")
    }
    
    fun setConnectionListener(listener: ConnectionListener) {
        this.connectionListener = listener
    }
    
    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true
    }
    
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<String> {
        val deviceList = mutableListOf<String>()
        try {
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                deviceList.add("${device.name ?: "Unknown"} (${device.address})")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied when getting paired devices", e)
        }
        return deviceList
    }
    
    private fun startDataReceiveThread() {
        dataReceiveThread = Thread {
            val buffer = ByteArray(1024)
            var bytes: Int
            
            try {
                val inputStream = bluetoothSocket?.inputStream
                while (!Thread.currentThread().isInterrupted && bluetoothSocket?.isConnected == true) {
                    try {
                        bytes = inputStream?.read(buffer) ?: 0
                        if (bytes > 0) {
                            val receivedData = String(buffer, 0, bytes).trim()
                            if (receivedData.isNotEmpty()) {
                                Log.d(TAG, "Data received: $receivedData")
                                connectionListener?.onDataReceived(receivedData)
                            }
                        }
                    } catch (e: IOException) {
                        if (!Thread.currentThread().isInterrupted) {
                            Log.e(TAG, "Error reading data", e)
                            connectionListener?.onError("Connection lost: ${e.message}")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                if (!Thread.currentThread().isInterrupted) {
                    Log.e(TAG, "Unexpected error in data receive thread", e)
                }
            }
        }
        dataReceiveThread?.start()
    }
    
    fun getConnectedDeviceName(): String? {
        return connectedDeviceName
    }
    
    @SuppressLint("MissingPermission")
    fun connectToHC06(): Boolean {
        if (!isBluetoothEnabled()) {
            connectionListener?.onError("Bluetooth is not enabled")
            return false
        }
        
        try {
            // First try to find HC-06 by name in paired devices
            val pairedDevices = bluetoothAdapter?.bondedDevices
            var targetDevice: BluetoothDevice? = null
            
            // Look for HC-06 in paired devices first
            pairedDevices?.forEach { device ->
                if (device.name != null) {
                    val deviceName = device.name.uppercase()
                    val isHC06Device = BluetoothConfig.HC06_DEVICE_NAMES.any { name ->
                        deviceName.contains(name.uppercase())
                    }
                    if (isHC06Device || device.address == HC06_MAC_ADDRESS) {
                        targetDevice = device
                        Log.d(TAG, "Found HC-06 device: ${device.name} (${device.address})")
                        return@forEach
                    }
                }
            }
            
            // If not found in paired devices, try the hardcoded MAC address
            if (targetDevice == null) {
                try {
                    targetDevice = bluetoothAdapter?.getRemoteDevice(HC06_MAC_ADDRESS)
                    Log.d(TAG, "Using hardcoded MAC address: $HC06_MAC_ADDRESS")
                } catch (e: IllegalArgumentException) {
                    connectionListener?.onError("Invalid MAC address: $HC06_MAC_ADDRESS")
                    return false
                }
            }
            
            val device = targetDevice
            if (device == null) {
                connectionListener?.onError("HC-06 device not found. Please ensure it's paired and nearby.")
                return false
            }
            
            Log.d(TAG, "Attempting to connect to: ${device.name} (${device.address})")
            
            // Close any existing connection
            try {
                bluetoothSocket?.close()
            } catch (e: IOException) {
                Log.w(TAG, "Error closing existing socket", e)
            }
            
            // Create connection
            bluetoothSocket = device.createRfcommSocketToServiceRecord(HC06_UUID)
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            
            // Store connected device name
            connectedDeviceName = device.name ?: device.address
            
            // Start listening for incoming data
            startDataReceiveThread()
            
            connectionListener?.onConnectionStateChanged(true, connectedDeviceName)
            Log.d(TAG, "Successfully connected to: $connectedDeviceName")
            return true
            
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed", e)
            val errorMsg = when {
                e.message?.contains("Service discovery failed") == true -> 
                    "Device not found or not responding. Check if HC-06 is powered on and nearby."
                e.message?.contains("read failed") == true -> 
                    "Connection failed. Device may be connected to another device."
                e.message?.contains("Connection refused") == true -> 
                    "Connection refused. Check if the device is in pairing mode."
                else -> "Connection failed: ${e.message}"
            }
            connectionListener?.onError(errorMsg)
            disconnect()
            return false
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission denied", e)
            connectionListener?.onError("Bluetooth permission denied. Please grant Bluetooth permissions.")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during connection", e)
            connectionListener?.onError("Unexpected error: ${e.message}")
            disconnect()
            return false
        }
    }
    
    fun disconnect() {
        try {
            // Stop data receive thread
            dataReceiveThread?.interrupt()
            dataReceiveThread = null
            
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing connection", e)
        } finally {
            outputStream = null
            bluetoothSocket = null
            connectedDeviceName = null
            connectionListener?.onConnectionStateChanged(false)
            Log.d(TAG, "Disconnected from Bluetooth device")
        }
    }
    
    fun sendData(data: String): Boolean {
        if (!isConnected()) {
            connectionListener?.onError("Not connected to HC-06")
            return false
        }
        
        try {
            outputStream?.write(data.toByteArray())
            outputStream?.flush()
            connectionListener?.onDataSent(data)
            Log.d(TAG, "Data sent: $data")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Error sending data", e)
            connectionListener?.onError("Error sending data: ${e.message}")
            disconnect()
            return false
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        Log.d(TAG, "BluetoothService destroyed")
    }
}
