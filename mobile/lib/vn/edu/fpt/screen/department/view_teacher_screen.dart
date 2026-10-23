import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/department_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/department_notification_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/teacher_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/department/add_department_notification_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/user_labels.dart';

/// Chi tiết một giáo viên trong tổ: thông tin công việc và liên lạc. Số điện
/// thoại và email có nút sao chép; nút "Gửi riêng thông báo" mở form cho
/// riêng giáo viên này (không có với chính mình và tài khoản đã khóa). Nơi
/// mở màn hình đã gọi [DepartmentController.openTeacher].
class ViewTeacherScreen extends StatelessWidget {
  final DepartmentController controller;
  final DepartmentNotificationController notificationController;
  final int userId;
  final int? currentUserId;

  const ViewTeacherScreen({
    super.key,
    required this.controller,
    required this.notificationController,
    required this.userId,
    required this.currentUserId,
  });

  void _sendToTeacher(BuildContext context, TeacherDetailModel teacher) {
    notificationController.startCompose(
      department: controller.selectedDepartment,
      teacherUserId: teacher.userId,
      teacherName: teacher.fullName,
    );
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) =>
            AddDepartmentNotificationScreen(controller: notificationController),
      ),
    );
  }

  Future<void> _copy(BuildContext context, String label, String value) async {
    final messenger = ScaffoldMessenger.of(context);
    await Clipboard.setData(ClipboardData(text: value));
    messenger.showSnackBar(SnackBar(content: Text('Đã sao chép $label.')));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListenableBuilder(
        listenable: controller,
        builder: (context, _) {
          final detail = controller.detail?.userId == userId
              ? controller.detail
              : null;
          return RefreshIndicator(
            color: AppColors.orange,
            onRefresh: () => controller.openTeacher(userId),
            child: ListView(
              padding: EdgeInsets.zero,
              children: [
                PageHeaderWidget(
                  icon: Icons.badge_outlined,
                  title: 'Tổ chuyên môn',
                  headline: detail?.fullName ?? 'Giáo viên',
                  subtitle: detail?.teacherCode,
                  viewerName: detail?.fullName,
                ),
                ..._content(context, detail),
              ],
            ),
          );
        },
      ),
    );
  }

  List<Widget> _content(BuildContext context, TeacherDetailModel? detail) {
    final error = controller.detailError;
    if (error != null) {
      return [
        ErrorRetryWidget(
          message: error.message,
          onRetry: () => controller.openTeacher(userId),
        ),
      ];
    }
    if (detail == null) {
      return const [
        Padding(
          padding: EdgeInsets.all(24),
          child: Center(
            child: CircularProgressIndicator(color: AppColors.orange),
          ),
        ),
      ];
    }
    final email = detail.email ?? '';
    final lastLogin = detail.lastLoginAt;
    return [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Wrap(
              spacing: 8,
              runSpacing: 6,
              children: [
                for (final role in detail.roles)
                  StatusBadgeWidget(
                    label: kRoleLabels[role] ?? role,
                    tone: BadgeTone.orange,
                  ),
                StatusBadgeWidget(
                  label: detail.active ? 'Đang hoạt động' : 'Đã khóa',
                  tone: detail.active ? BadgeTone.success : BadgeTone.muted,
                ),
              ],
            ),
            const SizedBox(height: 12),
            _InfoRow(label: 'Mã giáo viên', value: detail.teacherCode),
            _InfoRow(label: 'Học hàm', value: detail.academicTitle ?? ''),
            _InfoRow(label: 'Tổ', value: detail.departmentName ?? ''),
            _InfoRow(
              label: 'Đăng nhập gần nhất',
              value: lastLogin == null
                  ? 'Chưa đăng nhập'
                  : formatApiDateTime(lastLogin),
            ),
            const SizedBox(height: 16),
            const Text(
              'Liên lạc',
              style: TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w700,
                color: AppColors.navy,
              ),
            ),
            const SizedBox(height: 10),
            _ContactTile(
              icon: Icons.phone_outlined,
              label: 'Số điện thoại',
              value: detail.phone,
              onCopy: () => _copy(context, 'số điện thoại', detail.phone),
            ),
            if (email.isNotEmpty) ...[
              const SizedBox(height: 10),
              _ContactTile(
                icon: Icons.email_outlined,
                label: 'Email',
                value: email,
                onCopy: () => _copy(context, 'email', email),
              ),
            ],
            if (detail.active && detail.userId != currentUserId) ...[
              const SizedBox(height: 24),
              FilledButton.icon(
                onPressed: () => _sendToTeacher(context, detail),
                icon: const Icon(Icons.campaign_outlined),
                label: const Text('Gửi riêng thông báo'),
              ),
            ],
          ],
        ),
      ),
    ];
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;

  const _InfoRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    if (value.isEmpty) {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 150,
            child: Text(
              label,
              style: const TextStyle(fontSize: 15, color: AppColors.textMuted),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w600,
                color: AppColors.text,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Một cách liên lạc: chữ chọn được để sao chép, kèm nút sao chép lớn.
class _ContactTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final VoidCallback onCopy;

  const _ContactTile({
    required this.icon,
    required this.label,
    required this.value,
    required this.onCopy,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 10, 4, 10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          Icon(icon, color: AppColors.orange),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: const TextStyle(
                    fontSize: 13,
                    color: AppColors.textMuted,
                  ),
                ),
                SelectableText(
                  value,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: AppColors.text,
                  ),
                ),
              ],
            ),
          ),
          IconButton(
            tooltip: 'Sao chép $label',
            icon: const Icon(Icons.copy, color: AppColors.orangeDark),
            onPressed: onCopy,
          ),
        ],
      ),
    );
  }
}
