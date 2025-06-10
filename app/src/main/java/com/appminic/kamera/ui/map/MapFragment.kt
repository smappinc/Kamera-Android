package com.appminic.kamera.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.appminic.kamera.R
import com.appminic.kamera.databinding.FragmentMapBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

class MapFragment : Fragment(), OnMapReadyCallback {
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private var googleMap: GoogleMap? = null
    private val viewModel: MapViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

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
        
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.fabReport.setOnClickListener {
            // TODO: Implement report functionality
        }

        binding.fabLocation.setOnClickListener {
            if (hasLocationPermission()) {
                centerOnMyLocation()
            } else {
                requestLocationPermission()
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        setupMapListeners()
        checkLocationPermission()
    }

    private fun setupMapListeners() {
        googleMap?.setOnCameraIdleListener {
            googleMap?.cameraPosition?.let { position ->
                viewModel.saveCameraPosition(position.target, position.zoom)
            }
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
                val (position, zoom) = viewModel.getLastCameraPosition()
                position?.let {
                    moveCamera(CameraUpdateFactory.newLatLngZoom(it, zoom))
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 