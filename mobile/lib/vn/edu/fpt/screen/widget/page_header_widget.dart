import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/user_labels.dart';

/// Phần đầu của màn hình chức năng: mảng cam nhạt bo góc dưới, dòng trên có
/// nút quay lại, icon, tên màn hình và avatar chữ cái của người đang được
/// xem; dưới là tiêu đề lớn, dòng phụ và [bottom] (ví dụ dải tuần).
class PageHeaderWidget extends StatelessWidget {
  final IconData icon;
  final String title;
  final String headline;
  final String? subtitle;
  final String? viewerName;
  final Widget? bottom;

  const PageHeaderWidget({
    super.key,
    required this.icon,
    required this.title,
    required this.headline,
    this.subtitle,
    this.viewerName,
    this.bottom,
  });

  @override
  Widget build(BuildContext context) {
    final topInset = MediaQuery.of(context).padding.top;
    final canPop = Navigator.of(context).canPop();
    return Container(
      width: double.infinity,
      padding: EdgeInsets.fromLTRB(8, topInset + 4, 16, 16),
      decoration: const BoxDecoration(
        color: AppColors.orangeLight,
        borderRadius: BorderRadius.vertical(bottom: Radius.circular(24)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              if (canPop)
                IconButton(
                  tooltip: 'Quay lại',
                  icon: const Icon(Icons.arrow_back, color: AppColors.navy),
                  onPressed: () => Navigator.of(context).maybePop(),
                )
              else
                const SizedBox(width: 8),
              Icon(icon, color: AppColors.orange, size: 24),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  title,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                    color: AppColors.navy,
                  ),
                ),
              ),
              if (viewerName != null)
                CircleAvatar(
                  radius: 18,
                  backgroundColor: AppColors.navy,
                  child: Text(
                    initialOf(viewerName!),
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: AppColors.orange,
                    ),
                  ),
                ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.only(left: 8, top: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  headline,
                  style: const TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                    color: AppColors.navy,
                  ),
                ),
                if (subtitle != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    subtitle!,
                    style: const TextStyle(
                      fontSize: 14,
                      color: AppColors.textMuted,
                    ),
                  ),
                ],
                if (bottom != null) ...[const SizedBox(height: 16), bottom!],
              ],
            ),
          ),
        ],
      ),
    );
  }
}
