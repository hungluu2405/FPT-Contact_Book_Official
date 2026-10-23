import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';

/// Một dòng của timeline: cột giờ (giờ bắt đầu đậm, giờ kết thúc mờ), đường
/// dọc có chấm và thẻ trắng bo góc. [highlighted] tô cam chấm, giờ và viền
/// thẻ (buổi đang diễn ra, lịch thi sắp tới); [dimmed] làm mờ cả dòng.
class TimelineTileWidget extends StatelessWidget {
  final String startText;
  final String endText;
  final bool highlighted;
  final bool dimmed;
  final bool isFirst;
  final bool isLast;
  final Widget child;

  const TimelineTileWidget({
    super.key,
    required this.startText,
    required this.endText,
    required this.child,
    this.highlighted = false,
    this.dimmed = false,
    this.isFirst = false,
    this.isLast = false,
  });

  @override
  Widget build(BuildContext context) {
    final accent = highlighted ? AppColors.orange : AppColors.border;
    return Opacity(
      opacity: dimmed ? 0.55 : 1,
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SizedBox(
              width: 52,
              child: Padding(
                padding: const EdgeInsets.only(top: 14),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      startText,
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: highlighted
                            ? AppColors.orangeDark
                            : AppColors.navy,
                      ),
                    ),
                    Text(
                      endText,
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppColors.textMuted,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            SizedBox(
              width: 20,
              child: Column(
                children: [
                  Container(
                    width: 2,
                    height: 18,
                    color: isFirst ? Colors.transparent : AppColors.border,
                  ),
                  Container(
                    width: 10,
                    height: 10,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: highlighted ? AppColors.orange : Colors.white,
                      border: Border.all(color: accent, width: 2),
                    ),
                  ),
                  Expanded(
                    child: Container(
                      width: 2,
                      color: isLast ? Colors.transparent : AppColors.border,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: accent,
                      width: highlighted ? 1.5 : 1,
                    ),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x0F000000),
                        blurRadius: 8,
                        offset: Offset(0, 2),
                      ),
                    ],
                  ),
                  child: child,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
