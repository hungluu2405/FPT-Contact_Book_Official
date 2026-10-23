import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';

/// Ô chức năng trên trang chủ: icon cam trên nền tròn cam nhạt và nhãn.
/// [onTap] null là chức năng chưa có, ô hiện mờ kèm nhãn "Sắp có";
/// [badgeCount] lớn hơn 0 thì hiện số đỏ trên icon (ví dụ đơn chờ duyệt).
class FeatureTileWidget extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback? onTap;
  final int? badgeCount;

  const FeatureTileWidget({
    super.key,
    required this.icon,
    required this.label,
    this.onTap,
    this.badgeCount,
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
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 16),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Container(
                  width: 52,
                  height: 52,
                  decoration: const BoxDecoration(
                    color: AppColors.orangeLight,
                    shape: BoxShape.circle,
                  ),
                  child: (badgeCount ?? 0) > 0
                      ? Badge(
                          label: Text(badgeCount! > 99 ? '99+' : '$badgeCount'),
                          child: Icon(icon, size: 28, color: AppColors.orange),
                        )
                      : Icon(icon, size: 28, color: AppColors.orange),
                ),
                const SizedBox(height: 10),
                Text(
                  label,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: AppColors.text,
                  ),
                ),
                if (!enabled) ...[
                  const SizedBox(height: 4),
                  const Text(
                    'Sắp có',
                    style: TextStyle(fontSize: 13, color: AppColors.textMuted),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
