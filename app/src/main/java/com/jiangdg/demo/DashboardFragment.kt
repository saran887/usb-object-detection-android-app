package com.jiangdg.demo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.jiangdg.ausbc.base.BaseFragment
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.demo.databinding.FragmentMultiCameraBinding
import com.jiangdg.demo.service.VehicleSafetyService

class DashboardFragment : BaseFragment() {
    private var _binding: FragmentMultiCameraBinding? = null
    private val binding get() = _binding!!

    private var safetyService: VehicleSafetyService? = null
    private var isBound = false
    private var activeCameraIndex = 1

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as VehicleSafetyService.LocalBinder
            val sService = binder.getService()
            safetyService = sService
            isBound = true
            
            // Set callback to receive updates from the service
            sService.detectionEngine.start()
            
            // Start default camera
            updateCameraUi(1)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            safetyService = null
            isBound = false
        }
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View? {
        _binding = FragmentMultiCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initView() {
        super.initView()

        binding.camera1Button.setOnClickListener {
            safetyService?.updateDrivingState(VehicleSafetyService.DrivingState.FORWARD)
            updateCameraUi(1)
        }
        binding.camera2Button.setOnClickListener {
            safetyService?.updateDrivingState(VehicleSafetyService.DrivingState.REVERSE)
            updateCameraUi(2)
        }
        binding.camera3Button.setOnClickListener {
            safetyService?.updateDrivingState(VehicleSafetyService.DrivingState.LEFT)
            updateCameraUi(3)
        }
        binding.camera4Button.setOnClickListener {
            safetyService?.updateDrivingState(VehicleSafetyService.DrivingState.RIGHT)
            updateCameraUi(4)
        }
    }

    override fun initData() {
        super.initData()
        val intent = Intent(requireContext(), VehicleSafetyService::class.java)
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun updateCameraUi(index: Int) {
        activeCameraIndex = index

        // Hide all camera containers
        binding.camera1Container.visibility = View.GONE
        binding.camera2Container.visibility = View.GONE
        binding.camera3Container.visibility = View.GONE
        binding.camera4Container.visibility = View.GONE

        // Get the texture view and overlay for the active camera
        val (container, textureView, overlayView) = when (index) {
            1 -> Triple(binding.camera1Container, binding.textureView1, binding.overlayView1)
            2 -> Triple(binding.camera2Container, binding.textureView2, binding.overlayView2)
            3 -> Triple(binding.camera3Container, binding.textureView3, binding.overlayView3)
            else -> Triple(binding.camera4Container, binding.textureView4, binding.overlayView4)
        }

        container.visibility = View.VISIBLE
        binding.cameraInfoText.text = "Camera: $index - " + when (index) {
            1 -> "FRONT (Active)"
            2 -> "REAR"
            3 -> "LEFT BLINDSPOT"
            else -> "RIGHT BLINDSPOT"
        }

        // Bind the surface texture of the active TextureView to the camera stream
        safetyService?.cameraManager?.switchActiveCamera(index, textureView)
        
        // Listen for inference results to draw bounding box overlays
        // In a production system, we intercept the results from DetectionEngine and update active overlay
        // We override the service callback to also dispatch overlay results to the UI thread
        safetyService?.detectionEngine?.stop()
        safetyService?.detectionEngine = com.jiangdg.demo.manager.DetectionEngine(requireContext()) { results ->
            if (isAdded && !isDetached) {
                requireActivity().runOnUiThread {
                    overlayView.setImageSize(320, 320)
                    overlayView.setResults(results)
                    binding.fpsCounter.text = "Objects: ${results.size}"
                }
            }
        }
        safetyService?.detectionEngine?.start()
    }

    override fun onDestroyView() {
        if (isBound) {
            requireActivity().unbindService(serviceConnection)
            isBound = false
        }
        _binding = null
        super.onDestroyView()
    }
}
