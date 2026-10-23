/// Một tệp đính kèm của thông báo hoặc đơn: `{id, fileName, sizeBytes}`.
class AttachmentModel {
  int _id;
  String _fileName;
  int _sizeBytes;

  AttachmentModel({
    required this._id,
    required this._fileName,
    this._sizeBytes = 0,
  });

  factory AttachmentModel.fromJson(Map<String, dynamic> json) {
    return AttachmentModel(
      id: (json['id'] as num).toInt(),
      fileName: json['fileName'] as String? ?? '',
      sizeBytes: (json['sizeBytes'] as num?)?.toInt() ?? 0,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': _id,
    'fileName': _fileName,
    'sizeBytes': _sizeBytes,
  };

  int get id => _id;
  set id(int value) => _id = value;

  String get fileName => _fileName;
  set fileName(String value) => _fileName = value;

  int get sizeBytes => _sizeBytes;
  set sizeBytes(int value) => _sizeBytes = value;

  /// Phần mở rộng viết thường, không có dấu chấm ("pdf"); không có thì rỗng.
  String get extension {
    final dot = _fileName.lastIndexOf('.');
    return dot < 0 ? '' : _fileName.substring(dot + 1).toLowerCase();
  }
}
