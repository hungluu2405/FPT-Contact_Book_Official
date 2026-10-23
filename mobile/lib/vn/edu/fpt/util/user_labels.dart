import 'package:flutter/widgets.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';

/// Tên vai trò, giống `role.*` trong messages.properties của web.
const Map<String, String> kRoleLabels = {
  RoleCodes.admin: 'Quản trị viên',
  RoleCodes.headOfDepartment: 'Tổ trưởng chuyên môn',
  RoleCodes.teacher: 'Giáo viên',
  RoleCodes.student: 'Học sinh',
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

/// Vai trò trong câu lạc bộ (giống web).
const Map<String, String> kClubRoleLabels = {
  'PRESIDENT': 'Chủ nhiệm',
  'VICE_PRESIDENT': 'Phó chủ nhiệm',
  'MEMBER': 'Thành viên',
};

/// Trạng thái tham gia câu lạc bộ khác "đang tham gia".
const Map<String, String> kClubStatusLabels = {
  'PENDING': 'Chờ duyệt',
  'REJECTED': 'Bị từ chối',
  'LEFT': 'Đã rời',
};

/// Mức độ thông báo (giống `notification-priority.*` của web).
const Map<String, String> kNotificationPriorityLabels = {
  'NORMAL': 'Thường',
  'IMPORTANT': 'Quan trọng',
  'URGENT': 'Khẩn',
};
