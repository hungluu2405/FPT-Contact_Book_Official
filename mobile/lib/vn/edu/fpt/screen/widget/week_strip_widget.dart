import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/schedule_week_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

/// Dải 7 ngày T2–CN. Ngày đang chọn là ô cam đặc chữ trắng, hôm nay có chữ
/// cam đậm, ngày có buổi học có chấm nhỏ dưới số. Mũi tên hoặc vuốt ngang
/// để đổi tuần.
class WeekStripWidget extends StatelessWidget {
  final List<ScheduleDayModel> days;
  final String? selectedDate;
  final ValueChanged<String> onSelect;
  final VoidCallback onPrevious;
  final VoidCallback onNext;

  const WeekStripWidget({
    super.key,
    required this.days,
    required this.selectedDate,
    required this.onSelect,
    required this.onPrevious,
    required this.onNext,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onHorizontalDragEnd: (details) {
        final velocity = details.primaryVelocity ?? 0;
        if (velocity > 200) {
          onPrevious();
        } else if (velocity < -200) {
          onNext();
        }
      },
      child: Row(
        children: [
          _Arrow(
            icon: Icons.chevron_left,
            tooltip: 'Tuần trước',
            onPressed: onPrevious,
          ),
          for (final day in days)
            Expanded(
              child: _DayCell(
                day: day,
                selected: day.date == selectedDate,
                onTap: () => onSelect(day.date),
              ),
            ),
          _Arrow(
            icon: Icons.chevron_right,
            tooltip: 'Tuần sau',
            onPressed: onNext,
          ),
        ],
      ),
    );
  }
}

class _Arrow extends StatelessWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback onPressed;

  const _Arrow({
    required this.icon,
    required this.tooltip,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: tooltip,
      visualDensity: VisualDensity.compact,
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(minWidth: 32, minHeight: 48),
      icon: Icon(icon, color: AppColors.textMuted),
      onPressed: onPressed,
    );
  }
}

class _DayCell extends StatelessWidget {
  final ScheduleDayModel day;
  final bool selected;
  final VoidCallback onTap;

  const _DayCell({
    required this.day,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final date = parseApiDate(day.date);
    final Color labelColor;
    final Color numberColor;
    if (selected) {
      labelColor = Colors.white;
      numberColor = Colors.white;
    } else if (day.today) {
      labelColor = AppColors.orangeDark;
      numberColor = AppColors.orangeDark;
    } else {
      labelColor = AppColors.textMuted;
      numberColor = AppColors.text;
    }

    return Semantics(
      selected: selected,
      button: true,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Container(
          margin: const EdgeInsets.symmetric(horizontal: 2),
          padding: const EdgeInsets.symmetric(vertical: 8),
          decoration: BoxDecoration(
            color: selected ? AppColors.orange : Colors.transparent,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            children: [
              Text(
                date == null ? '' : weekdayShort(date),
                style: TextStyle(fontSize: 12, color: labelColor),
              ),
              const SizedBox(height: 2),
              Text(
                date == null ? '' : '${date.day}',
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                  color: numberColor,
                ),
              ),
              const SizedBox(height: 4),
              Container(
                width: 5,
                height: 5,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: day.items.isEmpty
                      ? Colors.transparent
                      : (selected ? Colors.white : AppColors.orange),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
