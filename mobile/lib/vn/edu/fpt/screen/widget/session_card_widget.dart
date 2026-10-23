import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/schedule_item_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';

/// Nội dung thẻ của một buổi học: nhãn trạng thái, mã môn (gạch ngang khi
/// đã hủy), tên môn, giảng viên, phòng và ghi chú.
class SessionCardWidget extends StatelessWidget {
  final ScheduleItemModel item;
  final bool ongoing;

  const SessionCardWidget({
    super.key,
    required this.item,
    this.ongoing = false,
  });

  @override
  Widget build(BuildContext context) {
    final badges = <Widget>[
      if (ongoing) const StatusBadgeWidget(label: 'Đang diễn ra', dot: true),
      if (item.isCancelled)
        const StatusBadgeWidget(label: 'Đã hủy', tone: BadgeTone.muted),
      if (item.status == SessionStatus.makeup)
        const StatusBadgeWidget(label: 'Học bù', tone: BadgeTone.info),
    ];
    final note = item.note ?? '';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (badges.isNotEmpty) ...[
          Wrap(spacing: 6, runSpacing: 4, children: badges),
          const SizedBox(height: 6),
        ],
        Text(
          item.subjectCode,
          style: TextStyle(
            fontSize: 17,
            fontWeight: FontWeight.w800,
            color: AppColors.navy,
            decoration: item.isCancelled ? TextDecoration.lineThrough : null,
          ),
        ),
        if (item.subjectName.isNotEmpty)
          Text(
            item.subjectName,
            style: const TextStyle(fontSize: 14, color: AppColors.text),
          ),
        const SizedBox(height: 6),
        Row(
          children: [
            Expanded(
              child: Text(
                item.teacherName ?? '',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 13,
                  color: AppColors.textMuted,
                ),
              ),
            ),
            if ((item.roomCode ?? '').isNotEmpty) ...[
              const Icon(
                Icons.location_on_outlined,
                size: 16,
                color: AppColors.textMuted,
              ),
              const SizedBox(width: 2),
              Text(
                item.roomCode!,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: AppColors.text,
                ),
              ),
            ],
          ],
        ),
        if (note.isNotEmpty) ...[
          const SizedBox(height: 6),
          Text(
            note,
            style: const TextStyle(
              fontSize: 13,
              fontStyle: FontStyle.italic,
              color: AppColors.textMuted,
            ),
          ),
        ],
      ],
    );
  }
}
