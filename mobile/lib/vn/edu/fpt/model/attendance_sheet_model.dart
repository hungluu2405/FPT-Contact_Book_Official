/// Một học sinh trong bảng điểm danh: trạng thái đã lưu (null là chưa điểm
/// danh), ghi chú, và [approvedLeave] là có đơn nghỉ đã duyệt phủ buổi này.
class AttendanceSheetRowModel {
  int _studentId;
  String _studentCode;
  String _fullName;
  String? _status;
  String? _note;
  bool _approvedLeave;

  AttendanceSheetRowModel({
    required this._studentId,
    required this._studentCode,
    required this._fullName,
    this._status,
    this._note,
    this._approvedLeave = false,
  });

  factory AttendanceSheetRowModel.fromJson(Map<String, dynamic> json) {
    return AttendanceSheetRowModel(
      studentId: (json['studentId'] as num).toInt(),
      studentCode: json['studentCode'] as String? ?? '',
      fullName: json['fullName'] as String? ?? '',
      status: json['status'] as String?,
      note: json['note'] as String?,
      approvedLeave: json['approvedLeave'] as bool? ?? false,
    );
  }

  int get studentId => _studentId;
  set studentId(int value) => _studentId = value;

  String get studentCode => _studentCode;
  set studentCode(String value) => _studentCode = value;

  String get fullName => _fullName;
  set fullName(String value) => _fullName = value;

  String? get status => _status;
  set status(String? value) => _status = value;

  String? get note => _note;
  set note(String? value) => _note = value;

  bool get approvedLeave => _approvedLeave;
  set approvedLeave(bool value) => _approvedLeave = value;
}

/// Bảng điểm danh một buổi theo
/// `GET /teacher/sections/{id}/sessions/{sessionId}/attendance`.
/// [marked]: buổi đã điểm danh (lần lưu sau là điều chỉnh); [locked]: quá
/// hạn, chỉ xem; [reasonRequired]: lần lưu sau phải có lý do khi đổi trạng
/// thái; [editableUntil] dạng `yyyy-MM-ddTHH:mm:ss`.
class AttendanceSheetModel {
  int _sectionId;
  String _sectionCode;
  String _subjectCode;
  String _subjectName;
  int _sessionId;
  int _sessionNo;
  String _date;
  String _startTime;
  String _endTime;
  String? _roomCode;
  bool _marked;
  String? _editableUntil;
  bool _locked;
  bool _reasonRequired;
  List<AttendanceSheetRowModel> _rows;

  AttendanceSheetModel({
    required this._sectionId,
    required this._sectionCode,
    this._subjectCode = '',
    this._subjectName = '',
    required this._sessionId,
    this._sessionNo = 0,
    this._date = '',
    this._startTime = '',
    this._endTime = '',
    this._roomCode,
    this._marked = false,
    this._editableUntil,
    this._locked = false,
    this._reasonRequired = false,
    List<AttendanceSheetRowModel> rows = const [],
  }) : _rows = List<AttendanceSheetRowModel>.of(rows);

  factory AttendanceSheetModel.fromJson(Map<String, dynamic> json) {
    return AttendanceSheetModel(
      sectionId: (json['sectionId'] as num).toInt(),
      sectionCode: json['sectionCode'] as String? ?? '',
      subjectCode: json['subjectCode'] as String? ?? '',
      subjectName: json['subjectName'] as String? ?? '',
      sessionId: (json['sessionId'] as num).toInt(),
      sessionNo: (json['sessionNo'] as num?)?.toInt() ?? 0,
      date: json['date'] as String? ?? '',
      startTime: json['startTime'] as String? ?? '',
      endTime: json['endTime'] as String? ?? '',
      roomCode: json['roomCode'] as String?,
      marked: json['marked'] as bool? ?? false,
      editableUntil: json['editableUntil'] as String?,
      locked: json['locked'] as bool? ?? false,
      reasonRequired: json['reasonRequired'] as bool? ?? false,
      rows: (json['rows'] as List<dynamic>? ?? const [])
          .map(
            (item) =>
                AttendanceSheetRowModel.fromJson(item as Map<String, dynamic>),
          )
          .toList(),
    );
  }

  int get sectionId => _sectionId;
  set sectionId(int value) => _sectionId = value;

  String get sectionCode => _sectionCode;
  set sectionCode(String value) => _sectionCode = value;

  String get subjectCode => _subjectCode;
  set subjectCode(String value) => _subjectCode = value;

  String get subjectName => _subjectName;
  set subjectName(String value) => _subjectName = value;

  int get sessionId => _sessionId;
  set sessionId(int value) => _sessionId = value;

  int get sessionNo => _sessionNo;
  set sessionNo(int value) => _sessionNo = value;

  String get date => _date;
  set date(String value) => _date = value;

  String get startTime => _startTime;
  set startTime(String value) => _startTime = value;

  String get endTime => _endTime;
  set endTime(String value) => _endTime = value;

  String? get roomCode => _roomCode;
  set roomCode(String? value) => _roomCode = value;

  bool get marked => _marked;
  set marked(bool value) => _marked = value;

  String? get editableUntil => _editableUntil;
  set editableUntil(String? value) => _editableUntil = value;

  bool get locked => _locked;
  set locked(bool value) => _locked = value;

  bool get reasonRequired => _reasonRequired;
  set reasonRequired(bool value) => _reasonRequired = value;

  List<AttendanceSheetRowModel> get rows =>
      List<AttendanceSheetRowModel>.unmodifiable(_rows);
  set rows(List<AttendanceSheetRowModel> value) =>
      _rows = List<AttendanceSheetRowModel>.of(value);
}

/// Kết quả lưu điểm danh: [firstTime] là lần đầu điểm danh buổi này,
/// [changed] là số học sinh được lưu hoặc điều chỉnh, [sheet] là bảng mới.
class AttendanceSaveResultModel {
  bool _firstTime;
  int _changed;
  AttendanceSheetModel _sheet;

  AttendanceSaveResultModel({
    required this._firstTime,
    required this._changed,
    required this._sheet,
  });

  factory AttendanceSaveResultModel.fromJson(Map<String, dynamic> json) {
    return AttendanceSaveResultModel(
      firstTime: json['firstTime'] as bool? ?? false,
      changed: (json['changed'] as num?)?.toInt() ?? 0,
      sheet: AttendanceSheetModel.fromJson(
        json['sheet'] as Map<String, dynamic>,
      ),
    );
  }

  bool get firstTime => _firstTime;
  set firstTime(bool value) => _firstTime = value;

  int get changed => _changed;
  set changed(int value) => _changed = value;

  AttendanceSheetModel get sheet => _sheet;
  set sheet(AttendanceSheetModel value) => _sheet = value;
}
