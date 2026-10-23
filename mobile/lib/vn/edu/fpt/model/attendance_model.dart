import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/page_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/semester_model.dart';

/// Mã `status` điểm danh (docs/API.md mục "Giá trị enum"); null là chưa
/// điểm danh.
class AttendanceStatus {
  AttendanceStatus._();

  static const String present = 'PRESENT';
  static const String absentExcused = 'ABSENT_EXCUSED';
  static const String absentUnexcused = 'ABSENT_UNEXCUSED';
}

/// Chuyên cần của một môn. Tỷ lệ là phần trăm; [attendanceRate] null khi
/// chưa điểm danh buổi nào. [warning] là vượt ngưỡng vắng.
class AttendanceSummaryModel {
  int _present;
  int _absentExcused;
  int _absentUnexcused;
  int _totalSessions;
  double? _attendanceRate;
  double _absentRate;
  bool _warning;

  AttendanceSummaryModel({
    this._present = 0,
    this._absentExcused = 0,
    this._absentUnexcused = 0,
    this._totalSessions = 0,
    this._attendanceRate,
    this._absentRate = 0,
    this._warning = false,
  });

  factory AttendanceSummaryModel.fromJson(Map<String, dynamic> json) {
    return AttendanceSummaryModel(
      present: (json['present'] as num?)?.toInt() ?? 0,
      absentExcused: (json['absentExcused'] as num?)?.toInt() ?? 0,
      absentUnexcused: (json['absentUnexcused'] as num?)?.toInt() ?? 0,
      totalSessions: (json['totalSessions'] as num?)?.toInt() ?? 0,
      attendanceRate: (json['attendanceRate'] as num?)?.toDouble(),
      absentRate: (json['absentRate'] as num?)?.toDouble() ?? 0,
      warning: json['warning'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toJson() => {
    'present': _present,
    'absentExcused': _absentExcused,
    'absentUnexcused': _absentUnexcused,
    'totalSessions': _totalSessions,
    'attendanceRate': _attendanceRate,
    'absentRate': _absentRate,
    'warning': _warning,
  };

  int get present => _present;
  set present(int value) => _present = value;

  int get absentExcused => _absentExcused;
  set absentExcused(int value) => _absentExcused = value;

  int get absentUnexcused => _absentUnexcused;
  set absentUnexcused(int value) => _absentUnexcused = value;

  int get totalSessions => _totalSessions;
  set totalSessions(int value) => _totalSessions = value;

  double? get attendanceRate => _attendanceRate;
  set attendanceRate(double? value) => _attendanceRate = value;

  double get absentRate => _absentRate;
  set absentRate(double value) => _absentRate = value;

  bool get warning => _warning;
  set warning(bool value) => _warning = value;

  /// Số buổi đã điểm danh.
  int get marked => _present + _absentExcused + _absentUnexcused;
}

/// Chuyên cần của một lớp môn học (StudentAttendanceRow).
class AttendanceSectionModel {
  int _sectionId;
  String _sectionCode;
  String _subjectCode;
  String _subjectName;
  AttendanceSummaryModel _summary;

  AttendanceSectionModel({
    required this._sectionId,
    required this._sectionCode,
    required this._subjectCode,
    required this._subjectName,
    required this._summary,
  });

  factory AttendanceSectionModel.fromJson(Map<String, dynamic> json) {
    return AttendanceSectionModel(
      sectionId: (json['sectionId'] as num).toInt(),
      sectionCode: json['sectionCode'] as String? ?? '',
      subjectCode: json['subjectCode'] as String? ?? '',
      subjectName: json['subjectName'] as String? ?? '',
      summary: AttendanceSummaryModel.fromJson(
        json['summary'] as Map<String, dynamic>? ?? const {},
      ),
    );
  }

  Map<String, dynamic> toJson() => {
    'sectionId': _sectionId,
    'sectionCode': _sectionCode,
    'subjectCode': _subjectCode,
    'subjectName': _subjectName,
    'summary': _summary.toJson(),
  };

  int get sectionId => _sectionId;
  set sectionId(int value) => _sectionId = value;

  String get sectionCode => _sectionCode;
  set sectionCode(String value) => _sectionCode = value;

  String get subjectCode => _subjectCode;
  set subjectCode(String value) => _subjectCode = value;

  String get subjectName => _subjectName;
  set subjectName(String value) => _subjectName = value;

  AttendanceSummaryModel get summary => _summary;
  set summary(AttendanceSummaryModel value) => _summary = value;
}

/// Chuyên cần mọi môn của học kỳ, theo `GET .../attendance`.
class AttendanceOverviewModel {
  SemesterModel _semester;
  int _thresholdPercent;
  List<AttendanceSectionModel> _sections;

  AttendanceOverviewModel({
    required this._semester,
    required this._thresholdPercent,
    List<AttendanceSectionModel> sections = const [],
  }) : _sections = List<AttendanceSectionModel>.of(sections);

  factory AttendanceOverviewModel.fromJson(Map<String, dynamic> json) {
    return AttendanceOverviewModel(
      semester: SemesterModel.fromJson(
        json['semester'] as Map<String, dynamic>,
      ),
      thresholdPercent: (json['thresholdPercent'] as num?)?.toInt() ?? 0,
      sections: (json['sections'] as List<dynamic>? ?? const [])
          .map(
            (row) =>
                AttendanceSectionModel.fromJson(row as Map<String, dynamic>),
          )
          .toList(),
    );
  }

  SemesterModel get semester => _semester;
  set semester(SemesterModel value) => _semester = value;

  int get thresholdPercent => _thresholdPercent;
  set thresholdPercent(int value) => _thresholdPercent = value;

  List<AttendanceSectionModel> get sections =>
      List<AttendanceSectionModel>.unmodifiable(_sections);
  set sections(List<AttendanceSectionModel> value) =>
      _sections = List<AttendanceSectionModel>.of(value);
}

/// Một buổi trong lịch sử điểm danh. [status] null là chưa điểm danh;
/// [sessionStatus] là trạng thái buổi học (đã hủy, học bù...).
class AttendanceSessionModel {
  int _sessionNo;
  String _date;
  String _startTime;
  String _endTime;
  String? _roomCode;
  String _sessionStatus;
  String? _status;
  String? _note;

  AttendanceSessionModel({
    required this._sessionNo,
    required this._date,
    required this._startTime,
    required this._endTime,
    this._roomCode,
    required this._sessionStatus,
    this._status,
    this._note,
  });

  factory AttendanceSessionModel.fromJson(Map<String, dynamic> json) {
    return AttendanceSessionModel(
      sessionNo: (json['sessionNo'] as num?)?.toInt() ?? 0,
      date: json['date'] as String? ?? '',
      startTime: json['startTime'] as String? ?? '',
      endTime: json['endTime'] as String? ?? '',
      roomCode: json['roomCode'] as String?,
      sessionStatus: json['sessionStatus'] as String? ?? '',
      status: json['status'] as String?,
      note: json['note'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
    'sessionNo': _sessionNo,
    'date': _date,
    'startTime': _startTime,
    'endTime': _endTime,
    'roomCode': _roomCode,
    'sessionStatus': _sessionStatus,
    'status': _status,
    'note': _note,
  };

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

  String get sessionStatus => _sessionStatus;
  set sessionStatus(String value) => _sessionStatus = value;

  String? get status => _status;
  set status(String? value) => _status = value;

  String? get note => _note;
  set note(String? value) => _note = value;
}

/// Lịch sử điểm danh một môn, theo `GET .../attendance/{sectionId}`:
/// thông tin môn kèm chuyên cần và các buổi (tải theo trang).
class AttendanceHistoryModel {
  AttendanceSectionModel _section;
  int _thresholdPercent;
  PageModel<AttendanceSessionModel> _sessions;

  AttendanceHistoryModel({
    required this._section,
    required this._thresholdPercent,
    required this._sessions,
  });

  factory AttendanceHistoryModel.fromJson(Map<String, dynamic> json) {
    return AttendanceHistoryModel(
      section: AttendanceSectionModel.fromJson(
        json['summary'] as Map<String, dynamic>,
      ),
      thresholdPercent: (json['thresholdPercent'] as num?)?.toInt() ?? 0,
      sessions: PageModel.fromJson(
        json['sessions'] as Map<String, dynamic>,
        AttendanceSessionModel.fromJson,
      ),
    );
  }

  AttendanceSectionModel get section => _section;
  set section(AttendanceSectionModel value) => _section = value;

  int get thresholdPercent => _thresholdPercent;
  set thresholdPercent(int value) => _thresholdPercent = value;

  PageModel<AttendanceSessionModel> get sessions => _sessions;
  set sessions(PageModel<AttendanceSessionModel> value) => _sessions = value;
}
