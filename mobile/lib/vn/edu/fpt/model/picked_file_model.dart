import 'dart:typed_data';

/// Một tệp minh chứng người dùng vừa chọn hoặc chụp, chưa gửi. [bytes] null
/// khi tệp quá lớn nên không đọc vào bộ nhớ (chỉ biết [sizeBytes]).
class PickedFileModel {
  String _fileName;
  int _sizeBytes;
  Uint8List? _bytes;

  PickedFileModel({
    required this._fileName,
    required this._sizeBytes,
    this._bytes,
  });

  /// Tệp đã đọc sẵn nội dung.
  factory PickedFileModel.fromBytes(String fileName, Uint8List bytes) =>
      PickedFileModel(
        fileName: fileName,
        sizeBytes: bytes.length,
        bytes: bytes,
      );

  String get fileName => _fileName;
  set fileName(String value) => _fileName = value;

  int get sizeBytes => _sizeBytes;
  set sizeBytes(int value) => _sizeBytes = value;

  Uint8List? get bytes => _bytes;
  set bytes(Uint8List? value) => _bytes = value;

  /// Phần mở rộng viết thường, không có dấu chấm; không có thì rỗng.
  String get extension {
    final dot = _fileName.lastIndexOf('.');
    return dot < 0 ? '' : _fileName.substring(dot + 1).toLowerCase();
  }
}
