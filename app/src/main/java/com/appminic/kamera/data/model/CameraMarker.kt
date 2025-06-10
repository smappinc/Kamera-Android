package com.appminic.kamera.data.model

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.GeoPoint

data class CameraMarker(
    val id: String = "",
    val type: CameraType = CameraType.SPEED,
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val reportedBy: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val thumbsUp: Int = 0,
    val thumbsDown: Int = 0,
    val flags: Int = 0,
    val description: String = ""
) {
    fun toLatLng(): LatLng = LatLng(location.latitude, location.longitude)
}

enum class CameraType {
    SPEED,
    RED_LIGHT,
    POLICE
} 