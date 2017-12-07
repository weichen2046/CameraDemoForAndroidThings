package com.example.chenwei.androidthingscamerademo

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import com.example.chenwei.androidthingscamerademo.Constants.Companion.BUTTON_GPIO_PIN_1
import com.example.chenwei.androidthingscamerademo.Constants.Companion.BUTTON_GPIO_PIN_2
import com.example.chenwei.androidthingscamerademo.Constants.Companion.TAG
import com.google.android.things.contrib.driver.button.Button
import com.google.android.things.contrib.driver.button.ButtonInputDriver
import java.io.IOException

class CameraPreviewActivity : Activity() {

    private var mButtonDriver1: ButtonInputDriver? = null
    private var mButtonDriver2: ButtonInputDriver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_preview)
    }

    override fun onResume() {
        super.onResume()
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
}
