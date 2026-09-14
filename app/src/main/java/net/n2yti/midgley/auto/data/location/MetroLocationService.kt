package net.n2yti.midgley.auto.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.n2yti.midgley.auto.data.preferences.MetroPreferenceManager

/**
 * Automotive Location Service tracking vehicle position via FusedLocationProviderClient,
 * enforcing 25 km displacement filters and notifying subscribers when crossing MSA boundaries.
 */
class MetroLocationService(
    private val context: Context,
    private val preferenceManager: MetroPreferenceManager = MetroPreferenceManager(context),
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
) {

    companion object {
        const val UPDATE_INTERVAL_MILLIS = 300_000L // 5 minutes
        const val MIN_DISPLACEMENT_METERS = 25_000f // 25 km automotive displacement filter
    }

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _currentResolvedMetro = MutableStateFlow<MetroLocationResolver.ResolvedMetro?>(null)
    val currentResolvedMetro: StateFlow<MetroLocationResolver.ResolvedMetro?> = _currentResolvedMetro.asStateFlow()

    private var isTracking = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val lastLoc = result.lastLocation ?: return
            handleNewLocation(lastLoc)
        }
    }

    fun hasLocationPermission(): Boolean {
        val finePerm = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePerm = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return finePerm == PackageManager.PERMISSION_GRANTED || coarsePerm == PackageManager.PERMISSION_GRANTED
    }

    fun startLocationUpdates(onMetroChanged: (MetroLocationResolver.ResolvedMetro) -> Unit = {}) {
        if (!hasLocationPermission() || isTracking) return

        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MILLIS)
                .setMinUpdateDistanceMeters(MIN_DISPLACEMENT_METERS)
                .build()

            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            isTracking = true

            // Query last known location immediately
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    handleNewLocation(loc, onMetroChanged)
                }
            }
        } catch (e: SecurityException) {
            // Permissions revoked
            isTracking = false
        }
    }

    fun stopLocationUpdates() {
        if (!isTracking) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isTracking = false
    }

    fun handleNewLocation(
        location: Location,
        onMetroChanged: (MetroLocationResolver.ResolvedMetro) -> Unit = {}
    ) {
        _currentLocation.value = location
        val resolved = MetroLocationResolver.resolve(location.latitude, location.longitude)
        val previous = _currentResolvedMetro.value
        _currentResolvedMetro.value = resolved

        if (preferenceManager.isAutoDetect()) {
            val currentSelected = preferenceManager.getSelectedLocale()
            if (!currentSelected.equals(resolved.id, ignoreCase = true)) {
                // Auto-switched to a new refining hub!
                onMetroChanged(resolved)
            }
        }
    }
}
