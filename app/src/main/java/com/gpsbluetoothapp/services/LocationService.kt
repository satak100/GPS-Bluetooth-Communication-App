package com.gpsbluetoothapp.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*

class LocationService : Service() {
    
    companion object {
        private const val TAG = "LocationService"
        private const val LOCATION_UPDATE_INTERVAL = 5000L // 5 seconds
        private const val FASTEST_LOCATION_UPDATE_INTERVAL = 2000L // 2 seconds
    }
    
    private val binder = LocalBinder()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var locationListener: LocationListener? = null
    private var currentLocation: Location? = null
    private var isRequestingUpdates = false
    
    interface LocationListener {
        fun onLocationChanged(location: Location)
        fun onLocationError(error: String)
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
        Log.d(TAG, "LocationService created")
    }
    
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    locationListener?.onLocationChanged(location)
                    Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
                }
            }
            
            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!locationAvailability.isLocationAvailable) {
                    locationListener?.onLocationError("Location not available")
                }
            }
        }
    }
    
    fun setLocationListener(listener: LocationListener) {
        this.locationListener = listener
    }
    
    fun getCurrentLocation(): Location? {
        return currentLocation
    }
    
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(): Boolean {
        if (isRequestingUpdates) {
            return true
        }
        
        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                LOCATION_UPDATE_INTERVAL
            ).apply {
                setMinUpdateIntervalMillis(FASTEST_LOCATION_UPDATE_INTERVAL)
                setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL * 2)
            }.build()
            
            locationCallback?.let { callback ->
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    callback,
                    Looper.getMainLooper()
                )
                isRequestingUpdates = true
                Log.d(TAG, "Started location updates")
                return true
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied", e)
            locationListener?.onLocationError("Location permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
            locationListener?.onLocationError("Error starting location updates: ${e.message}")
        }
        
        return false
    }
    
    fun stopLocationUpdates() {
        if (!isRequestingUpdates) {
            return
        }
        
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
            isRequestingUpdates = false
            Log.d(TAG, "Stopped location updates")
        }
    }
    
    @SuppressLint("MissingPermission")
    fun requestSingleLocationUpdate() {
        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = location
                    locationListener?.onLocationChanged(location)
                } else {
                    locationListener?.onLocationError("Unable to get current location")
                }
            }.addOnFailureListener { exception ->
                Log.e(TAG, "Error getting current location", exception)
                locationListener?.onLocationError("Error getting location: ${exception.message}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied", e)
            locationListener?.onLocationError("Location permission denied")
        }
    }
    
    fun isRequestingUpdates(): Boolean {
        return isRequestingUpdates
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        Log.d(TAG, "LocationService destroyed")
    }
}
