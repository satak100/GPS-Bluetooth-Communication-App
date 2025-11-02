package com.gpsbluetoothapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gpsbluetoothapp.databinding.ActivityMainBinding
import com.gpsbluetoothapp.services.BluetoothService
import com.gpsbluetoothapp.services.LocationService
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_ENABLE_BT = 1
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 101
        private const val LOG_VIEWER_REQUEST_CODE = 102
    }
    
    private lateinit var binding: ActivityMainBinding
    private var bluetoothService: BluetoothService? = null
    private var locationService: LocationService? = null
    private var isBluetoothServiceBound = false
    private var isLocationServiceBound = false
    
    private var autoSendHandler: Handler? = null
    private var autoSendRunnable: Runnable? = null
    
    // Track location availability
    private var hasLocationUpdate = false
    
    // Store all log messages for the log viewer
    private val allLogMessages = mutableListOf<String>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkPermissions()
    }
    
    private fun setupUI() {
        binding.btnConnectBluetooth.setOnClickListener {
            val isConnected = bluetoothService?.isConnected() ?: false
            if (isConnected) {
                disconnectBluetooth()
            } else {
                if (hasBluetoothPermissions()) {
                    connectBluetooth()
                } else {
                    requestBluetoothPermissions()
                }
            }
        }
        
        binding.btnSendLocation.setOnClickListener {
            sendCurrentLocation()
        }
        
        binding.btnSendTestMessage.setOnClickListener {
            sendTestMessage()
        }
        
        binding.switchAutoSend.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startAutoSend()
            } else {
                stopAutoSend()
            }
        }
        
        binding.btnClearLogs.setOnClickListener {
            clearLogs()
        }
        
        binding.btnViewAllLogs.setOnClickListener {
            openLogViewer()
        }
        
        updateUI()
    }
    
    private fun openLogViewer() {
        val intent = Intent(this, LogViewerActivity::class.java)
        intent.putStringArrayListExtra("log_messages", ArrayList(allLogMessages))
        startActivityForResult(intent, LOG_VIEWER_REQUEST_CODE)
    }
    
    private fun checkPermissions() {
        val missingPermissions = mutableListOf<String>()
        
        if (!hasLocationPermissions()) {
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (!hasBluetoothPermissions()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                missingPermissions.addAll(listOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ))
            }
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startServices()
        }
    }
    
    private fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this, 
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this, 
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                BLUETOOTH_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE, BLUETOOTH_PERMISSION_REQUEST_CODE -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                
                if (allGranted) {
                    addLogMessage("✅ Permissions granted")
                    startServices()
                } else {
                    showPermissionDeniedDialog("This app requires these permissions to function properly.")
                }
            }
        }
    }
    
    private fun showPermissionDeniedDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(message)
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                addLogMessage("❌ App cannot function without required permissions")
            }
            .setCancelable(false)
            .show()
    }
    
    private fun startServices() {
        startBluetoothService()
        startLocationService()
    }
    
    private fun startBluetoothService() {
        val intent = Intent(this, BluetoothService::class.java)
        bindService(intent, bluetoothServiceConnection, BIND_AUTO_CREATE)
        addLogMessage("🔵 Starting Bluetooth service...")
    }
    
    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        bindService(intent, locationServiceConnection, BIND_AUTO_CREATE)
        addLogMessage("📍 Starting location service...")
    }
    
    private val bluetoothServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothService.LocalBinder
            bluetoothService = binder.getService()
            bluetoothService?.setConnectionListener(bluetoothConnectionListener)
            isBluetoothServiceBound = true
            addLogMessage("🔵 Bluetooth service connected")
            updateUI()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            isBluetoothServiceBound = false
            bluetoothService = null
            addLogMessage("🔵 Bluetooth service disconnected")
            updateUI()
        }
    }
    
    private val locationServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationService.LocalBinder
            locationService = binder.getService()
            locationService?.setLocationListener(locationListener)
            isLocationServiceBound = true
            addLogMessage("📍 Location service connected")
            
            // Start location updates
            locationService?.startLocationUpdates()
            addLogMessage("📍 Starting location updates...")
            
            updateUI()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            isLocationServiceBound = false
            locationService = null
            addLogMessage("📍 Location service disconnected")
            updateUI()
        }
    }
    
    private val bluetoothConnectionListener = object : BluetoothService.ConnectionListener {
        override fun onConnectionStateChanged(isConnected: Boolean, deviceName: String?) {
            runOnUiThread {
                if (isConnected) {
                    addLogMessage("🔗 Bluetooth device connected: ${deviceName ?: "Unknown"}")
                } else {
                    addLogMessage("❌ Bluetooth device disconnected")
                }
                updateUI()
            }
        }
        
        override fun onDataSent(data: String) {
            runOnUiThread {
                addLogMessage("📤 Sent: $data")
            }
        }
        
        override fun onDataReceived(data: String) {
            runOnUiThread {
                addLogMessage("📨 Received: $data")
            }
        }
        
        override fun onError(error: String) {
            runOnUiThread {
                addLogMessage("⚠️ Bluetooth Error: $error")
                updateUI()
            }
        }
    }
    
    private val locationListener = object : LocationService.LocationListener {
        override fun onLocationChanged(location: Location) {
            runOnUiThread {
                hasLocationUpdate = true
                binding.tvLatitude.text = String.format("%.6f", location.latitude)
                binding.tvLongitude.text = String.format("%.6f", location.longitude)
                
                addLogMessage("📍 Location: ${location.latitude}, ${location.longitude} (±${location.accuracy.toInt()}m)")
                updateUI()
            }
        }
        
        override fun onLocationError(error: String) {
            runOnUiThread {
                addLogMessage("⚠️ Location Error: $error")
                updateUI()
            }
        }
    }
    
    private fun connectBluetooth() {
        try {
            if (!hasBluetoothPermissions()) {
                requestBluetoothPermissions()
                return
            }
            
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                addLogMessage("❌ Bluetooth not supported on this device")
                return
            }
            
            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                return
            }
            
            bluetoothService?.connectToHC06()
            addLogMessage("🔵 Connecting to Bluetooth device...")
            updateUI()
        } catch (e: Exception) {
            addLogMessage("❌ Connect error: ${e.message}")
        }
    }
    
    private fun disconnectBluetooth() {
        try {
            bluetoothService?.disconnect()
            addLogMessage("🔴 Disconnecting Bluetooth...")
            updateUI()
        } catch (e: Exception) {
            addLogMessage("❌ Disconnect error: ${e.message}")
        }
    }
    
    private fun sendCurrentLocation() {
        try {
            val location = locationService?.getCurrentLocation()
            if (location != null) {
                val locationData = "${location.latitude},${location.longitude}"
                val success = bluetoothService?.sendData(locationData) ?: false
                
                if (success) {
                    addLogMessage("📤 Sent: $locationData")
                } else {
                    addLogMessage("❌ Failed to send location data")
                }
            } else {
                addLogMessage("⚠️ Location not available")
            }
        } catch (e: Exception) {
            addLogMessage("❌ Send location error: ${e.message}")
        }
    }
    
    private fun sendTestMessage() {
        try {
            val success = bluetoothService?.sendData("Test message from GPS Bluetooth App") ?: false
            
            if (success) {
                addLogMessage("📤 Sent: Test message")
            } else {
                addLogMessage("❌ Failed to send test message")
            }
        } catch (e: Exception) {
            addLogMessage("❌ Send test message error: ${e.message}")
        }
    }
    
    private fun startAutoSend() {
        try {
            val intervalText = binding.etInterval.text.toString()
            val interval = if (intervalText.isNotEmpty()) {
                intervalText.toIntOrNull()?.let { it * 1000L } ?: 5000L
            } else {
                5000L
            }
            
            autoSendHandler = Handler(Looper.getMainLooper())
            autoSendRunnable = object : Runnable {
                override fun run() {
                    sendCurrentLocation()
                    autoSendHandler?.postDelayed(this, interval)
                }
            }
            autoSendHandler?.post(autoSendRunnable!!)
            addLogMessage("🔄 Auto-send started (every ${interval/1000} seconds)")
        } catch (e: Exception) {
            addLogMessage("❌ Auto-send start error: ${e.message}")
        }
    }
    
    private fun stopAutoSend() {
        try {
            autoSendHandler?.removeCallbacks(autoSendRunnable!!)
            autoSendHandler = null
            autoSendRunnable = null
            addLogMessage("⏹️ Auto-send stopped")
        } catch (e: Exception) {
            addLogMessage("❌ Auto-send stop error: ${e.message}")
        }
    }
    
    private fun clearLogs() {
        binding.tvLogs.text = "Logs cleared...\n"
        addLogMessage("🗑️ Logs cleared")
    }
    
    private fun addLogMessage(message: String) {
        try {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] $message"
            
            // Add to stored messages for log viewer
            allLogMessages.add(logEntry)
            
            runOnUiThread {
                val currentText = binding.tvLogs.text.toString()
                val newText = if (currentText.isEmpty()) {
                    logEntry
                } else {
                    "$currentText\n$logEntry"
                }
                binding.tvLogs.text = newText
                
                // Auto-scroll to bottom
                binding.scrollViewLogs.post {
                    binding.scrollViewLogs.fullScroll(android.view.View.FOCUS_DOWN)
                }
            }
        } catch (e: Exception) {
            // Fallback logging to avoid crashes
            runOnUiThread {
                binding.tvLogs.text = "${binding.tvLogs.text}\n[ERROR] $message"
            }
        }
    }
    
    private fun updateUI() {
        try {
            val isBluetoothConnected = bluetoothService?.isConnected() ?: false
            val isLocationAvailable = hasLocationUpdate && (locationService?.getCurrentLocation() != null)
            
            binding.btnConnectBluetooth.isEnabled = isBluetoothServiceBound
            binding.btnSendLocation.isEnabled = isBluetoothConnected && isLocationAvailable
            binding.btnSendTestMessage.isEnabled = isBluetoothConnected
            binding.switchAutoSend.isEnabled = isBluetoothConnected && isLocationAvailable
            
            // Update button text based on connection state
            binding.btnConnectBluetooth.text = if (isBluetoothConnected) "🔴 Disconnect" else "🔵 Connect Bluetooth"
            
            // Update status indicators
            binding.tvBluetoothStatus.text = if (isBluetoothConnected) "🔗 Connected" else "❌ Disconnected"
            binding.tvLocationStatus.text = if (isLocationAvailable) "📍 Available" else "⚠️ Searching..."
        } catch (e: Exception) {
            addLogMessage("❌ UI update error: ${e.message}")
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                if (resultCode == Activity.RESULT_OK) {
                    addLogMessage("✅ Bluetooth enabled")
                    connectBluetooth()
                } else {
                    addLogMessage("❌ Bluetooth enable cancelled")
                }
            }
            LOG_VIEWER_REQUEST_CODE -> {
                addLogMessage("📋 Log viewer closed")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        try {
            stopAutoSend()
            
            if (isBluetoothServiceBound) {
                unbindService(bluetoothServiceConnection)
            }
            
            if (isLocationServiceBound) {
                unbindService(locationServiceConnection)
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}
