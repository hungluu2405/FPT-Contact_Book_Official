/// Một học kỳ trong ô chọn học kỳ, theo `GET /semesters` (`{id, code, name}`),
/// mới nhất trước.
class SemesterOptionModel {
  int _id;
  String _code;
  String _name;

  SemesterOptionModel({
    required this._id,
    required this._code,
    required this._name,
  });

  factory SemesterOptionModel.fromJson(Map<String, dynamic> json) {
    return SemesterOptionModel(
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
