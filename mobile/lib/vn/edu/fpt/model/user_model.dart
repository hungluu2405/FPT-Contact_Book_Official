/// Mã vai trò, trùng với dữ liệu bảng `roles` của backend.
class RoleCodes {
  RoleCodes._();

  static const String admin = 'ADMIN';
  static const String headOfDepartment = 'HEAD_OF_DEPARTMENT';
  static const String teacher = 'TEACHER';
  static const String student = 'STUDENT';
  static const String parent = 'PARENT';
}

/// Người dùng đang đăng nhập, theo `user` của phản hồi đăng nhập và `GET /me`.
class UserModel {
  int _id;
  String _phone;
  String _fullName;
  List<String> _roles;
  bool _mustChangePassword;

  UserModel({
    required this._id,
    required this._phone,
    required this._fullName,
    required List<String> roles,
    this._mustChangePassword = false,
  }) : _roles = List<String>.of(roles);

  factory UserModel.fromJson(Map<String, dynamic> json) {
    return UserModel(
      id: (json['id'] as num).toInt(),
      phone: json['phone'] as String? ?? '',
      fullName: json['fullName'] as String? ?? '',
      roles: (json['roles'] as List<dynamic>? ?? const [])
          .map((role) => role as String)
          .toList(),
      mustChangePassword: json['mustChangePassword'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': _id,
    'phone': _phone,
    'fullName': _fullName,
    'roles': List<String>.of(_roles),
    'mustChangePassword': _mustChangePassword,
  };

  int get id => _id;
  set id(int value) => _id = value;

  String get phone => _phone;
  set phone(String value) => _phone = value;

  String get fullName => _fullName;
  set fullName(String value) => _fullName = value;

  List<String> get roles => List<String>.unmodifiable(_roles);
  set roles(List<String> value) => _roles = List<String>.of(value);

  bool get mustChangePassword => _mustChangePassword;
  set mustChangePassword(bool value) => _mustChangePassword = value;

  bool hasRole(String roleCode) => _roles.contains(roleCode);

  bool get isStudent => hasRole(RoleCodes.student);

  bool get isParent => hasRole(RoleCodes.parent);

  /// Trưởng bộ môn dùng app như giảng viên (API /teacher mở cho cả hai).
  bool get isTeacher =>
      hasRole(RoleCodes.teacher) || hasRole(RoleCodes.headOfDepartment);

  /// App chỉ có chức năng cho SV, PH và GV; tài khoản chỉ có ADMIN dùng web.
  bool get canUseMobile => isStudent || isParent || isTeacher;
}
