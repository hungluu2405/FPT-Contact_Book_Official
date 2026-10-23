import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/picked_file_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_clock.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/leave_rule.dart';
import 'package:image_picker/image_picker.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

/// Mở một tệp trên máy bằng ứng dụng ngoài (xem ảnh, đọc PDF...).
typedef FileOpener = Future<OpenResult> Function(String path);

/// Mở trình chọn tệp của máy; danh sách rỗng khi người dùng hủy.
typedef FilesPicker = Future<List<PickedFileModel>> Function();

/// Mở máy ảnh để chụp một ảnh; null khi người dùng hủy.
typedef PhotoTaker = Future<PickedFileModel?> Function();

/// Tải tệp đính kèm về thư mục cache của app rồi mở bằng ứng dụng có sẵn
/// trên máy. Tệp chỉ nằm tạm trong cache (máy tự dọn khi thiếu chỗ) và bị
/// xóa khi đăng xuất hoặc vào lại app qua [clearCache]. Chọn tệp và chụp
/// ảnh minh chứng cho đơn nghỉ cũng đi qua lớp này. Lỗi ném [ApiErrorModel].
class AttachmentService {
  static const String _folder = 'attachments';
  static const String openFailedCode = 'OPEN_FAILED';
  static const String saveFailedCode = 'SAVE_FAILED';
  static const String saveFailedMessage =
      'Không lưu được tệp trên máy, vui lòng kiểm tra bộ nhớ rồi thử lại.';
  static const String noAppMessage = 'Máy chưa có ứng dụng mở được tệp này.';
  static const String openFailedMessage =
      'Không mở được tệp, vui lòng thử lại.';
  static const String pickFailedCode = 'PICK_FAILED';
  static const String pickFailedMessage =
      'Không mở được máy ảnh hoặc thư mục tệp, vui lòng kiểm tra quyền '
      'truy cập của ứng dụng.';

  final ApiClient _apiClient;
  final Future<Directory> Function() _directory;
  final FileOpener _opener;
  final FilesPicker _filesPicker;
  final PhotoTaker _photoTaker;

  AttachmentService(
    this._apiClient, {
    Future<Directory> Function()? directory,
    FileOpener? opener,
    FilesPicker? filesPicker,
    PhotoTaker? photoTaker,
  }) : _directory = directory ?? getTemporaryDirectory,
       _opener = opener ?? ((path) => OpenFilex.open(path)),
       _filesPicker = filesPicker ?? _pickWithFilePicker,
       _photoTaker = photoTaker ?? _takeWithCamera;

  /// Chọn tệp PDF, ảnh, DOCX, XLSX trong máy.
  Future<List<PickedFileModel>> pickFiles() => _guard(_filesPicker);

  /// Chụp ảnh giấy tờ bằng máy ảnh.
  Future<PickedFileModel?> takePhoto() => _guard(_photoTaker);

  Future<T> _guard<T>(Future<T> Function() action) async {
    try {
      return await action();
    } on PlatformException {
      throw ApiErrorModel(
        status: 0,
        code: pickFailedCode,
        message: pickFailedMessage,
      );
    } on FileSystemException {
      throw ApiErrorModel(
        status: 0,
        code: pickFailedCode,
        message: pickFailedMessage,
      );
    }
  }

  static Future<List<PickedFileModel>> _pickWithFilePicker() async {
    final files = await FilePicker.pickFiles(
      type: FileType.custom,
      allowedExtensions: LeaveRule.contentTypes.keys.toList(),
    );
    return [
      for (final file in files)
        await _read(
          file.name,
          file.lengthSync() ?? await file.length() ?? 0,
          file.readAsBytes,
        ),
    ];
  }

  /// Ảnh nén còn khoảng vài trăm KB, đặt tên theo giờ chụp.
  static Future<PickedFileModel?> _takeWithCamera() async {
    final photo = await ImagePicker().pickImage(
      source: ImageSource.camera,
      maxWidth: 2000,
      maxHeight: 2000,
      imageQuality: 80,
    );
    if (photo == null) {
      return null;
    }
    final now = vietnamNow();
    String two(int value) => value.toString().padLeft(2, '0');
    final name =
        'anh-minh-chung-${now.year}${two(now.month)}${two(now.day)}-'
        '${two(now.hour)}${two(now.minute)}${two(now.second)}.jpg';
    return _read(name, await photo.length(), photo.readAsBytes);
  }

  /// Tệp quá giới hạn thì không đọc vào bộ nhớ, chỉ giữ dung lượng để báo lỗi.
  static Future<PickedFileModel> _read(
    String name,
    int size,
    Future<Uint8List> Function() readBytes,
  ) async {
    if (size > LeaveRule.maxFileBytes) {
      return PickedFileModel(fileName: name, sizeBytes: size);
    }
    return PickedFileModel.fromBytes(name, await readBytes());
  }

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
