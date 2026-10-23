import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';

/// Mức độ của thông báo (giống enum NotificationPriority của backend).
class NotificationPriority {
  NotificationPriority._();

  static const String normal = 'NORMAL';
  static const String important = 'IMPORTANT';
  static const String urgent = 'URGENT';
}

/// Một dòng trong hộp thư theo `GET /notifications`. [createdAt] dạng
/// `yyyy-MM-ddTHH:mm:ss`; [read] là người xem đã đọc.
class NotificationModel {
  int _id;
  String _title;
  String? _senderName;
  String _priority;
  String _createdAt;
  bool _read;

  NotificationModel({
    required this._id,
    required this._title,
    this._senderName,
    this._priority = NotificationPriority.normal,
    required this._createdAt,
    this._read = false,
  });

  factory NotificationModel.fromJson(Map<String, dynamic> json) {
    return NotificationModel(
      id: (json['id'] as num).toInt(),
      title: json['title'] as String? ?? '',
      senderName: json['senderName'] as String?,
      priority: json['priority'] as String? ?? NotificationPriority.normal,
      createdAt: json['createdAt'] as String? ?? '',
      read: json['read'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': _id,
    'title': _title,
    'senderName': _senderName,
    'priority': _priority,
    'createdAt': _createdAt,
    'read': _read,
  };

  int get id => _id;
  set id(int value) => _id = value;

  String get title => _title;
  set title(String value) => _title = value;

  String? get senderName => _senderName;
  set senderName(String? value) => _senderName = value;

  String get priority => _priority;
  set priority(String value) => _priority = value;

  String get createdAt => _createdAt;
  set createdAt(String value) => _createdAt = value;

  bool get read => _read;
  set read(bool value) => _read = value;
}

/// Chi tiết thông báo theo `GET /notifications/{id}`; [targetLabel] là nhóm
/// người nhận ("Toàn trường", "Lớp 10A1"...).
class NotificationDetailModel {
  int _id;
  String _title;
  String _content;
  String _priority;
  String? _targetLabel;
  String? _senderName;
  String _createdAt;
  List<AttachmentModel> _files;

  NotificationDetailModel({
    required this._id,
    required this._title,
    this._content = '',
    this._priority = NotificationPriority.normal,
    this._targetLabel,
    this._senderName,
    required this._createdAt,
    List<AttachmentModel> files = const [],
  }) : _files = List<AttachmentModel>.of(files);

  factory NotificationDetailModel.fromJson(Map<String, dynamic> json) {
    return NotificationDetailModel(
      id: (json['id'] as num).toInt(),
      title: json['title'] as String? ?? '',
      content: json['content'] as String? ?? '',
      priority: json['priority'] as String? ?? NotificationPriority.normal,
      targetLabel: json['targetLabel'] as String?,
      senderName: json['senderName'] as String?,
      createdAt: json['createdAt'] as String? ?? '',
      files: (json['files'] as List<dynamic>? ?? const [])
          .map((item) => AttachmentModel.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }

  Map<String, dynamic> toJson() => {
    'id': _id,
    'title': _title,
    'content': _content,
    'priority': _priority,
    'targetLabel': _targetLabel,
    'senderName': _senderName,
    'createdAt': _createdAt,
    'files': [for (final file in _files) file.toJson()],
  };

  int get id => _id;
  set id(int value) => _id = value;

  String get title => _title;
  set title(String value) => _title = value;

  String get content => _content;
  set content(String value) => _content = value;

  String get priority => _priority;
  set priority(String value) => _priority = value;

  String? get targetLabel => _targetLabel;
  set targetLabel(String? value) => _targetLabel = value;

  String? get senderName => _senderName;
  set senderName(String? value) => _senderName = value;

  String get createdAt => _createdAt;
  set createdAt(String value) => _createdAt = value;

  List<AttachmentModel> get files => List<AttachmentModel>.unmodifiable(_files);
  set files(List<AttachmentModel> value) =>
      _files = List<AttachmentModel>.of(value);
}
