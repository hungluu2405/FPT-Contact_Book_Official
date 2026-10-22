/// Một vai trò trong hồ sơ; [departmentName] chỉ có với trưởng bộ môn.
class RoleAssignmentModel {
  String _roleCode;
  String? _departmentCode;
  String? _departmentName;

  RoleAssignmentModel({
    required this._roleCode,
    this._departmentCode,
    this._departmentName,
  });

  factory RoleAssignmentModel.fromJson(Map<String, dynamic> json) {
    return RoleAssignmentModel(
      roleCode: json['roleCode'] as String? ?? '',
      departmentCode: json['departmentCode'] as String?,
      departmentName: json['departmentName'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
    'roleCode': _roleCode,
    'departmentCode': _departmentCode,
    'departmentName': _departmentName,
  };

  String get roleCode => _roleCode;
  set roleCode(String value) => _roleCode = value;

  String? get departmentCode => _departmentCode;
  set departmentCode(String? value) => _departmentCode = value;

  String? get departmentName => _departmentName;
  set departmentName(String? value) => _departmentName = value;
}

/// Hồ sơ của người đang đăng nhập, theo `GET /me/profile`.
/// Ngày sinh giữ dạng `yyyy-MM-dd` của API; màn hình đổi sang dd/MM/yyyy.
class ProfileModel {
  String _phone;
  String _fullName;
  String? _email;
  String? _gender;
  String? _dateOfBirth;
  String? _address;
  List<RoleAssignmentModel> _roles;

  ProfileModel({
    required this._phone,
    required this._fullName,
    this._email,
    this._gender,
    this._dateOfBirth,
    this._address,
    List<RoleAssignmentModel> roles = const [],
  }) : _roles = List<RoleAssignmentModel>.of(roles);

  factory ProfileModel.fromJson(Map<String, dynamic> json) {
    return ProfileModel(
      phone: json['phone'] as String? ?? '',
      fullName: json['fullName'] as String? ?? '',
      email: json['email'] as String?,
      gender: json['gender'] as String?,
      dateOfBirth: json['dateOfBirth'] as String?,
      address: json['address'] as String?,
      roles: (json['roles'] as List<dynamic>? ?? const [])
          .map(
            (role) =>
                RoleAssignmentModel.fromJson(role as Map<String, dynamic>),
          )
          .toList(),
    );
  }

  Map<String, dynamic> toJson() => {
    'phone': _phone,
    'fullName': _fullName,
    'email': _email,
    'gender': _gender,
    'dateOfBirth': _dateOfBirth,
    'address': _address,
    'roles': [for (final role in _roles) role.toJson()],
  };

  String get phone => _phone;
  set phone(String value) => _phone = value;

  String get fullName => _fullName;
  set fullName(String value) => _fullName = value;

  String? get email => _email;
  set email(String? value) => _email = value;

  /// `MALE`, `FEMALE` hoặc `OTHER`.
  String? get gender => _gender;
  set gender(String? value) => _gender = value;

  String? get dateOfBirth => _dateOfBirth;
  set dateOfBirth(String? value) => _dateOfBirth = value;

  String? get address => _address;
  set address(String? value) => _address = value;

  List<RoleAssignmentModel> get roles =>
      List<RoleAssignmentModel>.unmodifiable(_roles);
  set roles(List<RoleAssignmentModel> value) =>
      _roles = List<RoleAssignmentModel>.of(value);
}
