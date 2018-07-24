# Fast QR Reader View Plugin

**[See in pub](https://pub.dartlang.org/packages/fast_qr_reader_view)**

A Flutter plugin for iOS and Android allowing access to the device cameras to scan multiple type of codes (QR, PDF417, CODE39, etc). **Heavily based on [camera](https://pub.dartlang.org/packages/camera)**


<p align="center">
  <img src="https://raw.githubusercontent.com/facundomedica/fast_qr_reader_view/master/example.gif">
  <img src="https://raw.githubusercontent.com/facundomedica/fast_qr_reader_view/master/example2.gif">
</p>

*Red box is a Flutter animation (removable).* *Low FPS due to GIF*

## Features:

* Display live camera preview in a widget.
* Uses native AVFoundation code detection in iOS
* Uses ML Kit in Android

## Installation

First, add `fast_qr_reader_view` as a [dependency in your pubspec.yaml file](https://flutter.io/using-packages/).

### iOS

Add a row to the `ios/Runner/Info.plist` with the key `Privacy - Camera Usage Description` and a usage description.

Or in text format add the key:

```xml
<key>NSCameraUsageDescription</key>
<string>Can I use the camera please?</string>
```

### Android

Add Firebase to your project following [this step](https://codelabs.developers.google.com/codelabs/flutter-firebase/#5) (only that step, not the entire guide).

Change the minimum Android sdk version to 21 (or higher) in your `android/app/build.gradle` file.

```
minSdkVersion 21
```

### Example

Here is a small example flutter app displaying a full screen camera preview.

```dart
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:fast_qr_reader_view/fast_qr_reader_view.dart';

List<CameraDescription> cameras;

Future<Null> main() async {
  cameras = await availableCameras();
  runApp(new CameraApp());
}

class CameraApp extends StatefulWidget {
  @override
  _CameraAppState createState() => new _CameraAppState();
}

class _CameraAppState extends State<CameraApp> {
  QRReaderController controller;

  @override
  void initState() {
    super.initState();
    controller = new QRReaderController(cameras[0], ResolutionPreset.medium, [CodeFormat.qr], (dynamic value){
        print(value); // the result!
    // ... do something
    // wait 3 seconds then start scanning again.
    new Future.delayed(const Duration(seconds: 3), controller.startScanning);
    });
    controller.initialize().then((_) {
      if (!mounted) {
        return;
      }
      setState(() {});
      controller.startScanning();
    });
  }

  @override
  void dispose() {
    controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!controller.value.isInitialized) {
      return new Container();
    }
    return new AspectRatio(
        aspectRatio:
        controller.value.aspectRatio,
        child: new QRReaderPreview(controller));
  }
}
```

For a more elaborate usage example see [here](https://github.com/facundomedica/fast_qr_reader_view/tree/master/example).

*Note*: This plugin is still under development, and some APIs might not be available yet.
[Feedback welcome](https://github.com/facundomedica/fast_qr_reader_view/issues) and
[Pull Requests](https://github.com/facundomedica/fast_qr_reader_view/pulls) are most welcome!
