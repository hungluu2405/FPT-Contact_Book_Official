import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';

/// Trạng thái trống: icon trong vòng tròn cam nhạt và một câu giải thích.
class EmptyStateWidget extends StatelessWidget {
  final IconData icon;
  final String message;

  const EmptyStateWidget({
    super.key,
    required this.icon,
    required this.message,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 32, horizontal: 24),
      child: Column(
        children: [
          Container(
            width: 72,
            height: 72,
            decoration: const BoxDecoration(
              color: AppColors.orangeLight,
              shape: BoxShape.circle,
            ),
            child: Icon(icon, size: 36, color: AppColors.orange),
          ),
          const SizedBox(height: 16),
          Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 16, color: AppColors.textMuted),
          ),
        ],
      ),
    );
  }
}
