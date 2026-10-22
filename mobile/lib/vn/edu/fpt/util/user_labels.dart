import 'package:flutter/widgets.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';

/// Tên vai trò, giống `role.*` trong messages.properties của web.
const Map<String, String> kRoleLabels = {
  RoleCodes.admin: 'Quản trị viên',
  RoleCodes.headOfDepartment: 'Trưởng bộ môn',
  RoleCodes.teacher: 'Giảng viên',
  RoleCodes.student: 'Sinh viên',
  RoleCodes.parent: 'Phụ huynh',
};

/// Mã `gender` của API (docs/API.md mục "Giá trị enum").
const Map<String, String> kGenderLabels = {
  'MALE': 'Nam',
  'FEMALE': 'Nữ',
  'OTHER': 'Khác',
};

String roleLabelsOf(UserModel? user) => (user?.roles ?? const <String>[])
    .map((role) => kRoleLabels[role] ?? role)
    .join(', ');

/// Chữ cái đầu của tên (từ cuối của họ tên kiểu Việt) cho avatar mặc định.
String initialOf(String fullName) {
  final words = fullName.trim().split(RegExp(r'\s+'));
  final last = words.isEmpty ? '' : words.last;
  return last.isEmpty ? '?' : last.characters.first.toUpperCase();
}
