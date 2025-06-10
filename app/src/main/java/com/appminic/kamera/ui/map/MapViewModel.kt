package com.appminic.kamera.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appminic.kamera.data.model.CameraMarker
import com.appminic.kamera.data.model.CameraType
import com.appminic.kamera.data.repository.CameraRepository
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.pow

class MapViewModel : ViewModel() {
    private val repository = CameraRepository()
    private val TAG = "MapViewModel"

    private val _cameraMarkers = MutableStateFlow<List<CameraMarker>>(emptyList())
    val cameraMarkers: StateFlow<List<CameraMarker>> = _cameraMarkers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    var visibleBounds: LatLngBounds? = null
        private set

    private val _lastCameraPosition = MutableStateFlow<Pair<LatLng?, Float>?>(null)
    val lastCameraPosition: StateFlow<Pair<LatLng?, Float>?> = _lastCameraPosition.asStateFlow()

    init {
        loadCameraMarkers()
    }

    fun updateVisibleMarkers(center: LatLng, zoom: Float) {
        val radius = calculateVisibleRadius(zoom)
        visibleBounds = LatLngBounds(
            LatLng(center.latitude - radius, center.longitude - radius),
            LatLng(center.latitude + radius, center.longitude + radius)
        )
        Log.d(TAG, "Updated visible bounds: $visibleBounds with radius: $radius")
    }

    private fun calculateVisibleRadius(zoom: Float): Double {
        // More generous radius calculation
        // At zoom level 15, radius is about 0.01 degrees (roughly 1km)
        // At zoom level 10, radius is about 0.1 degrees (roughly 10km)
        return 0.01 * (2.0.pow((15 - zoom).toDouble()))
    }

    fun saveCameraPosition(position: LatLng, zoom: Float) {
        _lastCameraPosition.value = position to zoom
    }

    fun hasSavedPosition(): Boolean {
        return _lastCameraPosition.value != null
    }

    fun getLastCameraPosition(): Pair<LatLng?, Float>? {
        return _lastCameraPosition.value
    }

    private fun loadCameraMarkers() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d(TAG, "Starting to load camera markers")
                repository.getCameraMarkers().collect { markers ->
                    Log.d(TAG, "Received ${markers.size} markers from repository")
                    _cameraMarkers.value = markers
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading markers", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun addCameraMarker(
        type: CameraType,
        location: LatLng,
        reportedBy: String,
        description: String
    ): Result<Unit> {
        return try {
            Log.d(TAG, "Adding new camera marker: type=$type, location=$location")
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            val result = repository.addCameraMarker(type, geoPoint, reportedBy, description)
            if (result.isSuccess) {
                Log.d(TAG, "Successfully added marker to repository")
                loadCameraMarkers() // Reload markers after adding a new one
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to add marker to repository", result.exceptionOrNull())
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to add marker"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding camera marker", e)
            Result.failure(e)
        }
    }

    suspend fun updateVotes(cameraId: String, isThumbsUp: Boolean): Result<Unit> {
        return try {
            repository.updateVotes(cameraId, isThumbsUp)
            loadCameraMarkers() // Reload markers after updating votes
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun flagCamera(cameraId: String): Result<Unit> {
        return try {
            repository.flagCamera(cameraId)
            loadCameraMarkers() // Reload markers after flagging
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 