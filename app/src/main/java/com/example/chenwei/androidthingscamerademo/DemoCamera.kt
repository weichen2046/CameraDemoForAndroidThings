package com.example.chenwei.androidthingscamerademo

import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.util.Log
import java.util.*

/**
 * Created by Chen Wei on 2017/12/5.
 */
class DemoCamera {

    private var mImageReader: ImageReader? = null
    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null

    fun initializeCamera(context: Context, backgroudHandler: Handler,
                         imageAvailableListener: ImageReader.OnImageAvailableListener) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var camIds: Array<String> = emptyArray()
        try {
            camIds = manager.cameraIdList
        } catch (e: CameraAccessException) {
            Log.d(TAG, "Camera access exception getting IDs", e)
        }
        if (camIds.isEmpty()) {
            Log.d(TAG, "No cameras found")
            return
        }
        val id = camIds[0]
        Log.d(TAG, "Using camera id $id")

        // Initialize image processor
        mImageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.JPEG, MAX_IMAGES)
        mImageReader!!.setOnImageAvailableListener(imageAvailableListener, backgroudHandler)

        // Open the camera resource
        try {
            manager.openCamera(id, mStateCallback, backgroudHandler)
        } catch (ex: CameraAccessException) {
            Log.d(TAG, "Open camera error", ex)
        }
    }

    fun shutDown() {
        mCameraDevice?.close()
    }

    fun tackPicture() {
        if (mCameraDevice == null || mImageReader == null) {
            Log.d(TAG, "Cannot capture image. Camera not initialized")
            return
        }
        try {
            mCameraDevice!!.createCaptureSession(Collections.singletonList(mImageReader!!.surface),
                    mSessionCallback, null)
        } catch (ex: CameraAccessException) {
            Log.d(TAG, "Access exception while preparing picture", ex)
        }
    }

    private val mSessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession?) {
            Log.d(TAG, "Failed to configure camera")
        }

        override fun onConfigured(session: CameraCaptureSession?) {
            if (mCameraDevice == null) {
                return
            }
            mCaptureSession = session
            triggerImageCapture()
        }
    }

    private fun triggerImageCapture() {
        try {
            val captureBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(mImageReader!!.surface)
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            Log.d(TAG, "Session initialized")
            mCaptureSession!!.capture(captureBuilder.build(), mCaptureCallback, null)
        } catch (ex: CameraAccessException) {
            Log.d(TAG, "Camera capture exception", ex)
        }
    }

    private val mCaptureCallback = object: CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession?, request: CaptureRequest?, result: TotalCaptureResult?) {
            if (session != null) {
                session.close()
                mCaptureSession = null
                Log.d(TAG, "CaptureSession closed")
            }
        }
    }

    private val mStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera opened")
            mCameraDevice = camera
        }

        override fun onDisconnected(camera: CameraDevice?) {
            Log.d(TAG, "Camera disconnected")
            mCameraDevice = null
        }

        override fun onError(camera: CameraDevice?, error: Int) {
            Log.d(TAG, "Camera error")
            mCameraDevice = null
        }
    }

    companion object {
        const val IMAGE_WIDTH = 640 // 320 // 640
        const val IMAGE_HEIGHT = 480 // 240 // 480
        const val MAX_IMAGES = 2
    }
}