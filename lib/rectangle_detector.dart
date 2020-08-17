import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:meta/meta.dart';
import 'package:platform/platform.dart';

class RectangleDetector {
  final MethodChannel _channel;
  final Platform _platform;
  
  factory RectangleDetector() => _instance;
  
  @visibleForTesting
  RectangleDetector.private(MethodChannel channel, Platform platform)
      : _channel = channel,
        _platform = platform;

  static final RectangleDetector _instance = RectangleDetector.private(
      const MethodChannel('rectangle_detector'),
      const LocalPlatform());

  final StreamController<String> _croppedPathStreamController =
      StreamController<String>.broadcast();
  Stream<String> get onCroppedPathStreamReceived {
    return _croppedPathStreamController.stream;
  }

  void configure() {
    _channel.setMethodCallHandler(_handleMethod);
  }

  Future<dynamic> _handleMethod(MethodCall call) async {
    final methodName = '${call.method}';

    switch (methodName) {
      case 'onCroppedPictureCreated':
        if (_croppedPathStreamController != null) {
          _croppedPathStreamController.add(call.arguments);
        }

        return null;
      default:
        throw UnsupportedError("Unrecognized JSON message");
    }
  }

  Future showRectangleDetector() async {
    await _channel.invokeMethod('startDetector');
  }
}
