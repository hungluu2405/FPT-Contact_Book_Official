import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

/// Chạy trước mọi test trong thư mục golden: nạp font Roboto và Material
/// Icons có sẵn trong Flutter SDK để ảnh chụp hiện chữ tiếng Việt và icon
/// thật thay cho ô vuông của font Ahem mặc định.
Future<void> testExecutable(FutureOr<void> Function() testMain) async {
  TestWidgetsFlutterBinding.ensureInitialized();
  final fontDir = _findSdkFontDir();
  // Chữ không ghi fontFamily sẽ dùng font test mặc định (ô vuông), nên mọi
  // TextStyle tự tạo trong app phải ghi fontFamily như theme.
  await _loadFont(fontDir, 'Roboto', [
    'roboto-regular.ttf',
    'roboto-medium.ttf',
    'roboto-bold.ttf',
    'roboto-black.ttf',
  ]);
  await _loadFont(fontDir, 'MaterialIcons', ['materialicons-regular.otf']);
  await testMain();
}

/// flutter_tester nằm trong `<FLUTTER_ROOT>/bin/cache/artifacts/engine/...`,
/// font nằm trong `<FLUTTER_ROOT>/bin/cache/artifacts/material_fonts`.
Directory _findSdkFontDir() {
  var dir = File(Platform.resolvedExecutable).parent;
  while (true) {
    final candidate = Directory(
      '${dir.path}${Platform.pathSeparator}material_fonts',
    );
    if (candidate.existsSync()) {
      return candidate;
    }
    final parent = dir.parent;
    if (parent.path == dir.path) {
      throw StateError(
        'material_fonts not found above ${Platform.resolvedExecutable}',
      );
    }
    dir = parent;
  }
}

Future<void> _loadFont(Directory dir, String family, List<String> files) async {
  final loader = FontLoader(family);
  for (final name in files) {
    final bytes = File('${dir.path}${Platform.pathSeparator}$name')
        .readAsBytesSync();
    loader.addFont(Future.value(ByteData.sublistView(bytes)));
  }
  await loader.load();
}
