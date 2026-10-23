import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/department_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_theme.dart';

/// Khối "Tổ chuyên môn" trên trang chủ của tổ trưởng: tổ đang phụ trách (có
/// ô chọn khi phụ trách nhiều tổ) và các chức năng quản lý tổ: giáo viên
/// trong tổ, gửi thông báo cả tổ, thông báo đã gửi.
class DepartmentPanelWidget extends StatelessWidget {
  final DepartmentController controller;
  final VoidCallback onOpenTeachers;
  final VoidCallback onSendNotification;
  final VoidCallback onOpenSent;

  const DepartmentPanelWidget({
    super.key,
    required this.controller,
    required this.onOpenTeachers,
    required this.onSendNotification,
    required this.onOpenSent,
  });

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) => Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.orangeLight,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Row(
              children: [
                Icon(Icons.apartment, color: AppColors.orange),
                SizedBox(width: 8),
                Text(
                  'Tổ chuyên môn',
                  style: TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w700,
                    color: AppColors.navy,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            ..._content(),
          ],
        ),
      ),
    );
  }

  List<Widget> _content() {
    final departments = controller.departments;
    final selected = controller.selectedDepartment;
    final error = controller.departmentsError;
    if (departments.isEmpty && controller.isLoadingDepartments) {
      return const [
        Padding(
          padding: EdgeInsets.all(12),
          child: Center(
            child: SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(
                strokeWidth: 2.5,
                color: AppColors.orange,
              ),
            ),
          ),
        ),
      ];
    }
    if (error != null && departments.isEmpty) {
      return [
        Row(
          children: [
            Expanded(
              child: Text(
                error.message,
                style: const TextStyle(fontSize: 14, color: AppColors.error),
              ),
            ),
            TextButton(
              onPressed: controller.loadDepartments,
              child: const Text('Thử lại'),
            ),
          ],
        ),
      ];
    }
    if (selected == null) {
      return const [
        Text(
          'Tài khoản chưa được gán tổ chuyên môn nào. '
          'Vui lòng liên hệ quản trị viên.',
          style: TextStyle(fontSize: 15, color: AppColors.textMuted),
        ),
      ];
    }
    return [
      Text(
        'Tổ trưởng · ${selected.name} · ${selected.teacherCount} giáo viên',
        style: const TextStyle(
          fontSize: 15,
          fontWeight: FontWeight.w600,
          color: AppColors.orangeDark,
        ),
      ),
      if (departments.length > 1) ...[
        const SizedBox(height: 10),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final department in departments)
              ChoiceChip(
                label: Text(department.name),
                selected: department.id == selected.id,
                onSelected: (_) => controller.selectDepartment(department),
                selectedColor: AppColors.orange,
                backgroundColor: Colors.white,
                labelStyle: TextStyle(
                  fontFamily: kAppFontFamily,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: department.id == selected.id
                      ? Colors.white
                      : AppColors.text,
                ),
                showCheckmark: false,
              ),
          ],
        ),
      ],
      const SizedBox(height: 12),
      _PanelAction(
        icon: Icons.badge_outlined,
        label: 'Giáo viên trong tổ',
        description: 'Danh sách, thông tin liên lạc',
        onTap: onOpenTeachers,
      ),
      const SizedBox(height: 8),
      _PanelAction(
        icon: Icons.campaign_outlined,
        label: 'Gửi thông báo tổ',
        description: 'Gửi cả tổ; gửi riêng ở trang giáo viên',
        onTap: onSendNotification,
      ),
      const SizedBox(height: 8),
      _PanelAction(
        icon: Icons.outbox_outlined,
        label: 'Thông báo đã gửi',
        description: 'Xem ai đã đọc, ai chưa đọc',
        onTap: onOpenSent,
      ),
    ];
  }
}

/// Một chức năng của tổ: hàng trắng có icon, tên, dòng mô tả. [onTap] null
/// là chức năng chưa có, hàng mờ kèm nhãn "Sắp có".
class _PanelAction extends StatelessWidget {
  final IconData icon;
  final String label;
  final String description;
  final VoidCallback? onTap;

  const _PanelAction({
    required this.icon,
    required this.label,
    required this.description,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final enabled = onTap != null;
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: AppColors.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Opacity(
          opacity: enabled ? 1 : 0.5,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 12, 8, 12),
            child: Row(
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: const BoxDecoration(
                    color: AppColors.orangeLight,
                    shape: BoxShape.circle,
                  ),
                  child: Icon(icon, color: AppColors.orange),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        label,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                          color: AppColors.navy,
                        ),
                      ),
                      Text(
                        description,
                        style: const TextStyle(
                          fontSize: 13,
                          color: AppColors.textMuted,
                        ),
                      ),
                    ],
                  ),
                ),
                if (enabled)
                  const Icon(Icons.chevron_right, color: AppColors.textMuted)
                else
                  const Padding(
                    padding: EdgeInsets.only(right: 4),
                    child: StatusBadgeWidget(
                      label: 'Sắp có',
                      tone: BadgeTone.muted,
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
