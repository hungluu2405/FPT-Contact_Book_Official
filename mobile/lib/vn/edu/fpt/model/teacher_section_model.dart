import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attendance_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/page_model.dart';

/// Trạng thái lớp môn học (giống enum SectionStatus của backend).
class SectionStatus {
  SectionStatus._();

  static const String open = 'OPEN';
  static const String closed = 'CLOSED';
  static const String cancelled = 'CANCELLED';
}

/// Một lớp môn học giáo viên dạy, theo `GET /teacher/sections`.
class TeacherSectionModel {
  int _id;
  String _code;
  String _subjectCode;
  String _subjectName;
  String _semesterCode;
  String _status;

  TeacherSectionModel({
    required this._id,
    required this._code,
    this._subjectCode = '',
    this._subjectName = '',
    this._semesterCode = '',
    this._status = SectionStatus.open,
  });

  factory TeacherSectionModel.fromJson(Map<String, dynamic> json) {
    return TeacherSectionModel(
      id: (json['id'] as num).toInt(),
      code: json['code'] as String? ?? '',
      subjectCode: json['subjectCode'] as String? ?? '',
      subjectName: json['subjectName'] as String? ?? '',
      semesterCode: json['semesterCode'] as String? ?? '',
      status: json['status'] as String? ?? SectionStatus.open,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': _id,
    'code': _code,
    'subjectCode': _subjectCode,
    'subjectName': _subjectName,
    'semesterCode': _semesterCode,
    'status': _status,
  };

  int get id => _id;
  set id(int value) => _id = value;

  String get code => _code;
  set code(String value) => _code = value;

  String get subjectCode => _subjectCode;
  set subjectCode(String value) => _subjectCode = value;

  String get subjectName => _subjectName;
  set subjectName(String value) => _subjectName = value;

  String get semesterCode => _semesterCode;
  set semesterCode(String value) => _semesterCode = value;

  String get status => _status;
  set status(String value) => _status = value;
}

/// Một buổi của lớp trong chi tiết lớp; [hasAttendance] là đã điểm danh.
/// [status]: `SCHEDULED`, `DONE`, `CANCELLED` hoặc `MAKEUP`.
class SectionSessionModel {
  int _id;
  int _sessionNo;
  String _date;
  String _startTime;
  String _endTime;
  String? _roomCode;
  String _status;
  String? _note;
  bool _hasAttendance;

  SectionSessionModel({
    required this._id,
    required this._sessionNo,
    required this._date,
    this._startTime = '',
    this._endTime = '',
    this._roomCode,
    this._status = 'SCHEDULED',
    this._note,
    this._hasAttendance = false,
  });

  factory SectionSessionModel.fromJson(Map<String, dynamic> json) {
    return SectionSessionModel(
      id: (json['id'] as num).toInt(),
      sessionNo: (json['sessionNo'] as num?)?.toInt() ?? 0,
      date: json['date'] as String? ?? '',
      startTime: json['startTime'] as String? ?? '',
      endTime: json['endTime'] as String? ?? '',
      roomCode: json['roomCode'] as String?,
      status: json['status'] as String? ?? 'SCHEDULED',
      note: json['note'] as String?,
      hasAttendance: json['hasAttendance'] as bool? ?? false,
    );
  }

  int get id => _id;
  set id(int value) => _id = value;

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

  String get status => _status;
  set status(String value) => _status = value;

  String? get note => _note;
  set note(String? value) => _note = value;

  bool get hasAttendance => _hasAttendance;
  set hasAttendance(bool value) => _hasAttendance = value;

  bool get isCancelled => _status == 'CANCELLED';
}

/// Chuyên cần của một học sinh trong lớp.
class SectionStudentModel {
  int _studentId;
  String _studentCode;
  String _fullName;
  AttendanceSummaryModel _summary;

  SectionStudentModel({
    required this._studentId,
    required this._studentCode,
    required this._fullName,
    required this._summary,
  });

  factory SectionStudentModel.fromJson(Map<String, dynamic> json) {
    return SectionStudentModel(
      studentId: (json['studentId'] as num).toInt(),
      studentCode: json['studentCode'] as String? ?? '',
      fullName: json['fullName'] as String? ?? '',
      summary: AttendanceSummaryModel.fromJson(
        json['summary'] as Map<String, dynamic>? ?? const {},
      ),
    );
  }

  int get studentId => _studentId;
  set studentId(int value) => _studentId = value;

  String get studentCode => _studentCode;
  set studentCode(String value) => _studentCode = value;

  String get fullName => _fullName;
  set fullName(String value) => _fullName = value;

  AttendanceSummaryModel get summary => _summary;
  set summary(AttendanceSummaryModel value) => _summary = value;
}

/// Chi tiết lớp theo `GET /teacher/sections/{id}/attendance`: một trang buổi
/// học, một trang học sinh, tổng số buổi và ngưỡng vắng (phần trăm).
class SectionAttendanceModel {
  TeacherSectionModel _section;
  PageModel<SectionSessionModel> _sessions;
  PageModel<SectionStudentModel> _students;
  int _totalSessions;
  int _thresholdPercent;

  SectionAttendanceModel({
    required this._section,
    required this._sessions,
    required this._students,
    this._totalSessions = 0,
    this._thresholdPercent = 0,
  });

  factory SectionAttendanceModel.fromJson(Map<String, dynamic> json) {
    return SectionAttendanceModel(
      section: TeacherSectionModel.fromJson(
        json['section'] as Map<String, dynamic>,
      ),
      sessions: PageModel.fromJson(
        json['sessions'] as Map<String, dynamic>? ?? const {},
        SectionSessionModel.fromJson,
      ),
      students: PageModel.fromJson(
        json['students'] as Map<String, dynamic>? ?? const {},
        SectionStudentModel.fromJson,
      ),
      totalSessions: (json['totalSessions'] as num?)?.toInt() ?? 0,
      thresholdPercent: (json['thresholdPercent'] as num?)?.toInt() ?? 0,
    );
  }

  TeacherSectionModel get section => _section;
  set section(TeacherSectionModel value) => _section = value;

  PageModel<SectionSessionModel> get sessions => _sessions;
  set sessions(PageModel<SectionSessionModel> value) => _sessions = value;

  PageModel<SectionStudentModel> get students => _students;
  set students(PageModel<SectionStudentModel> value) => _students = value;

  int get totalSessions => _totalSessions;
  set totalSessions(int value) => _totalSessions = value;

  int get thresholdPercent => _thresholdPercent;
  set thresholdPercent(int value) => _thresholdPercent = value;
}
