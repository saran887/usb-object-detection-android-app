 # Android USB Camera with Object Detection & Voice

This app lets you:
- Access a USB camera on Android (OTG required)
- Run real-time object detection on the camera feed
- Hear detected object names spoken aloud (TextToSpeech)

## Quick Start
1. Place your TFLite model and `labelmap.txt` in `app/src/main/assets/`.
2. Build and run the app on an Android device with USB OTG support.
3. Connect a USB camera. The app will show the camera preview, detect objects, and speak their names.

## How it works

1. The app detects and opens a USB camera when connected to your Android device (OTG required).
2. The camera preview is displayed on screen in real time.
3. Each frame from the camera is processed by a TensorFlow Lite object detection model.
4. Detected objects are shown as overlays on the camera preview.
5. The label (name) of the detected object is spoken aloud using Android's TextToSpeech engine. If the same object is detected repeatedly, the voice output is throttled to avoid repetition.

## Notes
- To update detection, replace the `.tflite` model and `labelmap.txt` in assets.
- Only the above features are included; all other features have been removed for simplicity.
