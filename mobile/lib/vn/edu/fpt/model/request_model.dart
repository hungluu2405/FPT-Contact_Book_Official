import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';

/// Loại đơn (giống enum RequestType của backend).
class RequestType {
  RequestType._();

  static const String leave = 'LEAVE';
  static const String transcript = 'TRANSCRIPT';
  static const String certificate = 'CERTIFICATE';
  static const String other = 'OTHER';
}

/// Trạng thái đơn (giống enum RequestStatus của backend).
class RequestStatus {
  RequestStatus._();

  static const String pending = 'PENDING';
  static const String approved = 'APPROVED';
  static const String rejected = 'REJECTED';
  static const String cancelled = 'CANCELLED';
}

/// Một dòng trong danh sách đơn theo `GET .../requests`. Ngày dạng
/// `yyyy-MM-dd`, null với đơn không phải đơn nghỉ; [createdAt] dạng
/// `yyyy-MM-ddTHH:mm:ss`.
class RequestModel {
  int _id;
  String _type;
  String _title;
  String? _studentCode;
  String? _studentName;
  String? _requesterName;
  String? _sectionCode;
  String? _fromDate;
  String? _toDate;
  String _status;
  String _createdAt;

  RequestModel({
    required this._id,
    required this._type,
    required this._title,
    this._studentCode,
    this._studentName,
    this._requesterName,
    this._sectionCode,
    this._fromDate,
    this._toDate,
    this._status = RequestStatus.pending,
    this._createdAt = '',
  });

  factory RequestModel.fromJson(Map<String, dynamic> json) {
    return RequestModel(
      id: (json['id'] as num).toInt(),
      type: json['type'] as String? ?? RequestType.other,
      title: json['title'] as String? ?? '',
      studentCode: json['studentCode'] as String?,
      studentName: json['studentName'] as String?,
      requesterName: json['requesterName'] as String?,
      sectionCode: json['sectionCode'] as String?,
      fromDate: json['fromDate'] as String?,
      toDate: json['toDate'] as String?,
      status: json['status'] as String? ?? RequestStatus.pending,
      createdAt: json['createdAt'] as String? ?? '',
    );
  }

  Map<String, dynamic> toJson() => {
    'id': _id,
    'type': _type,
    'title': _title,
    'studentCode': _studentCode,
    'studentName': _studentName,
    'requesterName': _requesterName,
    'sectionCode': _sectionCode,
    'fromDate': _fromDate,
    'toDate': _toDate,
    'status': _status,
    'createdAt': _createdAt,
  };

  int get id => _id;
  set id(int value) => _id = value;

  String get type => _type;
  set type(String value) => _type = value;

  String get title => _title;
  set title(String value) => _title = value;

  String? get studentCode => _studentCode;
  set studentCode(String? value) => _studentCode = value;

  String? get studentName => _studentName;
  set studentName(String? value) => _studentName = value;

  String? get requesterName => _requesterName;
  set requesterName(String? value) => _requesterName = value;

  String? get sectionCode => _sectionCode;
  set sectionCode(String? value) => _sectionCode = value;

  String? get fromDate => _fromDate;
  set fromDate(String? value) => _fromDate = value;

  String? get toDate => _toDate;
  set toDate(String? value) => _toDate = value;

  String get status => _status;
  set status(String value) => _status = value;

  String get createdAt => _createdAt;
  set createdAt(String value) => _createdAt = value;
}

/// Một lần duyệt hoặc từ chối trong lịch sử của đơn.
class RequestReviewModel {
  String? _reviewerName;
  String _decision;
  String? _comment;
  String? _reviewedAt;

  RequestReviewModel({
    this._reviewerName,
    required this._decision,
    this._comment,
    this._reviewedAt,
  });

  factory RequestReviewModel.fromJson(Map<String, dynamic> json) {
    return RequestReviewModel(
      reviewerName: json['reviewerName'] as String?,
      decision: json['decision'] as String? ?? RequestStatus.approved,
      comment: json['comment'] as String?,
      reviewedAt: json['reviewedAt'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
    'reviewerName': _reviewerName,
    'decision': _decision,
    'comment': _comment,
    'reviewedAt': _reviewedAt,
  };

  String? get reviewerName => _reviewerName;
  set reviewerName(String? value) => _reviewerName = value;

  /// `APPROVED` hoặc `REJECTED`.
  String get decision => _decision;
  set decision(String value) => _decision = value;

  String? get comment => _comment;
  set comment(String? value) => _comment = value;

  String? get reviewedAt => _reviewedAt;
  set reviewedAt(String? value) => _reviewedAt = value;
}

/// Chi tiết đơn theo `GET .../requests/{id}` (RequestDetail).
/// [reviewerName] null nghĩa là phòng quản trị duyệt; [canCancel] là người
/// xem gửi đơn và đơn còn chờ duyệt.
class RequestDetailModel {
  int _id;
  String _type;
  String _title;
  String _reason;
  String? _studentCode;
  String? _studentName;
  String? _requesterName;
  bool _sentByParent;
  String? _sectionLabel;
  String? _fromDate;
  String? _toDate;
  String _status;
  String _createdAt;
  String? _updatedAt;
  String? _reviewerName;
  List<AttachmentModel> _files;
  List<RequestReviewModel> _reviews;
  bool _canCancel;

  RequestDetailModel({
    required this._id,
    required this._type,
    required this._title,
    this._reason = '',
    this._studentCode,
    this._studentName,
    this._requesterName,
    this._sentByParent = false,
    this._sectionLabel,
    this._fromDate,
    this._toDate,
    this._status = RequestStatus.pending,
    this._createdAt = '',
    this._updatedAt,
    this._reviewerName,
    List<AttachmentModel> files = const [],
    List<RequestReviewModel> reviews = const [],
    this._canCancel = false,
  }) : _files = List<AttachmentModel>.of(files),
       _reviews = List<RequestReviewModel>.of(reviews);

  factory RequestDetailModel.fromJson(Map<String, dynamic> json) {
    return RequestDetailModel(
      id: (json['id'] as num).toInt(),
      type: json['type'] as String? ?? RequestType.other,
      title: json['title'] as String? ?? '',
      reason: json['reason'] as String? ?? '',
      studentCode: json['studentCode'] as String?,
      studentName: json['studentName'] as String?,
      requesterName: json['requesterName'] as String?,
      sentByParent: json['sentByParent'] as bool? ?? false,
      sectionLabel: json['sectionLabel'] as String?,
      fromDate: json['fromDate'] as String?,
      toDate: json['toDate'] as String?,
      status: json['status'] as String? ?? RequestStatus.pending,
      createdAt: json['createdAt'] as String? ?? '',
      updatedAt: json['updatedAt'] as String?,
      reviewerName: json['reviewerName'] as String?,
      files: (json['files'] as List<dynamic>? ?? const [])
          .map((item) => AttachmentModel.fromJson(item as Map<String, dynamic>))
          .toList(),
      reviews: (json['reviews'] as List<dynamic>? ?? const [])
          .map(
            (item) => RequestReviewModel.fromJson(item as Map<String, dynamic>),
          )
          .toList(),
      canCancel: json['canCancel'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': _id,
    'type': _type,
    'title': _title,
    'reason': _reason,
    'studentCode': _studentCode,
    'studentName': _studentName,
    'requesterName': _requesterName,
    'sentByParent': _sentByParent,
    'sectionLabel': _sectionLabel,
    'fromDate': _fromDate,
    'toDate': _toDate,
    'status': _status,
    'createdAt': _createdAt,
    'updatedAt': _updatedAt,
    'reviewerName': _reviewerName,
    'files': [for (final file in _files) file.toJson()],
    'reviews': [for (final review in _reviews) review.toJson()],
    'canCancel': _canCancel,
  };

  int get id => _id;
  set id(int value) => _id = value;

  String get type => _type;
  set type(String value) => _type = value;

  String get title => _title;
  set title(String value) => _title = value;

  String get reason => _reason;
  set reason(String value) => _reason = value;

  String? get studentCode => _studentCode;
  set studentCode(String? value) => _studentCode = value;

  String? get studentName => _studentName;
  set studentName(String? value) => _studentName = value;

  String? get requesterName => _requesterName;
  set requesterName(String? value) => _requesterName = value;

  bool get sentByParent => _sentByParent;
  set sentByParent(bool value) => _sentByParent = value;

  String? get sectionLabel => _sectionLabel;
  set sectionLabel(String? value) => _sectionLabel = value;

  String? get fromDate => _fromDate;
  set fromDate(String? value) => _fromDate = value;

  String? get toDate => _toDate;
  set toDate(String? value) => _toDate = value;

  String get status => _status;
  set status(String value) => _status = value;

  String get createdAt => _createdAt;
  set createdAt(String value) => _createdAt = value;

  String? get updatedAt => _updatedAt;
  set updatedAt(String? value) => _updatedAt = value;

  String? get reviewerName => _reviewerName;
  set reviewerName(String? value) => _reviewerName = value;

  List<AttachmentModel> get files => List<AttachmentModel>.unmodifiable(_files);
  set files(List<AttachmentModel> value) =>
      _files = List<AttachmentModel>.of(value);

  List<RequestReviewModel> get reviews =>
      List<RequestReviewModel>.unmodifiable(_reviews);
  set reviews(List<RequestReviewModel> value) =>
      _reviews = List<RequestReviewModel>.of(value);

  bool get canCancel => _canCancel;
  set canCancel(bool value) => _canCancel = value;
}
