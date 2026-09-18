package com.dakd.jarvis

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

class TorchManager(private val context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var isTorchOn = false
    private var torchCameraId: String? = null

    init {
        findTorchCamera()
    }

    private fun findTorchCamera() {
        if (cameraManager == null) return
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    torchCameraId = id
                    break
                }
            }
            if (torchCameraId == null && cameraManager.cameraIdList.isNotEmpty()) {
                torchCameraId = cameraManager.cameraIdList[0]
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun isSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) && torchCameraId != null
    }

    fun isStateOn(): Boolean = isTorchOn

    fun setTorch(enabled: Boolean): Pair<Boolean, String> {
        if (!isSupported() || cameraManager == null || torchCameraId == null) {
            return Pair(false, "Sir, is phone mein flashlight feature available nahi hai.")
        }
        return try {
            cameraManager.setTorchMode(torchCameraId!!, enabled)
            isTorchOn = enabled
            val state = if (enabled) "on" else "off"
            Pair(true, "Torch $state kar diya hai Sir.")
        } catch (e: CameraAccessException) {
            Pair(false, "Torch access error: ${e.message}")
        } catch (e: Exception) {
            Pair(false, "Torch toggle error: ${e.message}")
        }
    }

    fun toggleTorch(): Pair<Boolean, String> {
        return setTorch(!isTorchOn)
    }
}
