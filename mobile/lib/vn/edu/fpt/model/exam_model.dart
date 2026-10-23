/// Mã `examType` (docs/API.md mục "Giá trị enum").
class ExamType {
  ExamType._();

  static const String midterm = 'MIDTERM';
  static const String finalExam = 'FINAL';
  static const String retake = 'RETAKE';
}

/// Một lịch thi theo `GET .../exams`. Ngày `yyyy-MM-dd`, giờ `HH:mm:ss`.
class ExamModel {
  int _id;
  String _sectionCode;
  String _subjectCode;
  String _subjectName;
  String _examType;
  String _date;
  String _startTime;
  String _endTime;
  String? _roomCode;
  String? _note;

  ExamModel({
    required this._id,
    required this._sectionCode,
    required this._subjectCode,
    required this._subjectName,
    required this._examType,
    required this._date,
    required this._startTime,
    required this._endTime,
    this._roomCode,
    this._note,
  });

  factory ExamModel.fromJson(Map<String, dynamic> json) {
    return ExamModel(
      id: (json['id'] as num).toInt(),
      sectionCode: json['sectionCode'] as String? ?? '',
      subjectCode: json['subjectCode'] as String? ?? '',
      subjectName: json['subjectName'] as String? ?? '',
      examType: json['examType'] as String? ?? '',
      date: json['date'] as String? ?? '',
      startTime: json['startTime'] as String? ?? '',
      endTime: json['endTime'] as String? ?? '',
      roomCode: json['roomCode'] as String?,
      note: json['note'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': _id,
    'sectionCode': _sectionCode,
    'subjectCode': _subjectCode,
    'subjectName': _subjectName,
    'examType': _examType,
    'date': _date,
    'startTime': _startTime,
    'endTime': _endTime,
    'roomCode': _roomCode,
    'note': _note,
  };

  int get id => _id;
  set id(int value) => _id = value;

  String get sectionCode => _sectionCode;
  set sectionCode(String value) => _sectionCode = value;

  String get subjectCode => _subjectCode;
  set subjectCode(String value) => _subjectCode = value;

  String get subjectName => _subjectName;
  set subjectName(String value) => _subjectName = value;

  String get examType => _examType;
  set examType(String value) => _examType = value;

  String get date => _date;
  set date(String value) => _date = value;

  String get startTime => _startTime;
  set startTime(String value) => _startTime = value;

  String get endTime => _endTime;
  set endTime(String value) => _endTime = value;

  String? get roomCode => _roomCode;
  set roomCode(String? value) => _roomCode = value;

  String? get note => _note;
  set note(String? value) => _note = value;
}
