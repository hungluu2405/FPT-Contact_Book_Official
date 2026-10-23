import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';

/// Thanh "đã đọc / người nhận" của thông báo đã gửi, kèm dòng "2/3 đã đọc".
class ReadProgressWidget extends StatelessWidget {
  final int readCount;
  final int recipientCount;

  const ReadProgressWidget({
    super.key,
    required this.readCount,
    required this.recipientCount,
  });

  @override
  Widget build(BuildContext context) {
    final ratio = recipientCount == 0 ? 0.0 : readCount / recipientCount;
    return Row(
      children: [
        Expanded(
          child: ClipRRect(
            borderRadius: BorderRadius.circular(999),
            child: LinearProgressIndicator(
              value: ratio.clamp(0.0, 1.0),
              minHeight: 8,
              color: AppColors.success,
              backgroundColor: AppColors.border,
            ),
          ),
        ),
        const SizedBox(width: 10),
        Text(
          '$readCount/$recipientCount đã đọc',
          style: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
            color: AppColors.text,
          ),
        ),
      ],
    );
  }
}
