package com.gpsbluetoothapp.config

/**
 * Configuration constants for the GPS Bluetooth App
 * 
 * To use your own HC-06 device:
 * 1. Find your HC-06's MAC address in Android Bluetooth settings
 * 2. Update HC06_MAC_ADDRESS below with your device's MAC address
 * 3. Rebuild the app
 * 
 * Alternative: The app will automatically search for any paired device 
 * with "HC-06" or "HC-05" in the name, so manual MAC configuration 
 * may not be necessary.
 */
object BluetoothConfig {
    
    /**
     * Default HC-06 MAC address
     * Replace this with your actual HC-06 MAC address
     * Format: "XX:XX:XX:XX:XX:XX"
     */
    const val HC06_MAC_ADDRESS = "98:D3:31:FB:48:F6"
    
    /**
     * Common HC-06 device names to search for
     */
    val HC06_DEVICE_NAMES = listOf(
        "HC-06",
        "HC-05", 
        "HC06",
        "HC05",
        "Bluetooth Module"
    )
    
    /**
     * Bluetooth SPP (Serial Port Profile) UUID
     * This is standard and should not be changed
     */
    const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
    
    /**
     * Connection timeout in milliseconds
     */
    const val CONNECTION_TIMEOUT_MS = 10000L
    
    /**
     * Default baud rate for HC-06 communication
     */
    const val DEFAULT_BAUD_RATE = 9600
}
