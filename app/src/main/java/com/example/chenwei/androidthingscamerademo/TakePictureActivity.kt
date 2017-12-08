package com.example.chenwei.androidthingscamerademo

import android.app.Activity
import android.graphics.BitmapFactory
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.KeyEvent
import android.widget.ImageView
import com.example.chenwei.androidthingscamerademo.Constants.Companion.BUTTON_GPIO_PIN_1
import com.example.chenwei.androidthingscamerademo.Constants.Companion.BUTTON_GPIO_PIN_2
import com.example.chenwei.androidthingscamerademo.Constants.Companion.TAG
import com.google.android.things.contrib.driver.button.Button
import com.google.android.things.contrib.driver.button.ButtonInputDriver
import java.io.IOException

/**
 * Skeleton of an Android Things activity.
 *
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 *
 * <pre>{@code
 * val service = PeripheralManagerService()
 * val mLedGpio = service.openGpio("BCM6")
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
 * mLedGpio.value = true
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 *
 */
class TakePictureActivity : Activity() {

    private lateinit var mCameraHandler: Handler
    private lateinit var mCameraThread: HandlerThread

    private lateinit var mCamera: DemoCamera

    private var mButtonDriver1: ButtonInputDriver? = null
    private var mButtonDriver2: ButtonInputDriver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        mCamera = DemoCamera(mOnImageAvailableListener, mCameraHandler)
        mCamera.setUpCameraOutputs(this, 0, 0)
        mCamera.openCamera(this)

        // Initial button driver.
        try {
            mButtonDriver1 = ButtonInputDriver(BUTTON_GPIO_PIN_1, Button.LogicState.PRESSED_WHEN_LOW,
                    KeyEvent.KEYCODE_ENTER)
        } catch (ex: IOException) {
            Log.d(TAG, "Button1 error when create button driver.", ex)
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
        super.onPause()
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
        stopBackgroudThread()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "KeyUp event, keyCode: $keyCode")
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            Log.d(TAG, "Button released.")
            mCamera.tackPicture()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_B) {
            finish()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        val imageBuf = image.planes[0].buffer
        val imageBytes = ByteArray(imageBuf.remaining())
        imageBuf.get(imageBytes)
        image.close()
        onPictureTaken(imageBytes)
    }

    private fun onPictureTaken(imageBytes: ByteArray) {
        // Process the captured image...
        Log.d(TAG, "Picture taken, size: ${imageBytes.size}")
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        runOnUiThread {
            val imageView = findViewById<ImageView>(R.id.imageView)
            imageView.setImageBitmap(bitmap)
        }
    }

    private fun startBackgroundThread() {
        mCameraThread = HandlerThread("CameraBackground")
        mCameraThread.start()
        mCameraHandler = Handler(mCameraThread.looper)
    }

    private fun stopBackgroudThread() {
        mCameraThread.quitSafely()
        try {
            mCameraThread.join()
        } catch (ex: InterruptedException) {
            ex.printStackTrace()
        }
    }
}

