/// Học kỳ đi kèm dữ liệu học tập (`semester` của InSemester). Ngày giữ dạng
/// `yyyy-MM-dd` của API.
class SemesterModel {
  int _id;
  String _code;
  String _name;
  String _startDate;
  String _endDate;
  bool _current;

  SemesterModel({
    required this._id,
    required this._code,
    required this._name,
    required this._startDate,
    required this._endDate,
    this._current = false,
  });

  factory SemesterModel.fromJson(Map<String, dynamic> json) {
    return SemesterModel(
      id: (json['id'] as num).toInt(),
      code: json['code'] as String? ?? '',
      name: json['name'] as String? ?? '',
      startDate: json['startDate'] as String? ?? '',
      endDate: json['endDate'] as String? ?? '',
      current: json['current'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': _id,
    'code': _code,
    'name': _name,
    'startDate': _startDate,
    'endDate': _endDate,
    'current': _current,
  };

  int get id => _id;
  set id(int value) => _id = value;

  String get code => _code;
  set code(String value) => _code = value;

  String get name => _name;
  set name(String value) => _name = value;

  String get startDate => _startDate;
  set startDate(String value) => _startDate = value;

  String get endDate => _endDate;
  set endDate(String value) => _endDate = value;

  bool get current => _current;
  set current(bool value) => _current = value;
}

/// Dữ liệu kèm học kỳ mà nó thuộc về, theo dạng `{semester, data}` của API.
class InSemesterModel<T> {
  SemesterModel _semester;
  T _data;

  InSemesterModel({required this._semester, required this._data});

  factory InSemesterModel.fromJson(
    Map<String, dynamic> json,
    T Function(Object? data) dataFromJson,
  ) {
    return InSemesterModel(
      semester: SemesterModel.fromJson(
        json['semester'] as Map<String, dynamic>,
      ),
      data: dataFromJson(json['data']),
    );
  }

  SemesterModel get semester => _semester;
  set semester(SemesterModel value) => _semester = value;

  T get data => _data;
  set data(T value) => _data = value;
}
