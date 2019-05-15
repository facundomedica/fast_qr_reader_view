import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

final MethodChannel _channel = const MethodChannel('fast_qr_reader_view')..invokeMethod('init');

enum CameraLensDirection { front, back, external }

enum ResolutionPreset { low, medium, high }

enum CodeFormat { codabar, code39, code93, code128, ean8, ean13, itf, upca, upce, aztec, datamatrix, pdf417, qr }

var _availableFormats = {
  CodeFormat.codabar: 'codabar', // Android only
  CodeFormat.code39: 'code39',
  CodeFormat.code93: 'code93',
  CodeFormat.code128: 'code128',
  CodeFormat.ean8: 'ean8',
  CodeFormat.ean13: 'ean13',
  CodeFormat.itf: 'itf', // itf-14 on iOS, should be changed to Interleaved2of5?
  CodeFormat.upca: 'upca', // Android only
  CodeFormat.upce: 'upce',
  CodeFormat.aztec: 'aztec',
  CodeFormat.datamatrix: 'datamatrix',
  CodeFormat.pdf417: 'pdf417',
  CodeFormat.qr: 'qr',
};

/// Returns the resolution preset as a String.
String serializeResolutionPreset(ResolutionPreset resolutionPreset) {
  switch (resolutionPreset) {
    case ResolutionPreset.high:
      return 'high';
    case ResolutionPreset.medium:
      return 'medium';
    case ResolutionPreset.low:
      return 'low';
  }
  throw new ArgumentError('Unknown ResolutionPreset value');
}

List<String> serializeCodeFormatsList(List<CodeFormat> formats) {
  List<String> list = [];

  for (var i = 0; i < formats.length; i++) {
    if (_availableFormats[formats[i]] != null) {
      //  this format exists in my list of available formats
      list.add(_availableFormats[formats[i]]);
    }
  }

  return list;
}

CameraLensDirection _parseCameraLensDirection(String string) {
  switch (string) {
    case 'front':
      return CameraLensDirection.front;
    case 'back':
      return CameraLensDirection.back;
    case 'external':
      return CameraLensDirection.external;
  }
  throw new ArgumentError('Unknown CameraLensDirection value');
}

/// Completes with a list of available cameras.
///
/// May throw a [QRReaderException].
Future<List<CameraDescription>> availableCameras() async {
  try {
    final List<dynamic> cameras = await _channel.invokeMethod('availableCameras');
    return cameras.map((dynamic camera) {
      return new CameraDescription(
        name: camera['name'],
        lensDirection: _parseCameraLensDirection(camera['lensFacing']),
      );
    }).toList();
  } on PlatformException catch (e) {
    throw new QRReaderException(e.code, e.message);
  }
}

/// Checks the current status of the Camera Permission
///
/// returns: [Future<PermissionStatus>] with the status from the check
Future<PermissionStatus> checkCameraPermission() async {
  try {
    print("checkCameraPermission()");
    var permission = await _channel.invokeMethod('checkPermission') as String;
    print("Permission: $permission");
    return _getPermissionStatus(permission);
  } on PlatformException catch (e) {
    print("Error while permissions");
    return Future.value(PermissionStatus.unknown);
  }
}

/// Requests the camera permission
///
/// returns: [Future<PermissionStatus>] with the status from the request
Future<PermissionStatus> requestCameraPermission() async {
  try {
    var result = await _channel.invokeMethod('requestPermission');
    switch (result) {
      case "denied":
        return PermissionStatus.denied;
      case "dismissedForever":
        return PermissionStatus.dismissedForever;
      case "granted":
        return PermissionStatus.granted;
      default:
        return PermissionStatus.unknown;
    }
  } on PlatformException catch (e) {
    return Future.value(PermissionStatus.unknown);
  }
}

/// Gets the PermissionStatus from the channel Method
///
/// Given a [String] status from the method channel, it returns a
/// [PermissionStatus]
PermissionStatus _getPermissionStatus(String status) {
  switch (status) {
    case "denied":
      return PermissionStatus.denied;
    case "dismissedForever":
      return PermissionStatus.dismissedForever;
    case "granted":
      return PermissionStatus.granted;
    case "restricted":
      return PermissionStatus.restricted;
    default:
      return PermissionStatus.unknown;
  }
}

/// Opens the native settings screen
///
/// Opens the native iOS or Android settings screens for the current app,
/// So that the user can give the app permission even if he has denied them
Future<void> openSettings() {
  try {
    return _channel.invokeMethod('settings');
  } on PlatformException catch (e) {
    return Future.error(e);
  }
}

/// Enum to give us the status of the Permission request/check
enum PermissionStatus {
  granted,

  /// Permission to access the requested feature is denied by the user.
  denied,

  /// The feature is disabled (or not available) on the device.
  disabled,

  /// Permission to access the requested feature is granted by the user.
  dismissedForever,

  /// The user granted restricted access to the requested feature (only on iOS).
  restricted,

  /// Permission is in an unknown state
  unknown
}

class CameraDescription {
  final String name;
  final CameraLensDirection lensDirection;

  CameraDescription({this.name, this.lensDirection});

  @override
  bool operator ==(Object o) {
    return o is CameraDescription && o.name == name && o.lensDirection == lensDirection;
  }

  @override
  int get hashCode {
    return hashValues(name, lensDirection);
  }

  @override
  String toString() {
    return '$runtimeType($name, $lensDirection)';
  }
}

/// This is thrown when the plugin reports an error.
class QRReaderException implements Exception {
  String code;
  String description;

  QRReaderException(this.code, this.description);

  @override
  String toString() => '$runtimeType($code, $description)';
}

// Build the UI texture view of the video data with textureId.
class QRReaderPreview extends StatelessWidget {
  final QRReaderController controller;

  const QRReaderPreview(this.controller);

  @override
  Widget build(BuildContext context) {
    return controller.value.isInitialized ? new Texture(textureId: controller._textureId) : new Container();
  }
}

/// The state of a [QRReaderController].
class QRReaderValue {
  /// True after [QRReaderController.initialize] has completed successfully.
  final bool isInitialized;

  /// True when the camera is scanning.
  final bool isScanning;

  final String errorDescription;

  /// The size of the preview in pixels.
  ///
  /// Is `null` until  [isInitialized] is `true`.
  final Size previewSize;

  const QRReaderValue({
    this.isInitialized,
    this.errorDescription,
    this.previewSize,
    this.isScanning,
  });

  const QRReaderValue.uninitialized()
      : this(
          isInitialized: false,
          isScanning: false,
        );

  /// Convenience getter for `previewSize.height / previewSize.width`.
  ///
  /// Can only be called when [initialize] is done.
  double get aspectRatio => previewSize.height / previewSize.width;

  bool get hasError => errorDescription != null;

  QRReaderValue copyWith({
    bool isInitialized,
    bool isScanning,
    String errorDescription,
    Size previewSize,
  }) {
    return new QRReaderValue(
      isInitialized: isInitialized ?? this.isInitialized,
      errorDescription: errorDescription,
      previewSize: previewSize ?? this.previewSize,
      isScanning: isScanning ?? this.isScanning,
    );
  }

  @override
  String toString() {
    return '$runtimeType('
        'isScanning: $isScanning, '
        'isInitialized: $isInitialized, '
        'errorDescription: $errorDescription, '
        'previewSize: $previewSize)';
  }
}

/// Controls a QR Reader
///
/// Use [availableCameras] to get a list of available cameras.
///
/// Before using a [QRReaderController] a call to [initialize] must complete.
///
/// To show the camera preview on the screen use a [QRReaderPreview] widget.
class QRReaderController extends ValueNotifier<QRReaderValue> {
  final CameraDescription description;
  final ResolutionPreset resolutionPreset;
  final Function onCodeRead;
  final List<CodeFormat> codeFormats;

  int _textureId;
  bool _isDisposed = false;
  StreamSubscription<dynamic> _eventSubscription;
  Completer<Null> _creatingCompleter;

  QRReaderController(this.description, this.resolutionPreset, this.codeFormats, this.onCodeRead)
      : super(const QRReaderValue.uninitialized());

  /// Initializes the camera on the device.
  ///
  /// Throws a [QRReaderException] if the initialization fails.
  Future<Null> initialize() async {
    if (_isDisposed) {
      return new Future<Null>.value(null);
    }
    try {
      _channel.setMethodCallHandler(_handleMethod);
      _creatingCompleter = new Completer<Null>();
      final Map<dynamic, dynamic> reply = await _channel.invokeMethod(
        'initialize',
        <String, dynamic>{
          'cameraName': description.name,
          'resolutionPreset': serializeResolutionPreset(resolutionPreset),
          'codeFormats': serializeCodeFormatsList(codeFormats),
        },
      );
      _textureId = reply['textureId'];
      value = value.copyWith(
        isInitialized: true,
        previewSize: new Size(
          reply['previewWidth'].toDouble(),
          reply['previewHeight'].toDouble(),
        ),
      );
    } on PlatformException catch (e) {
      throw new QRReaderException(e.code, e.message);
    }
    _eventSubscription =
        new EventChannel('fast_qr_reader_view/cameraEvents$_textureId').receiveBroadcastStream().listen(_listener);
    _creatingCompleter.complete(null);
    return _creatingCompleter.future;
  }

  /// Listen to events from the native plugins.
  ///
  /// A "cameraClosing" event is sent when the camera is closed automatically by the system (for example when the app go to background). The plugin will try to reopen the camera automatically but any ongoing recording will end.
  void _listener(dynamic event) {
    final Map<dynamic, dynamic> map = event;
    if (_isDisposed) {
      return;
    }

    switch (map['eventType']) {
      case 'error':
        value = value.copyWith(errorDescription: event['errorDescription']);
        break;
      case 'cameraClosing':
        value = value.copyWith(isScanning: false);
        break;
    }
  }

  /// Toggle flashlight
  Future<Null> toggleFlash() async {
    try {
      value = value.copyWith(isScanning: false);
      await _channel.invokeMethod(
        'toggleFlash',
        <String, dynamic>{'textureId': _textureId},
      );
    } on PlatformException catch (e) {
      throw new QRReaderException(e.code, e.message);
    }
  }

  /// Start a QR scan.
  ///
  /// Throws a [QRReaderException] if the capture fails.
  Future<Null> startScanning() async {
    if (!value.isInitialized || _isDisposed) {
      throw new QRReaderException(
        'Uninitialized QRReaderController',
        'startScanning was called on uninitialized QRReaderController',
      );
    }
    if (value.isScanning) {
      throw new QRReaderException(
        'A scan has already started.',
        'startScanning was called when a recording is already started.',
      );
    }
    try {
      value = value.copyWith(isScanning: true);
      await _channel.invokeMethod(
        'startScanning',
        <String, dynamic>{'textureId': _textureId},
      );
    } on PlatformException catch (e) {
      throw new QRReaderException(e.code, e.message);
    }
  }

  /// Stop scanning.
  Future<Null> stopScanning() async {
    if (!value.isInitialized || _isDisposed) {
      throw new QRReaderException(
        'Uninitialized QRReaderController',
        'stopScanning was called on uninitialized QRReaderController',
      );
    }
    if (!value.isScanning) {
      throw new QRReaderException(
        'No scanning is happening',
        'stopScanning was called when the scanner was not scanning.',
      );
    }
    try {
      value = value.copyWith(isScanning: false);
      await _channel.invokeMethod(
        'stopScanning',
        <String, dynamic>{'textureId': _textureId},
      );
    } on PlatformException catch (e) {
      throw new QRReaderException(e.code, e.message);
    }
  }

  /// Releases the resources of this camera.
  @override
  Future<Null> dispose() async {
    if (_isDisposed) {
      return new Future<Null>.value(null);
    }
    _isDisposed = true;
    super.dispose();
    if (_creatingCompleter == null) {
      return new Future<Null>.value(null);
    } else {
      return _creatingCompleter.future.then((_) async {
        await _channel.invokeMethod(
          'dispose',
          <String, dynamic>{'textureId': _textureId},
        );
        await _eventSubscription?.cancel();
      });
    }
  }

  Future<dynamic> _handleMethod(MethodCall call) async {
    switch (call.method) {
      case "updateCode":
        if (value.isScanning) {
          onCodeRead(call.arguments);
          value = value.copyWith(isScanning: false);
        }
    }
  }
}

class FastQrReaderView {
  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }
}
