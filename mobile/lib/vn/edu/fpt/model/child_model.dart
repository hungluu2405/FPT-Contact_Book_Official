/// Một con đã liên kết với phụ huynh, theo `GET /parent/children`.
/// [id] là `students.id`, dùng trong đường dẫn `/parent/children/{id}/...`.
class ChildModel {
  int _id;
  String _code;
  String _name;

  ChildModel({required this._id, required this._code, required this._name});

  factory ChildModel.fromJson(Map<String, dynamic> json) {
    return ChildModel(
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
