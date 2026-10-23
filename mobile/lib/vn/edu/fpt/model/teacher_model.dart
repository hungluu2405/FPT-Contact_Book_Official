import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';

/// Một giáo viên trong danh sách của tổ trưởng (`GET /head/teachers`);
/// [head] là giáo viên đó cũng làm tổ trưởng.
class TeacherModel {
  int _userId;
  String _teacherCode;
  String _fullName;
  String _phone;
  bool _active;
  bool _head;

  TeacherModel({
    required this._userId,
    required this._teacherCode,
    required this._fullName,
    this._phone = '',
    this._active = true,
    this._head = false,
  });

  factory TeacherModel.fromJson(Map<String, dynamic> json) {
    return TeacherModel(
      userId: (json['userId'] as num).toInt(),
      teacherCode: json['teacherCode'] as String? ?? '',
      fullName: json['fullName'] as String? ?? '',
      phone: json['phone'] as String? ?? '',
      active: json['active'] as bool? ?? true,
      head: json['head'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toJson() => {
    'userId': _userId,
    'teacherCode': _teacherCode,
    'fullName': _fullName,
    'phone': _phone,
    'active': _active,
    'head': _head,
  };

  int get userId => _userId;
  set userId(int value) => _userId = value;

  String get teacherCode => _teacherCode;
  set teacherCode(String value) => _teacherCode = value;

  String get fullName => _fullName;
  set fullName(String value) => _fullName = value;

  String get phone => _phone;
  set phone(String value) => _phone = value;

  bool get active => _active;
  set active(bool value) => _active = value;

  bool get head => _head;
  set head(bool value) => _head = value;
}

/// Chi tiết một giáo viên trong tổ (`GET /head/teachers/{userId}`): thông tin
/// công việc và liên lạc. [lastLoginAt] dạng `yyyy-MM-ddTHH:mm:ss`, null là
/// chưa đăng nhập lần nào.
class TeacherDetailModel {
  int _userId;
  String _teacherCode;
  String _fullName;
  String? _academicTitle;
  String? _departmentName;
  String _phone;
  String? _email;
  bool _active;
  String? _lastLoginAt;
  List<String> _roles;

  TeacherDetailModel({
    required this._userId,
    required this._teacherCode,
    required this._fullName,
    this._academicTitle,
    this._departmentName,
    this._phone = '',
    this._email,
    this._active = true,
    this._lastLoginAt,
    List<String> roles = const [],
  }) : _roles = List<String>.of(roles);

  factory TeacherDetailModel.fromJson(Map<String, dynamic> json) {
    return TeacherDetailModel(
      userId: (json['userId'] as num).toInt(),
      teacherCode: json['teacherCode'] as String? ?? '',
      fullName: json['fullName'] as String? ?? '',
      academicTitle: json['academicTitle'] as String?,
      departmentName: json['departmentName'] as String?,
      phone: json['phone'] as String? ?? '',
      email: json['email'] as String?,
      active: json['active'] as bool? ?? true,
      lastLoginAt: json['lastLoginAt'] as String?,
      roles: (json['roles'] as List<dynamic>? ?? const [])
          .map((role) => role as String)
          .toList(),
    );
  }

  Map<String, dynamic> toJson() => {
    'userId': _userId,
    'teacherCode': _teacherCode,
    'fullName': _fullName,
    'academicTitle': _academicTitle,
    'departmentName': _departmentName,
    'phone': _phone,
    'email': _email,
    'active': _active,
    'lastLoginAt': _lastLoginAt,
    'roles': _roles,
  };

  int get userId => _userId;
  set userId(int value) => _userId = value;

  String get teacherCode => _teacherCode;
  set teacherCode(String value) => _teacherCode = value;

  String get fullName => _fullName;
  set fullName(String value) => _fullName = value;

  String? get academicTitle => _academicTitle;
  set academicTitle(String? value) => _academicTitle = value;

  String? get departmentName => _departmentName;
  set departmentName(String? value) => _departmentName = value;

  String get phone => _phone;
  set phone(String value) => _phone = value;

  String? get email => _email;
  set email(String? value) => _email = value;

  bool get active => _active;
  set active(bool value) => _active = value;

  String? get lastLoginAt => _lastLoginAt;
  set lastLoginAt(String? value) => _lastLoginAt = value;

  List<String> get roles => List<String>.unmodifiable(_roles);
  set roles(List<String> value) => _roles = List<String>.of(value);

  bool get isHead => _roles.contains(RoleCodes.headOfDepartment);
}
