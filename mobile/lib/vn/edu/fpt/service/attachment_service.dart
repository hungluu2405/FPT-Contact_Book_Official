import 'dart:io';

import 'package:flutter/services.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

/// Mở một tệp trên máy bằng ứng dụng ngoài (xem ảnh, đọc PDF...).
typedef FileOpener = Future<OpenResult> Function(String path);

/// Tải tệp đính kèm về thư mục cache của app rồi mở bằng ứng dụng có sẵn
/// trên máy. Tệp chỉ nằm tạm trong cache (máy tự dọn khi thiếu chỗ) và bị
/// xóa khi đăng xuất hoặc vào lại app qua [clearCache]. Lỗi ném [ApiErrorModel].
class AttachmentService {
  static const String _folder = 'attachments';
  static const String openFailedCode = 'OPEN_FAILED';
  static const String saveFailedCode = 'SAVE_FAILED';
  static const String saveFailedMessage =
      'Không lưu được tệp trên máy, vui lòng kiểm tra bộ nhớ rồi thử lại.';
  static const String noAppMessage = 'Máy chưa có ứng dụng mở được tệp này.';
  static const String openFailedMessage =
      'Không mở được tệp, vui lòng thử lại.';

  final ApiClient _apiClient;
  final Future<Directory> Function() _directory;
  final FileOpener _opener;

  AttachmentService(
    this._apiClient, {
    Future<Directory> Function()? directory,
    FileOpener? opener,
  }) : _directory = directory ?? getTemporaryDirectory,
       _opener = opener ?? ((path) => OpenFilex.open(path));

  /// Tải tệp ở [apiPath] (ví dụ `/notifications/1/files/11`) và mở. Mỗi
  /// đường dẫn một thư mục riêng nên hai tệp trùng tên không đè nhau.
  Future<void> downloadAndOpen(String apiPath, String fileName) async {
    final bytes = await _apiClient.getBytes(apiPath);
    OpenResult result;
    try {
      final root = await _directory();
      final folder = Directory(
        '${root.path}/$_folder/${apiPath.replaceAll(RegExp(r'[^A-Za-z0-9]+'), '_')}',
      );
      // Tệp tối đa vài MB nên ghi đồng bộ, tránh phụ thuộc vòng lặp sự kiện.
      folder.createSync(recursive: true);
      final file = File('${folder.path}/${safeFileName(fileName)}')
        ..writeAsBytesSync(bytes, flush: true);
      result = await _opener(file.path);
    } on FileSystemException {
      throw ApiErrorModel(
        status: 0,
        code: saveFailedCode,
        message: saveFailedMessage,
      );
    } on PlatformException {
      result = OpenResult(type: ResultType.error);
    }
    if (result.type != ResultType.done) {
      throw ApiErrorModel(
        status: 0,
        code: openFailedCode,
        message: result.type == ResultType.noAppToOpen
            ? noAppMessage
            : openFailedMessage,
      );
    }
  }

  /// Xóa mọi tệp đã tải của phiên trước.
  Future<void> clearCache() async {
    try {
      final folder = Directory('${(await _directory()).path}/$_folder');
      if (folder.existsSync()) {
        folder.deleteSync(recursive: true);
      }
    } on FileSystemException {
      // Không xóa được thì để hệ điều hành tự dọn cache.
    }
  }

  /// Tên tệp an toàn để ghi: bỏ ký tự đường dẫn và ký tự Android không nhận.
  static String safeFileName(String name) {
    final cleaned = name
        .replaceAll(RegExp(r'[\\/:*?"<>|\x00-\x1F]'), '_')
        .trim();
    if (cleaned.isEmpty || cleaned == '.' || cleaned == '..') {
      return 'tep-dinh-kem';
    }
    return cleaned;
  }
}
