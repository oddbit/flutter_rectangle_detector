import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const MethodChannel channel = MethodChannel('rectangle_detector');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // set channel mock
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });
}
