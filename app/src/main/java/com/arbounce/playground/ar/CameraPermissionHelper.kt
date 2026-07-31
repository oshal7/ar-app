package com.arbounce.playground.ar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Camera runtime-permission helpers (from the ARCore samples). */
object CameraPermissionHelper {
    private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
    const val CAMERA_PERMISSION_CODE = 4321

    fun hasCameraPermission(activity: Activity): Boolean =
        ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    fun requestCameraPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity, arrayOf(CAMERA_PERMISSION), CAMERA_PERMISSION_CODE,
        )
    }

    fun shouldShowRequestPermissionRationale(activity: Activity): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(activity, CAMERA_PERMISSION)
}
