package com.appminic.kamera.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.appminic.kamera.R
import com.appminic.kamera.databinding.FragmentMapBinding
import com.appminic.kamera.data.model.CameraMarker
import com.appminic.kamera.data.model.CameraType
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import android.util.Log
import androidx.annotation.DrawableRes

class MapFragment : Fragment(), OnMapReadyCallback {
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private var googleMap: GoogleMap? = null
    private val viewModel: MapViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentMarkers = mutableListOf<com.google.android.gms.maps.model.Marker>()
    private var selectedLocation: LatLng? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Permission granted, enable location features
                enableMyLocation()
            }
            else -> {
                // Permission denied, show Uganda map without location features
                setupMapWithoutLocation()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        binding.map.onCreate(savedInstanceState)
        binding.map.getMapAsync(this)

        binding.fabReport.setOnClickListener {
            if (hasLocationPermission()) {
                showReportCameraDialog()
            } else {
                requestLocationPermission()
            }
        }

        binding.fabLocation.setOnClickListener {
            if (hasLocationPermission()) {
                centerOnMyLocation()
            } else {
                requestLocationPermission()
            }
        }

        // Observe camera markers
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.cameraMarkers.collect { markers ->
                    updateCameraMarkers(markers)
                }
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        setupMapListeners()
        checkLocationPermission()

        // Set initial visible bounds
        map.cameraPosition?.let { position ->
            viewModel.updateVisibleMarkers(position.target, position.zoom)
        }
    }

    private fun setupMapListeners() {
        googleMap?.setOnCameraIdleListener {
            googleMap?.cameraPosition?.let { position ->
                viewModel.saveCameraPosition(position.target, position.zoom)
                // Update visible markers when map moves
                viewModel.updateVisibleMarkers(position.target, position.zoom)
            }
        }

        googleMap?.setOnMarkerClickListener { marker ->
            val cameraMarker = currentMarkers.indexOf(marker).let { index ->
                if (index >= 0) viewModel.cameraMarkers.value.getOrNull(index) else null
            }
            cameraMarker?.let { showCameraDetailsDialog(it) }
            true
        }

        // Add long press listener
        googleMap?.setOnMapLongClickListener { latLng ->
            selectedLocation = latLng
            showReportCameraDialog()
        }
    }

    private fun checkLocationPermission() {
        when {
            hasLocationPermission() -> {
                enableMyLocation()
            }
            else -> {
                requestLocationPermission()
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun enableMyLocation() {
        try {
            googleMap?.apply {
                isMyLocationEnabled = true
                uiSettings.isCompassEnabled = true
                setupMapWithoutLocation()
            }
        } catch (e: SecurityException) {
            // Handle the case where permission was revoked while the app was running
            setupMapWithoutLocation()
        }
    }

    private fun centerOnMyLocation() {
        if (!hasLocationPermission()) {
            Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                } else {
                    Toast.makeText(context, "Location not available", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                Toast.makeText(context, "Failed to get location", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMapWithoutLocation() {
        googleMap?.apply {
            uiSettings.isCompassEnabled = true

            if (viewModel.hasSavedPosition()) {
                // Restore last camera position
                viewModel.getLastCameraPosition()?.let { (position, zoom) ->
                    position?.let {
                        moveCamera(CameraUpdateFactory.newLatLngZoom(it, zoom))
                    }
                }
            } else {
                // Set initial camera position to Uganda
                val ugandaBounds = LatLngBounds(
                    LatLng(1.0, 29.0), // Southwest corner
                    LatLng(4.0, 35.0)  // Northeast corner
                )
                moveCamera(CameraUpdateFactory.newLatLngBounds(ugandaBounds, 0))
            }
        }
    }

    private fun updateCameraMarkers(markers: List<CameraMarker>) {
        Log.d("MapFragment", "Updating markers: received ${markers.size} markers")
        
        // Clear existing markers
        currentMarkers.forEach { it.remove() }
        currentMarkers.clear()

        // Filter markers based on visible bounds
        val visibleMarkers = markers.filter { marker ->
            viewModel.visibleBounds?.contains(marker.toLatLng()) == true
        }
        Log.d("MapFragment", "Filtered to ${visibleMarkers.size} visible markers")
        Log.d("MapFragment", "Current visible bounds: ${viewModel.visibleBounds}")

        // Add new markers
        visibleMarkers.forEach { marker ->
            val icon = when (marker.type) {
                CameraType.SPEED -> BitmapDescriptorFactory.fromBitmap(vectorToBitmap(R.drawable.ic_speed_camera))
                CameraType.RED_LIGHT -> BitmapDescriptorFactory.fromBitmap(vectorToBitmap(R.drawable.ic_red_light_camera))
                CameraType.POLICE -> BitmapDescriptorFactory.fromBitmap(vectorToBitmap(R.drawable.ic_traffic_camera))
            }

            val markerOptions = MarkerOptions()
                .position(marker.toLatLng())
                .icon(icon)
                .title(marker.type.name)
                .snippet("Reported by: ${marker.reportedBy}")

            googleMap?.addMarker(markerOptions)?.let { marker ->
                currentMarkers.add(marker)
                Log.d("MapFragment", "Added marker at ${marker.position}")
            }
        }
        Log.d("MapFragment", "Total markers on map: ${currentMarkers.size}")
    }

    private fun vectorToBitmap(@DrawableRes id: Int): Bitmap {
        val vectorDrawable = ContextCompat.getDrawable(requireContext(), id)!!
        val bitmap = Bitmap.createBitmap(
            vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vectorDrawable.draw(canvas)
        return bitmap
    }

    private fun showReportCameraDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_report_camera, null)

        val cameraTypeDropdown = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.camera_type_dropdown)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.description_input)
        val locationText = dialogView.findViewById<TextView>(R.id.location_text)

        // Setup camera type dropdown
        val cameraTypes = CameraType.values().map { it.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() } }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, cameraTypes)
        cameraTypeDropdown.setAdapter(adapter)
        
        // Show dropdown when clicked
        cameraTypeDropdown.setOnClickListener {
            cameraTypeDropdown.showDropDown()
        }

        // Handle selection
        cameraTypeDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedType = CameraType.values()[position].name
            cameraTypeDropdown.setText(cameraTypes[position], false)
        }

        // Update location text
        locationText.text = if (selectedLocation != null) {
            "Selected location: ${selectedLocation?.latitude}, ${selectedLocation?.longitude}"
        } else {
            "Location will be set to your current position"
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Report Camera")
            .setView(dialogView)
            .setPositiveButton("Report") { dialog, _ ->
                val selectedType = cameraTypeDropdown.text.toString()
                val description = descriptionInput.text.toString()

                if (selectedType.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            val location = if (selectedLocation != null) {
                                selectedLocation
                            } else {
                                val currentLocation = fusedLocationClient.lastLocation.await() // TODO: Call requires permission which may be rejected by user
                                if (currentLocation != null) {
                                    LatLng(currentLocation.latitude, currentLocation.longitude)
                                } else {
                                    Toast.makeText(context, "Location not available", Toast.LENGTH_SHORT).show()
                                    null
                                }
                            }

                            if (location != null) {
                                // Convert display text back to enum value
                                val cameraType = CameraType.values()[cameraTypes.indexOf(selectedType)]
                                val result = viewModel.addCameraMarker(
                                    type = cameraType,
                                    location = location,
                                    reportedBy = "Anonymous", // TODO: Get actual user ID
                                    description = description
                                )

                                if (result.isSuccess) {
                                    Toast.makeText(context, "Camera reported successfully", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                    selectedLocation = null // Reset selected location
                                } else {
                                    Toast.makeText(context, "Failed to report camera", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Please select a camera type", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                selectedLocation = null // Reset selected location
            }
            .create()

        dialog.show()
    }

    private fun showCameraDetailsDialog(cameraMarker: CameraMarker) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_camera_details, null)

        dialogView.findViewById<TextView>(R.id.camera_type).text = 
            cameraMarker.type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        dialogView.findViewById<TextView>(R.id.reported_by).text = 
            "Reported by: ${cameraMarker.reportedBy}"
        dialogView.findViewById<TextView>(R.id.description).text = 
            cameraMarker.description.ifEmpty { "No description provided" }
        dialogView.findViewById<TextView>(R.id.vote_count).text = 
            "👍 ${cameraMarker.thumbsUp}  👎 ${cameraMarker.thumbsDown}"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.btn_thumbs_up).setOnClickListener {
            lifecycleScope.launch {
                viewModel.updateVotes(cameraMarker.id, true)
                dialog.dismiss()
            }
        }

        dialogView.findViewById<MaterialButton>(R.id.btn_thumbs_down).setOnClickListener {
            lifecycleScope.launch {
                viewModel.updateVotes(cameraMarker.id, false)
                dialog.dismiss()
            }
        }

        dialogView.findViewById<MaterialButton>(R.id.btn_flag).setOnClickListener {
            lifecycleScope.launch {
                viewModel.flagCamera(cameraMarker.id)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    override fun onStart() {
        super.onStart()
        binding.map.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        binding.map.onPause()
        super.onPause()
    }

    override fun onStop() {
        binding.map.onStop()
        super.onStop()
    }

    override fun onDestroyView() {
        binding.map.onDestroy()
        _binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.map.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.map.onLowMemory()
    }
} 