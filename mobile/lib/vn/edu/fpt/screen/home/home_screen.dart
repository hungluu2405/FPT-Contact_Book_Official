import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/auth_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';

/// Tên vai trò, giống `role.*` trong messages.properties của web.
const Map<String, String> kRoleLabels = {
  RoleCodes.admin: 'Quản trị viên',
  RoleCodes.headOfDepartment: 'Trưởng bộ môn',
  RoleCodes.teacher: 'Giảng viên',
  RoleCodes.student: 'Sinh viên',
  RoleCodes.parent: 'Phụ huynh',
};

/// Trang chủ tạm của giai đoạn 10a: chào người dùng, vai trò và đăng xuất.
/// Chức năng theo vai trò được thêm ở 10b, 10c, 10d.
class HomeScreen extends StatelessWidget {
  final AuthController controller;

  const HomeScreen({super.key, required this.controller});

  Future<void> _confirmLogout(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Đăng xuất'),
        content: const Text('Bạn có chắc muốn đăng xuất khỏi ứng dụng?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            style: TextButton.styleFrom(foregroundColor: AppColors.textMuted),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: FilledButton.styleFrom(minimumSize: const Size(96, 48)),
            child: const Text('Đăng xuất'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await controller.logout();
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = controller.currentUser;
    final roleLabels = (user?.roles ?? const <String>[])
        .map((role) => kRoleLabels[role] ?? role)
        .join(', ');

    return Scaffold(
      appBar: AppBar(
        title: const Text('Trang chủ'),
        actions: [
          IconButton(
            tooltip: 'Đăng xuất',
            icon: const Icon(Icons.logout),
            onPressed: () => _confirmLogout(context),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: AppColors.orangeLight,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Xin chào, ${user?.fullName ?? ''}',
                  style: const TextStyle(
                    color: AppColors.navy,
                    fontSize: 22,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  roleLabels,
                  style: const TextStyle(
                    color: AppColors.orangeDark,
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          Text(
            user != null && !user.canUseMobile
                ? 'Tài khoản quản trị dùng trên trang web của nhà trường.'
                : 'Các chức năng sẽ có trong bản cập nhật tiếp theo.',
            style: const TextStyle(fontSize: 16, color: AppColors.textMuted),
          ),
        ],
      ),
    );
  }
}
