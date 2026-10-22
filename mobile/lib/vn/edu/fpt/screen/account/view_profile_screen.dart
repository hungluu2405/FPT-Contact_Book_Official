import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/auth_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/profile_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/profile_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/change_password_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_theme.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/user_labels.dart';

/// Tab Tài khoản: hồ sơ, đổi mật khẩu, đăng xuất.
class ViewProfileScreen extends StatelessWidget {
  final ProfileController controller;
  final AuthController authController;

  const ViewProfileScreen({
    super.key,
    required this.controller,
    required this.authController,
  });

  Future<void> _changePassword(BuildContext context) async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) =>
            ChangePasswordScreen(controller: authController, forced: false),
      ),
    );
    if (changed == true && context.mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Đổi mật khẩu thành công')));
    }
  }

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
      await authController.logout();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Tài khoản')),
      body: ListenableBuilder(
        listenable: controller,
        builder: (context, _) => RefreshIndicator(
          color: AppColors.orange,
          onRefresh: () => controller.load(force: true),
          // Nội dung ngắn nên dựng hết một lần thay cho ListView.
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                ..._profileSection(),
                const SizedBox(height: 24),
                OutlinedButton.icon(
                  onPressed: () => _changePassword(context),
                  icon: const Icon(Icons.lock_reset),
                  label: const Text('Đổi mật khẩu'),
                  style: _buttonStyle(AppColors.orangeDark),
                ),
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  onPressed: () => _confirmLogout(context),
                  icon: const Icon(Icons.logout),
                  label: const Text('Đăng xuất'),
                  style: _buttonStyle(AppColors.textMuted),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  ButtonStyle _buttonStyle(Color color) => OutlinedButton.styleFrom(
    foregroundColor: color,
    minimumSize: const Size.fromHeight(52),
    side: const BorderSide(color: AppColors.border),
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
    textStyle: const TextStyle(
      fontFamily: kAppFontFamily,
      fontSize: 16,
      fontWeight: FontWeight.w600,
    ),
  );

  List<Widget> _profileSection() {
    final profile = controller.profile;
    final error = controller.error;
    if (profile == null && error != null) {
      return [
        ErrorRetryWidget(
          message: error.message,
          onRetry: () => controller.load(force: true),
        ),
      ];
    }
    if (profile == null) {
      return const [
        Padding(
          padding: EdgeInsets.all(24),
          child: Center(
            child: CircularProgressIndicator(color: AppColors.orange),
          ),
        ),
      ];
    }
    return [
      Row(
        children: [
          CircleAvatar(
            radius: 32,
            backgroundColor: AppColors.orangeLight,
            child: Text(
              initialOf(profile.fullName),
              style: const TextStyle(
                fontSize: 28,
                fontWeight: FontWeight.w700,
                color: AppColors.orangeDark,
              ),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Text(
              profile.fullName,
              style: const TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.w700,
                color: AppColors.navy,
              ),
            ),
          ),
        ],
      ),
      const SizedBox(height: 16),
      _InfoRow(label: 'Số điện thoại', value: profile.phone),
      _InfoRow(label: 'Email', value: profile.email),
      _InfoRow(label: 'Ngày sinh', value: formatApiDate(profile.dateOfBirth)),
      _InfoRow(label: 'Giới tính', value: kGenderLabels[profile.gender]),
      _InfoRow(label: 'Địa chỉ', value: profile.address),
      _InfoRow(label: 'Vai trò', value: _roles(profile)),
    ];
  }

  String _roles(ProfileModel profile) => profile.roles
      .map((role) {
        final label = kRoleLabels[role.roleCode] ?? role.roleCode;
        final department = role.departmentName;
        return department == null ? label : '$label ($department)';
      })
      .join(', ');
}

/// Một dòng thông tin; giá trị trống hiện "Chưa cập nhật".
class _InfoRow extends StatelessWidget {
  final String label;
  final String? value;

  const _InfoRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    final text = value ?? '';
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 12),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: AppColors.border)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(fontSize: 14, color: AppColors.textMuted),
          ),
          const SizedBox(height: 2),
          Text(
            text.isEmpty ? 'Chưa cập nhật' : text,
            style: TextStyle(
              fontSize: 16,
              color: text.isEmpty ? AppColors.hint : AppColors.text,
            ),
          ),
        ],
      ),
    );
  }
}
