package com.example.chenwei.androidthingscamerademo

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.Surface
import com.example.chenwei.androidthingscamerademo.Constants.Companion.TAG
import java.util.*
import kotlin.Comparator

/**
 * Created by Chen Wei on 2017/12/5.
 */
class DemoCamera(private val mImageAvailableListener: ImageReader.OnImageAvailableListener,
                 private val mBackgroundHandler: Handler,
                 private val mTextureView: AutoFitTextureView? = null) {

    private var mCameraId: String? = null
    private var mImageReader: ImageReader? = null
    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mPreviewSize: Size? = null
    private var mFlashSupported = false
    private var mSupportedAFModes: IntArray = kotlin.IntArray(0)
    private var mSupportedAEModes: IntArray = kotlin.IntArray(0)
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null
    private var mPreviewRequest: CaptureRequest? = null
    private var mState = STATE.STATE_PREVIEW

    private var mSensorOrientation: Int = 0

    enum class STATE {
        STATE_PREVIEW,
        STATE_PICTURE_TAKEN,
    }

    class CompareSizeByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            return lhs.width * lhs.height - rhs.width * rhs.height
        }
    }

    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        private fun progress(result: CaptureResult, session: CameraCaptureSession) {
            when (mState) {
                STATE.STATE_PREVIEW -> {
                    // Nothing to do
                }
                STATE.STATE_PICTURE_TAKEN -> {
                    session.close()
                    mCaptureSession = null
                    Log.d(TAG, "CaptureSession closed")
                    // Reset to preview state
                    mState = STATE.STATE_PREVIEW
                }
            }
        }

        override fun onCaptureProgressed(session: CameraCaptureSession, request: CaptureRequest?, partialResult: CaptureResult) {
            progress(partialResult, session)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest?, result: TotalCaptureResult) {
            progress(result, session)
        }
    }

    private val mStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera opened")
            mCameraDevice = camera
            createPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Camera disconnected")
            camera.close()
            mCameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.d(TAG, "Camera error: $error")
            camera.close()
            mCameraDevice = null
        }
    }

    /**
     * Should be called before call openCamera(context: Context).
     */
    fun setUpCameraOutputs(activity: Activity, width: Int, height: Int) {
        Log.d(TAG, "Begin setUpCameraOutputs")
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
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

            val characteristics = manager.getCameraCharacteristics(id)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (map == null) {
                Log.d(TAG, "Stream configuration map is null")
                return
            }

            val outputSizes = map.getOutputSizes(ImageFormat.JPEG).asList()
            outputSizes.forEach {
                Log.d(TAG, "Camera support output size: $it")
            }
            val largest = Collections.max(outputSizes, CompareSizeByArea())
            Log.d(TAG, "Camera largest size: $largest")

            // Initialize image processor
            mImageReader = ImageReader.newInstance(largest.width, largest.height, ImageFormat.JPEG, MAX_IMAGES)
            mImageReader!!.setOnImageAvailableListener(mImageAvailableListener, mBackgroundHandler)

            val displayRotation = activity.windowManager.defaultDisplay.rotation
            Log.d(TAG, "Display rotation: $displayRotation")
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            Log.d(TAG, "Sensor rotation: $mSensorOrientation")

            var swappedDimensions = false
            when (displayRotation) {
                Surface.ROTATION_0, Surface.ROTATION_180 -> {
                    if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                        swappedDimensions = true
                    }
                }
                Surface.ROTATION_90, Surface.ROTATION_270 -> {
                    if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                        swappedDimensions = true
                    }
                }
                else -> Log.d(TAG, "Display rotation is invalid")
            }

            val displaySize = Point()
            activity.windowManager.defaultDisplay.getSize(displaySize)
            Log.d(TAG, "Display size: $displaySize")
            var rotatedPreviewWidth = width
            var rotatedPreviewHeight = height
            var maxPreviewWidth = displaySize.x
            var maxPreviewHeight = displaySize.y

            if (swappedDimensions) {
                rotatedPreviewWidth = height
                rotatedPreviewHeight = width
                maxPreviewWidth = displaySize.y
                maxPreviewHeight = displaySize.x
            }

            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH
            }
            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT
            }

            val choices = map.getOutputSizes(SurfaceTexture::class.java)
            Log.d(TAG, "Choice size of SurfaceTexture for output size: ${choices.size}")
            choices.forEach {
                Log.d(TAG, "Choice for output size: $it")
            }
            mPreviewSize = chooseOptimalSize(choices, rotatedPreviewWidth, rotatedPreviewHeight,
                    maxPreviewWidth, maxPreviewHeight, largest)
            Log.d(TAG, "Camera preview size: $mPreviewSize")

            val orientation = activity.resources.configuration.orientation
            Log.d(TAG, "Context orientation: $orientation")
            if (mTextureView != null) {
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(mPreviewSize!!.width, mPreviewSize!!.height)
                } else {
                    mTextureView.setAspectRatio(mPreviewSize!!.height, mPreviewSize!!.width)
                }
            }

            mFlashSupported = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            Log.d(TAG, "Camera flash supported: $mFlashSupported")

            mSupportedAFModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            mSupportedAFModes.forEach {
                Log.d(TAG, "Supported camera AF MODE: $it")
            }
            mSupportedAEModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
            mSupportedAEModes.forEach {
                Log.d(TAG, "Supported camera AE MODE: $it")
            }

            mCameraId = id
        } catch (ex: CameraAccessException) {
            ex.printStackTrace()
        }

        Log.d(TAG, "End setUpCameraOutputs")
    }

    /**
     * Should be called after setUpCameraOutputs(...) if use TextureView to show camera preview.
     */
    fun configureTransform(activity: Activity, viewWidth: Int, viewHeight: Int) {
        Log.d(TAG, "Begin configureTransform")
        if (activity == null || mPreviewSize == null || mTextureView == null) {
            Log.d(TAG, "End configureTransform")
            return
        }
        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0F, 0F, viewWidth.toFloat(), viewHeight.toFloat())
        val buffRect = RectF(0F, 0F, mPreviewSize!!.width.toFloat(), mPreviewSize!!.height.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            buffRect.offset(centerX - buffRect.centerX(), centerY - buffRect.centerY())
            matrix.setRectToRect(viewRect, buffRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(viewHeight.toFloat() / mPreviewSize!!.height, viewWidth.toFloat() / mPreviewSize!!.width)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90 * (rotation - 2).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180F, centerX, centerY)
        }
        mTextureView.setTransform(matrix)
        Log.d(TAG, "End configureTransform")
    }

    fun openCamera(context: Context) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        // Open the camera resource
        try {
            Log.d(TAG, "Try open camera...")
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler)
        } catch (ex: CameraAccessException) {
            Log.d(TAG, "Open camera error", ex)
        }
    }

    fun shutDown() {
        mImageReader?.close()
        mCaptureSession?.close()
        mCameraDevice?.close()
    }

    fun tackPicture() {
        if (mCameraDevice == null || mImageReader == null) {
            Log.d(TAG, "Cannot capture image. Camera not initialized")
            return
        }
        try {
            mCameraDevice!!.createCaptureSession(Collections.singletonList(mImageReader!!.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigureFailed(session: CameraCaptureSession?) {
                            Log.d(TAG, "Failed to configure camera")
                        }

                        override fun onConfigured(session: CameraCaptureSession?) {
                            if (mCameraDevice == null) {
                                return
                            }
                            mCaptureSession = session
                            Log.d(TAG, "Session initialized")
                            triggerImageCapture()
                        }
                    }, null)
        } catch (ex: CameraAccessException) {
            Log.d(TAG, "Access exception while preparing picture", ex)
        }
    }

    private fun triggerImageCapture() {
        try {
            val captureBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(mImageReader!!.surface)
            setAutoFlash(captureBuilder)
            if (CaptureRequest.CONTROL_AE_MODE_ON in mSupportedAEModes) {
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            mState = STATE.STATE_PICTURE_TAKEN
            mCaptureSession!!.capture(captureBuilder.build(), mCaptureCallback, null)
        } catch (ex: CameraAccessException) {
            Log.d(TAG, "Camera capture exception", ex)
        }
    }

    private fun createPreviewSession() {
        if (mTextureView == null || mPreviewSize == null || mCameraDevice == null) {
            return
        }
        val texture = mTextureView.surfaceTexture!!
        texture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
        val surface = Surface(texture)

        mCameraDevice!!.createCaptureSession(Collections.singletonList(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.d(TAG, "Create preview capture session failed")
            }

            override fun onConfigured(session: CameraCaptureSession?) {
                if (mCameraDevice == null) {
                    return
                }
                mCaptureSession = session
                Log.d(TAG, "Camera preview session initialized")

                mPreviewRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                mPreviewRequestBuilder!!.addTarget(surface)
                setAutoFlash(mPreviewRequestBuilder!!)
                if (CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE in mSupportedAFModes) {
                    mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }
                mPreviewRequest = mPreviewRequestBuilder!!.build()
                mCaptureSession!!.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler)
            }
        }, null)
    }

    private fun setAutoFlash(requestBuilder: CaptureRequest.Builder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
        }
    }

    companion object {
        const val MAX_IMAGES = 2
        // Max preview width and height is guaranteed by Camera2 API
        const val MAX_PREVIEW_WIDTH = 1920
        const val MAX_PREVIEW_HEIGHT = 1080

        fun chooseOptimalSize(choices: Array<Size>, textureViewWidth: Int, textureViewHeight: Int,
                              maxWidth: Int, maxHeight: Int, aspectRatio: Size): Size {
            val bigEnough = ArrayList<Size>()
            val notBitEnough = ArrayList<Size>()
            val w = aspectRatio.width
            val h = aspectRatio.height
            choices.forEach {
                if (it.width <= maxWidth && it.height <= maxHeight
                        && it.height == it.width * h / w) {
                    if (it.width >= textureViewWidth && it.height >= textureViewHeight) {
                        bigEnough.add(it)
                    } else {
                        notBitEnough.add(it)
                    }
                }
            }
            return when {
                bigEnough.isNotEmpty() -> Collections.min(bigEnough, CompareSizeByArea())
                notBitEnough.isNotEmpty() -> Collections.max(notBitEnough, CompareSizeByArea())
                else -> {
                    Log.d(TAG, "Couldn't find any suitable preview size")
                    choices[0]
                }
            }
        }
    }
}