/// Một tổ chuyên môn người xem làm tổ trưởng, theo `GET /head/departments`.
class DepartmentModel {
  int _id;
  String _code;
  String _name;
  int _teacherCount;

  DepartmentModel({
    required this._id,
    required this._code,
    required this._name,
    this._teacherCount = 0,
  });

  factory DepartmentModel.fromJson(Map<String, dynamic> json) {
    return DepartmentModel(
      id: (json['id'] as num).toInt(),
      code: json['code'] as String? ?? '',
      name: json['name'] as String? ?? '',
      teacherCount: (json['teacherCount'] as num?)?.toInt() ?? 0,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': _id,
    'code': _code,
    'name': _name,
    'teacherCount': _teacherCount,
  };

  int get id => _id;
  set id(int value) => _id = value;

  String get code => _code;
  set code(String value) => _code = value;

  String get name => _name;
  set name(String value) => _name = value;

  int get teacherCount => _teacherCount;
  set teacherCount(int value) => _teacherCount = value;
}
