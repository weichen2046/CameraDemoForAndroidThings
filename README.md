# CameraDemoForAndroidThings

Camera demo for android things, current support capture still picture as well as camera preview.

## Schematics

![Camera demo for android things](./CameraDemoForAndroidThings_bb.png)

## Camera preview support

Key process of setup camera preview extract from example [Android Camera2 Example][AndroidCamera2].

In order to support camera preview, you should enable hardware acceleration for your `Activity` or `Application`. More information about hardware acceleration, please refer to [Android Things Realse Notes][AndroidThingsRleaseNotes] and [Android Hardware Acceleration][HardwareAcceleration].

## Capture still picutre support

Key process of capture still picture extract from example [Doorbell][Doorbell].

## Details

We have two buttons for difference purpose.

In `MainActivity`:

- Button A: start `TakePictureActivity`
- Button B: start `CameraPreviewActivity`

In `TakePictureActivity`:

- Button A: trigger take still picture
- Button B: return to `MainActivity`

In `CameraPreviewActivity`:

- Button A: current not defined (*maybe trigger take still picture while preview is going on.*)
- Button B: return to `MainActivity`


<!-- lins -->

[Doorbell]: https://github.com/androidthings/doorbell
[AndroidCamera2]: [https://github.com/googlesamples/android-Camera2Basic]
[AndroidThingsRleaseNotes]: https://developer.android.com/things/preview/releases.html
[HardwareAcceleration]: https://developer.android.com/guide/topics/graphics/hardware-accel.html
