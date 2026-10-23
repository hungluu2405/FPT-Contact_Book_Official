/// Một lớp môn học trong ô chọn của form đơn nghỉ: `{id, code, name}`, [name]
/// dạng "TOAN10 - Toán 10 (HK1-2627)".
class SectionOptionModel {
  int _id;
  String _code;
  String _name;

  SectionOptionModel({
    required this._id,
    required this._code,
    required this._name,
  });

  factory SectionOptionModel.fromJson(Map<String, dynamic> json) {
    return SectionOptionModel(
      id: (json['id'] as num).toInt(),
      code: json['code'] as String? ?? '',
      name: json['name'] as String? ?? '',
    );
  }

  Map<String, dynamic> toJson() => {'id': _id, 'code': _code, 'name': _name};

  int get id => _id;
  set id(int value) => _id = value;

  String get code => _code;
  set code(String value) => _code = value;

  String get name => _name;
  set name(String value) => _name = value;
}

/// Dữ liệu cho form đơn nghỉ theo `GET .../requests/leave-options`: học sinh
/// được xin nghỉ và các lớp môn học đang học.
class LeaveOptionsModel {
  int _studentId;
  String _studentCode;
  String _studentName;
  List<SectionOptionModel> _sections;

  LeaveOptionsModel({
    required this._studentId,
    required this._studentCode,
    required this._studentName,
    List<SectionOptionModel> sections = const [],
  }) : _sections = List<SectionOptionModel>.of(sections);

  factory LeaveOptionsModel.fromJson(Map<String, dynamic> json) {
    return LeaveOptionsModel(
      studentId: (json['studentId'] as num).toInt(),
      studentCode: json['studentCode'] as String? ?? '',
      studentName: json['studentName'] as String? ?? '',
      sections: (json['sections'] as List<dynamic>? ?? const [])
          .map(
            (item) => SectionOptionModel.fromJson(item as Map<String, dynamic>),
          )
          .toList(),
    );
  }

  int get studentId => _studentId;
  set studentId(int value) => _studentId = value;

  String get studentCode => _studentCode;
  set studentCode(String value) => _studentCode = value;

  String get studentName => _studentName;
  set studentName(String value) => _studentName = value;

  List<SectionOptionModel> get sections =>
      List<SectionOptionModel>.unmodifiable(_sections);
  set sections(List<SectionOptionModel> value) =>
      _sections = List<SectionOptionModel>.of(value);
}

/// Nội dung form đơn nghỉ gửi lên `POST .../requests/leave`.
class LeaveFormModel {
  DateTime? _fromDate;
  DateTime? _toDate;
  int? _sectionId;
  String _title;
  String _reason;

  LeaveFormModel({
    this._fromDate,
    this._toDate,
    this._sectionId,
    this._title = '',
    this._reason = '',
  });

  DateTime? get fromDate => _fromDate;
  set fromDate(DateTime? value) => _fromDate = value;

  DateTime? get toDate => _toDate;
  set toDate(DateTime? value) => _toDate = value;

  /// Null là nghỉ mọi lớp trong khoảng ngày.
  int? get sectionId => _sectionId;
  set sectionId(int? value) => _sectionId = value;

  /// Để trống thì backend tự điền "Đơn xin nghỉ học từ ... đến ...".
  String get title => _title;
  set title(String value) => _title = value;

  String get reason => _reason;
  set reason(String value) => _reason = value;
}
