import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attendance_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/number_format.dart';

/// Thẻ chuyên cần của một môn: thanh tỷ lệ có mặt, ba số đếm, số buổi đã
/// điểm danh; vượt ngưỡng vắng thì viền và nhãn đỏ. Dùng lại ở phần đầu
/// màn hình chi tiết ([onTap] null).
class AttendanceSummaryCardWidget extends StatelessWidget {
  final AttendanceSectionModel row;
  final int thresholdPercent;
  final VoidCallback? onTap;

  const AttendanceSummaryCardWidget({
    super.key,
    required this.row,
    required this.thresholdPercent,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final summary = row.summary;
    final rate = summary.attendanceRate;
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(
          color: summary.warning ? AppColors.error : AppColors.border,
          width: summary.warning ? 1.5 : 1,
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (summary.warning) ...[
                StatusBadgeWidget(
                  label: 'Vượt ngưỡng vắng $thresholdPercent%',
                  tone: BadgeTone.danger,
                  dot: true,
                ),
                const SizedBox(height: 6),
              ],
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          row.subjectCode,
                          style: const TextStyle(
                            fontSize: 17,
                            fontWeight: FontWeight.w800,
                            color: AppColors.navy,
                          ),
                        ),
                        Text(
                          row.subjectName,
                          style: const TextStyle(
                            fontSize: 14,
                            color: AppColors.text,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Text(
                    rate == null ? 'Chưa điểm danh' : formatPercent(rate),
                    style: TextStyle(
                      fontSize: rate == null ? 14 : 20,
                      fontWeight: FontWeight.w800,
                      color: rate == null
                          ? AppColors.textMuted
                          : AppColors.orangeDark,
                    ),
                  ),
                  if (onTap != null)
                    const Icon(Icons.chevron_right, color: AppColors.textMuted),
                ],
              ),
              const SizedBox(height: 10),
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(
                  value: (rate ?? 0) / 100,
                  minHeight: 8,
                  color: summary.warning ? AppColors.error : AppColors.orange,
                  backgroundColor: const Color(0xFFF3F4F6),
                ),
              ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 6,
                runSpacing: 4,
                children: [
                  StatusBadgeWidget(
                    label: 'Có mặt ${summary.present}',
                    tone: BadgeTone.success,
                  ),
                  StatusBadgeWidget(
                    label: 'Vắng có phép ${summary.absentExcused}',
                    tone: BadgeTone.orange,
                  ),
                  StatusBadgeWidget(
                    label: 'Vắng không phép ${summary.absentUnexcused}',
                    tone: BadgeTone.danger,
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                'Đã điểm danh ${summary.marked}/${summary.totalSessions} buổi',
                style: const TextStyle(
                  fontSize: 13,
                  color: AppColors.textMuted,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
