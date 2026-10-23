/// Mã `status` của buổi học (docs/API.md mục "Giá trị enum").
class SessionStatus {
  SessionStatus._();

  static const String scheduled = 'SCHEDULED';
  static const String done = 'DONE';
  static const String cancelled = 'CANCELLED';
  static const String makeup = 'MAKEUP';
}

/// Một buổi học (ScheduleItem). Ngày `yyyy-MM-dd`, giờ `HH:mm:ss` như API.
class ScheduleItemModel {
  String _date;
  String _startTime;
  String _endTime;
  String _subjectCode;
  String _subjectName;
  String _sectionCode;
  String? _roomCode;
  String? _teacherName;
  String _status;
  String? _note;

  ScheduleItemModel({
    required this._date,
    required this._startTime,
    required this._endTime,
    required this._subjectCode,
    required this._subjectName,
    required this._sectionCode,
    this._roomCode,
    this._teacherName,
    this._status = SessionStatus.scheduled,
    this._note,
  });

  factory ScheduleItemModel.fromJson(Map<String, dynamic> json) {
    return ScheduleItemModel(
      date: json['date'] as String? ?? '',
      startTime: json['startTime'] as String? ?? '',
      endTime: json['endTime'] as String? ?? '',
      subjectCode: json['subjectCode'] as String? ?? '',
      subjectName: json['subjectName'] as String? ?? '',
      sectionCode: json['sectionCode'] as String? ?? '',
      roomCode: json['roomCode'] as String?,
      teacherName: json['teacherName'] as String?,
      status: json['status'] as String? ?? SessionStatus.scheduled,
      note: json['note'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
    'date': _date,
    'startTime': _startTime,
    'endTime': _endTime,
    'subjectCode': _subjectCode,
    'subjectName': _subjectName,
    'sectionCode': _sectionCode,
    'roomCode': _roomCode,
    'teacherName': _teacherName,
    'status': _status,
    'note': _note,
  };

  String get date => _date;
  set date(String value) => _date = value;

  String get startTime => _startTime;
  set startTime(String value) => _startTime = value;

  String get endTime => _endTime;
  set endTime(String value) => _endTime = value;

  String get subjectCode => _subjectCode;
  set subjectCode(String value) => _subjectCode = value;

  String get subjectName => _subjectName;
  set subjectName(String value) => _subjectName = value;

  String get sectionCode => _sectionCode;
  set sectionCode(String value) => _sectionCode = value;

  String? get roomCode => _roomCode;
  set roomCode(String? value) => _roomCode = value;

  String? get teacherName => _teacherName;
  set teacherName(String? value) => _teacherName = value;

  String get status => _status;
  set status(String value) => _status = value;

  String? get note => _note;
  set note(String? value) => _note = value;

  bool get isCancelled => _status == SessionStatus.cancelled;
}
