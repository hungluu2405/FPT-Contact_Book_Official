import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/page_model.dart';

/// Phạm vi gửi của thông báo (giống enum NotificationTargetType của backend).
class NotificationTargetType {
  NotificationTargetType._();

  static const String department = 'DEPARTMENT';
  static const String user = 'USER';
}

/// Một thông báo mình đã gửi theo `GET /notifications/sent`, kèm số người
/// nhận và số người đã đọc.
class SentNotificationModel {
  int _id;
  String _title;
  String _priority;
  String _targetType;
  String _createdAt;
  int _recipientCount;
  int _readCount;

  SentNotificationModel({
    required this._id,
    required this._title,
    this._priority = NotificationPriority.normal,
    this._targetType = NotificationTargetType.department,
    this._createdAt = '',
    this._recipientCount = 0,
    this._readCount = 0,
  });

  factory SentNotificationModel.fromJson(Map<String, dynamic> json) {
    return SentNotificationModel(
      id: (json['id'] as num).toInt(),
      title: json['title'] as String? ?? '',
      priority: json['priority'] as String? ?? NotificationPriority.normal,
      targetType: json['targetType'] as String? ?? '',
      createdAt: json['createdAt'] as String? ?? '',
      recipientCount: (json['recipientCount'] as num?)?.toInt() ?? 0,
      readCount: (json['readCount'] as num?)?.toInt() ?? 0,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': _id,
    'title': _title,
    'priority': _priority,
    'targetType': _targetType,
    'createdAt': _createdAt,
    'recipientCount': _recipientCount,
    'readCount': _readCount,
  };

  int get id => _id;
  set id(int value) => _id = value;

  String get title => _title;
  set title(String value) => _title = value;

  String get priority => _priority;
  set priority(String value) => _priority = value;

  String get targetType => _targetType;
  set targetType(String value) => _targetType = value;

  String get createdAt => _createdAt;
  set createdAt(String value) => _createdAt = value;

  int get recipientCount => _recipientCount;
  set recipientCount(int value) => _recipientCount = value;

  int get readCount => _readCount;
  set readCount(int value) => _readCount = value;
}

/// Một người nhận trong thống kê: đã đọc chưa và lúc nào
/// (`yyyy-MM-ddTHH:mm:ss`).
class RecipientModel {
  String _fullName;
  bool _read;
  String? _readAt;

  RecipientModel({required this._fullName, this._read = false, this._readAt});

  factory RecipientModel.fromJson(Map<String, dynamic> json) {
    return RecipientModel(
      fullName: json['fullName'] as String? ?? '',
      read: json['read'] as bool? ?? false,
      readAt: json['readAt'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
    'fullName': _fullName,
    'read': _read,
    'readAt': _readAt,
  };

  String get fullName => _fullName;
  set fullName(String value) => _fullName = value;

  bool get read => _read;
  set read(bool value) => _read = value;

  String? get readAt => _readAt;
  set readAt(String? value) => _readAt = value;
}

/// Thông báo mình đã gửi theo `GET /notifications/sent/{id}`: nội dung, phạm
/// vi, thống kê đã đọc và một trang người nhận.
class SentNotificationDetailModel {
  int _id;
  String _title;
  String _content;
  String _priority;
  String _targetType;
  String? _targetLabel;
  String _createdAt;
  List<AttachmentModel> _files;
  int _recipientCount;
  int _readCount;
  PageModel<RecipientModel> _recipients;

  SentNotificationDetailModel({
    required this._id,
    required this._title,
    this._content = '',
    this._priority = NotificationPriority.normal,
    this._targetType = NotificationTargetType.department,
    this._targetLabel,
    this._createdAt = '',
    List<AttachmentModel> files = const [],
    this._recipientCount = 0,
    this._readCount = 0,
    required this._recipients,
  }) : _files = List<AttachmentModel>.of(files);

  factory SentNotificationDetailModel.fromJson(Map<String, dynamic> json) {
    return SentNotificationDetailModel(
      id: (json['id'] as num).toInt(),
      title: json['title'] as String? ?? '',
      content: json['content'] as String? ?? '',
      priority: json['priority'] as String? ?? NotificationPriority.normal,
      targetType: json['targetType'] as String? ?? '',
      targetLabel: json['targetLabel'] as String?,
      createdAt: json['createdAt'] as String? ?? '',
      files: (json['files'] as List<dynamic>? ?? const [])
          .map((item) => AttachmentModel.fromJson(item as Map<String, dynamic>))
          .toList(),
      recipientCount: (json['recipientCount'] as num?)?.toInt() ?? 0,
      readCount: (json['readCount'] as num?)?.toInt() ?? 0,
      recipients: PageModel.fromJson(
        json['recipients'] as Map<String, dynamic>? ?? const {},
        RecipientModel.fromJson,
      ),
    );
  }

  int get id => _id;
  set id(int value) => _id = value;

  String get title => _title;
  set title(String value) => _title = value;

  String get content => _content;
  set content(String value) => _content = value;

  String get priority => _priority;
  set priority(String value) => _priority = value;

  String get targetType => _targetType;
  set targetType(String value) => _targetType = value;

  String? get targetLabel => _targetLabel;
  set targetLabel(String? value) => _targetLabel = value;

  String get createdAt => _createdAt;
  set createdAt(String value) => _createdAt = value;

  List<AttachmentModel> get files => List<AttachmentModel>.unmodifiable(_files);
  set files(List<AttachmentModel> value) =>
      _files = List<AttachmentModel>.of(value);

  int get recipientCount => _recipientCount;
  set recipientCount(int value) => _recipientCount = value;

  int get readCount => _readCount;
  set readCount(int value) => _readCount = value;

  PageModel<RecipientModel> get recipients => _recipients;
  set recipients(PageModel<RecipientModel> value) => _recipients = value;
}

/// Nội dung form thông báo nội bộ của tổ trưởng.
class DepartmentNotificationFormModel {
  String _title;
  String _content;
  String _priority;

  DepartmentNotificationFormModel({
    this._title = '',
    this._content = '',
    this._priority = NotificationPriority.normal,
  });

  String get title => _title;
  set title(String value) => _title = value;

  String get content => _content;
  set content(String value) => _content = value;

  /// `NORMAL`, `IMPORTANT` hoặc `URGENT`.
  String get priority => _priority;
  set priority(String value) => _priority = value;
}
