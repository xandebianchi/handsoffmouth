# Hands Off Mouth App

## Overview
This is an Android application that detects when a user's hand is near their mouth using the camera and plays a notification sound to alert them. The app utilizes **CameraX**, **MediaPipe**, and *Jetpack Compose** for real-time detection and a smooth user interface.

## Features
- **Real-time hand and face detection** using MediaPipe.
- **Adjustable detection frequency** for better performance.
- **User-friendly UI with alerts** when hand-to-mouth movement is detected.
- **Uses Jetpack Compose** for a modern and reactive UI.

## Installation
### Prerequisites
- Android Studio (latest version recommended)
- Android device or emulator with camera support
- Minimum SDK: **API Level 30 (Android 11.0) or higher**

### Steps
1. Clone this repository:
   ```sh
   git clone https://github.com/xandebianchi/handsoffmouth.git
   ```
2. Open the project in Android Studio.
3. Build and run the app on a physical device or emulator.

## Usage
1. **Grant Camera Permission**: The app requires access to the camera to function.
2. **Start Monitoring**: The app will detect if the user's hand is near their mouth.
3. **Receive Alerts**: If a hand is detected near the mouth, an alert will be triggered.

## Configuration
- Uses **CameraX** to handle camera input efficiently.

## Troubleshooting
### Common Issues
- **Camera Permission Not Granted**
    - Ensure that camera permissions are enabled in your device settings.
- **No Hand Detection**
    - Try adjusting the lighting or positioning the camera properly.
- **Sound Not Playing**
    - Check device volume and ensure audio permissions are granted.

## Download Release
The latest stable version of the app can be downloaded from the [Releases Page](https://github.com/xandebianchi/handsoffmouth/releases/latest).

## Contributing
Feel free to submit issues and pull requests to improve the app. Contributions are welcome!

## License
This project is licensed under the [MIT License](LICENSE).

