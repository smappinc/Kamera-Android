package com.appminic.kamera.ui.map

import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

class MapViewModel : ViewModel() {
    private var lastCameraPosition: LatLng? = null
    private var lastZoomLevel: Float = 0f

    fun saveCameraPosition(position: LatLng, zoom: Float) {
        lastCameraPosition = position
        lastZoomLevel = zoom
    }

    fun getLastCameraPosition(): Pair<LatLng?, Float> {
        return Pair(lastCameraPosition, lastZoomLevel)
    }

    fun hasSavedPosition(): Boolean {
        return lastCameraPosition != null
    }
} 