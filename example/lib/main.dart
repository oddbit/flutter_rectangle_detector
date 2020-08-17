import 'dart:io';

import 'package:flutter/material.dart';

import 'package:rectangle_detector/rectangle_detector.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _croppedPath = '';

  @override
  void initState() {
    super.initState();
    _listenToCroppedImageCreated();
    RectangleDetector().configure();
  }

  void _listenToCroppedImageCreated() {
    RectangleDetector().onCroppedPathStreamReceived.listen(
        (result) {
          setState(() {
            _croppedPath = result;
          });
    }, onError: (err) {
      print('Error');
      print(err);
    }, onDone: () {
      print('Done!');
    }, cancelOnError: false);
  }

  void _showDocumentScanner() async {
    final detector = RectangleDetector();
    await detector.showRectangleDetector();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Column(
            children: [
              if (_croppedPath.isNotEmpty)
                Image.file(File(_croppedPath)),
              Text(_croppedPath),
              MaterialButton(
                child: Text('Show Scanner'),
                onPressed: _showDocumentScanner,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
