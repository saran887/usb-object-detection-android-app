# USB Multi-Camera Object Detection Android App

**Version 1.0** - Enhanced multi-camera object detection app with automatic cycling and voice feedback.

## 🌟 Features

### 📹 Multi-Camera Support
- **Up to 4 USB cameras** simultaneously connected
- **5-second automatic cycling** between working cameras
- **Manual camera selection** with visual feedback
- **Smart camera management** with device blacklisting

### 🔍 Object Detection
- **Real-time TensorFlow Lite** object detection
- **Enhanced frame processing** with 300x300 input resize
- **Confidence-based filtering** for accurate detections
- **Visual bounding boxes** with object labels

### 🔊 Voice Feedback
- **Text-to-Speech announcements** for detected objects
- **Smart voice management** with throttling to prevent spam
- **Active camera voice feedback** (works for all cameras during cycling)

### 🛡️ Crash Prevention & Stability
- **Comprehensive error handling** for device failures
- **Fragment lifecycle safety** for all operations
- **Automatic recovery** from USB connection issues
- **Device compatibility checking** with graceful fallbacks

### 🎛️ User Interface
- **Clean, minimal UI** with essential controls only
- **Real-time camera status** indicators
- **Failed device visual feedback** (red buttons for blacklisted devices)
- **Automatic camera activation** (Camera 1 default)

## 🚀 Quick Start

### Prerequisites
- Android device with **USB OTG support**
- USB cameras (up to 4 supported)
- USB hub (recommended for multiple cameras)

### Setup
1. **Install APK** on your Android device
2. **Connect USB cameras** via OTG cable/hub
3. **Grant USB permissions** when prompted
4. **Camera 1 activates automatically** after 2 seconds
5. **5-second cycling starts** when 2+ cameras are working

### Assets Required
- Place your `.tflite` model in `app/src/main/assets/`
- Include `labelmap.txt` or `labels.txt` for object labels
- Current model: Optimized for 300x300 input resolution

## 🔧 How It Works

### Camera Management
1. **USB Detection**: Automatically detects connected USB cameras
2. **Permission Handling**: Requests and manages USB permissions
3. **Device Configuration**: Uses device-specific camera settings
4. **Health Monitoring**: Continuously monitors camera status
5. **Error Recovery**: Handles device failures gracefully

### Automatic Cycling System
- **Smart Activation**: Only starts with 2+ working cameras
- **5-Second Intervals**: Each camera displays for 5 seconds
- **Manual Override**: Click any camera button to pause cycling for 10 seconds
- **Auto-Resume**: Cycling resumes automatically after manual selection
- **Blacklist Filtering**: Failed devices excluded from cycling

### Object Detection Pipeline
1. **Frame Capture**: USB camera provides raw frames
2. **Image Processing**: Resize to 300x300 for model input
3. **TensorFlow Lite**: Run inference on processed frame
4. **Result Processing**: Filter detections by confidence threshold
5. **UI Update**: Draw bounding boxes and labels
6. **Voice Output**: Announce detected objects via TTS

### Error Handling & Recovery
- **USB Connection Monitoring**: Detects disconnections and reconnections
- **Device Failure Detection**: Identifies problematic cameras
- **Automatic Blacklisting**: Removes failed devices from rotation
- **Alternative Camera Switching**: Finds working cameras when primary fails
- **Fragment Lifecycle Safety**: Prevents crashes during state changes

## 📱 Supported Devices

### Tested Configurations
- **Device 1003** (Vendor: 3141, Product: 25771) - ✅ Fully Compatible
- **Device 1004** (Vendor: 13468, Product: 1041) - ⚠️ Limited Compatibility

### Camera Requirements
- **USB Video Class (UVC)** compatible cameras
- **Standard resolutions** supported (320x240 to 1024x768)
- **MJPEG or YUYV** format support
- **USB 2.0+** connection

## 🛠️ Technical Details

### Architecture
- **Fragment-based UI** with lifecycle safety
- **Multi-threaded processing** for smooth performance
- **Handler-based scheduling** for camera operations
- **Memory-efficient** frame processing

### Dependencies
- **AndroidUSBCamera** library for USB camera access
- **TensorFlow Lite** for object detection
- **Android TextToSpeech** for voice feedback
- **Kotlin Coroutines** for asynchronous operations

### Performance Optimizations
- **Bandwidth management** for multi-camera scenarios
- **Frame rate optimization** per device capability
- **Memory pooling** for efficient processing
- **Smart resolution selection** based on device performance

## 🔄 Version History

### Version 1.0 (Current)
- ✅ Multi-camera support with automatic cycling
- ✅ Enhanced object detection with MainActivity.kt-style processing
- ✅ Comprehensive crash prevention system
- ✅ Smart device management with blacklisting
- ✅ Voice feedback for all cameras
- ✅ Clean UI with essential controls only

## 🤝 Contributing

This project is actively maintained. Key areas for contribution:
- **Camera compatibility** testing with various USB cameras
- **Performance optimization** for resource-constrained devices
- **UI/UX improvements** for better user experience
- **Additional object detection models** integration

## 📄 Build Instructions

```bash
# Clone the repository
git clone <repository-url>

# Open in Android Studio
# Build debug APK
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 🔍 Troubleshooting

### Common Issues
- **Camera not detected**: Check USB OTG support and cable
- **Permission denied**: Ensure USB debugging is enabled
- **App crashes**: Check logs for device compatibility issues
- **No voice output**: Verify TTS engine is installed

### Debug Logs
The app provides comprehensive logging with tags:
- `DemoMultiCameraFragment`: Main camera operations
- `ObjectDetectorHelper`: Object detection processing
- `CameraConfig`: Device-specific configurations

---

**Built with ❤️ for reliable multi-camera object detection on Android**
