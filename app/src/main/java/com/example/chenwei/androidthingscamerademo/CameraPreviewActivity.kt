package com.example.chenwei.androidthingscamerademo

import android.app.Activity
import android.graphics.SurfaceTexture
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.KeyEvent
import android.view.TextureView
import com.example.chenwei.androidthingscamerademo.Constants.Companion.BUTTON_GPIO_PIN_1
import com.example.chenwei.androidthingscamerademo.Constants.Companion.BUTTON_GPIO_PIN_2
import com.example.chenwei.androidthingscamerademo.Constants.Companion.TAG
import com.google.android.things.contrib.driver.button.Button
import com.google.android.things.contrib.driver.button.ButtonInputDriver
import java.io.IOException
import java.util.concurrent.Semaphore

class CameraPreviewActivity : Activity() {

    private lateinit var mCameraHandler: Handler
    private lateinit var mCameraThread: HandlerThread

    private lateinit var mCamera: DemoCamera
    private val mCameraOpenCloseLock: Semaphore = Semaphore(1)

    private var mButtonDriver1: ButtonInputDriver? = null
    private var mButtonDriver2: ButtonInputDriver? = null

    private lateinit var mTextureView: AutoFitTextureView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_preview)
        mTextureView = findViewById(R.id.texture)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        startBackgroundThread()
        mCamera = DemoCamera(mOnImageAvailableListener, mCameraHandler, mTextureView)

        if (mTextureView.isAvailable) {
            startCameraPreview(mTextureView.width, mTextureView.height)
        } else {
            mTextureView.surfaceTextureListener = mSurfaceTextureListener
        }

        // Initial button driver.
        try {

            mButtonDriver1 = ButtonInputDriver(BUTTON_GPIO_PIN_1, Button.LogicState.PRESSED_WHEN_LOW,
                    KeyEvent.KEYCODE_A)
        } catch (ex: IOException) {
            Log.d(TAG, "Button1 error when create button driver.")
        }
        mButtonDriver1?.register()

        try {

            mButtonDriver2 = ButtonInputDriver(BUTTON_GPIO_PIN_2, Button.LogicState.PRESSED_WHEN_LOW,
                    KeyEvent.KEYCODE_B)
        } catch (ex: IOException) {
            Log.d(TAG, "Button2 error when create button driver.")
        }
        mButtonDriver2?.register()
    }

    override fun onPause() {
        Log.d(TAG, "onPause")
        mButtonDriver1?.unregister()
        try {
            mButtonDriver1?.close()
        } catch (ex: IOException) {
            Log.d(TAG, "Button1 error when close button driver.")
        }
        mButtonDriver1 = null

        mButtonDriver2?.unregister()
        try {
            mButtonDriver2?.close()
        } catch (ex: IOException) {
            Log.d(TAG, "Button2 error when close button driver.")
        }
        mButtonDriver2 = null

        mCamera.shutDown()
        stopBackgroundThread()

        super.onPause()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_A) {
            // TODO: start camera preview
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_B) {
            finish()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private val mSurfaceTextureListener = object: TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            Log.d(TAG, "onSurfaceTextureSizeChanged, width: $width, height: $height")
            mCamera.configureTransform(this@CameraPreviewActivity, width, height)
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            Log.d(TAG, "onSurfaceTextureAvailable, width: $width, height: $height")
            startCameraPreview(width, height)
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
            Log.d(TAG, "onSurfaceTextureUpdated")
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            Log.d(TAG, "onSurfaceTextureDestroyed")
            return true
        }
    }

    private fun startCameraPreview(width: Int, height: Int) {
        mCamera.setUpCameraOutputs(this, width, height)
        mCamera.configureTransform(this, width, height)
        mCamera.openCamera(this)
    }

    private fun startBackgroundThread() {
        mCameraThread = HandlerThread("CameraBackground")
        mCameraThread.start()
        mCameraHandler = Handler(mCameraThread.looper)
    }

    private fun stopBackgroundThread() {
        mCameraThread.quitSafely()
        try {
            mCameraThread.join()
        } catch (ex: InterruptedException) {
            ex.printStackTrace()
        }
    }

    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        Log.d(TAG, "Image available now")
    }
}
