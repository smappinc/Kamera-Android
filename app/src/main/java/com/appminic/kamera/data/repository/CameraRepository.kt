package com.appminic.kamera.data.repository

import android.util.Log
import com.appminic.kamera.data.model.CameraMarker
import com.appminic.kamera.data.model.CameraType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

class CameraRepository {
    private val TAG = "CameraRepository"
    private val firestore = FirebaseFirestore.getInstance()
    private val camerasCollection = firestore.collection("cameras")

    suspend fun addCameraMarker(
        type: CameraType,
        location: GeoPoint,
        reportedBy: String,
        description: String = ""
    ): Result<CameraMarker> = try {
        Log.d(TAG, "Creating new camera marker")
        val marker = CameraMarker(
            type = type,
            location = location,
            reportedBy = reportedBy,
            description = description
        )
        val docRef = camerasCollection.document()
        val markerWithId = marker.copy(id = docRef.id)
        Log.d(TAG, "Saving marker to Firestore with ID: ${markerWithId.id}")
        docRef.set(markerWithId).await()
        Log.d(TAG, "Successfully saved marker to Firestore")
        Result.success(markerWithId)
    } catch (e: Exception) {
        Log.e(TAG, "Error saving marker to Firestore", e)
        Result.failure(e)
    }

    fun getCameraMarkers(): Flow<List<CameraMarker>> = flow {
        try {
            Log.d(TAG, "Fetching camera markers from Firestore")
            val snapshot = camerasCollection.get().await()
            val markers = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CameraMarker::class.java)
            }
            Log.d(TAG, "Retrieved ${markers.size} markers from Firestore")
            emit(markers)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching markers from Firestore", e)
            emit(emptyList())
        }
    }

    suspend fun updateVotes(markerId: String, isThumbsUp: Boolean): Result<Unit> = try {
        val field = if (isThumbsUp) "thumbsUp" else "thumbsDown"
        camerasCollection.document(markerId)
            .update(field, com.google.firebase.firestore.FieldValue.increment(1))
            .await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun flagCamera(markerId: String): Result<Unit> = try {
        camerasCollection.document(markerId)
            .update("flags", com.google.firebase.firestore.FieldValue.increment(1))
            .await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
} 