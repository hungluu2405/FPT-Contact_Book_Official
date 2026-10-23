import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';

/// Kiểu màu của nhãn trạng thái.
enum BadgeTone { orange, muted, info, success, danger }

/// Nhãn bo tròn nhỏ: "Đang diễn ra", "Đã hủy", "Cuối kỳ"... [dot] thêm chấm
/// tròn phía trước cho trạng thái đang diễn ra.
class StatusBadgeWidget extends StatelessWidget {
  final String label;
  final BadgeTone tone;
  final bool dot;

  const StatusBadgeWidget({
    super.key,
    required this.label,
    this.tone = BadgeTone.orange,
    this.dot = false,
  });

  @override
  Widget build(BuildContext context) {
    final (background, foreground) = switch (tone) {
      BadgeTone.orange => (AppColors.orangeLight, AppColors.orangeDark),
      BadgeTone.muted => (const Color(0xFFF3F4F6), AppColors.textMuted),
      BadgeTone.info => (const Color(0xFFEFF6FF), AppColors.fptBlue),
      BadgeTone.success => (const Color(0xFFECFDF5), AppColors.success),
      BadgeTone.danger => (AppColors.errorLight, AppColors.error),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (dot) ...[
            Container(
              width: 6,
              height: 6,
              decoration: BoxDecoration(
                color: foreground,
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(width: 4),
          ],
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: foreground,
            ),
          ),
        ],
      ),
    );
  }
}
