import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/schedule_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/schedule_week_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/schedule/list_session_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/empty_state_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/session_card_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/timeline_tile_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/week_strip_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

/// Lịch học theo tuần: dải 7 ngày và timeline các buổi của ngày đang chọn.
/// [studentId] null là SV xem của mình; [viewerName] là tên người đang được
/// xem để hiện chữ cái đầu ở phần đầu.
class ViewScheduleScreen extends StatefulWidget {
  final ScheduleController controller;
  final int? studentId;
  final String viewerName;

  /// GV xem lịch dạy của mình (đường dẫn `/teacher`).
  final bool teacher;

  const ViewScheduleScreen({
    super.key,
    required this.controller,
    required this.studentId,
    required this.viewerName,
    this.teacher = false,
  });

  @override
  State<ViewScheduleScreen> createState() => _ViewScheduleScreenState();
}

class _ViewScheduleScreenState extends State<ViewScheduleScreen> {
  ScheduleController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.open(widget.studentId, teacher: widget.teacher);
  }

  void _openSessions() {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ListSessionScreen(
          controller: _controller,
          viewerName: widget.viewerName,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) => RefreshIndicator(
          color: AppColors.orange,
          onRefresh: _controller.reload,
          child: ListView(
            padding: EdgeInsets.zero,
            children: [_header(), ..._content()],
          ),
        ),
      ),
    );
  }

  Widget _header() {
    final week = _controller.week;
    final selected = parseApiDate(_controller.selectedDate);
    final day = _controller.selectedDay;
    final semesterName = _controller.semester?.name ?? '';
    String headline = _label;
    String? subtitle = semesterName.isEmpty ? null : semesterName;
    if (selected != null) {
      if (day?.today ?? false) {
        headline = 'Hôm nay, ${dayMonthText(selected)}';
        subtitle = [
          weekdayLong(selected),
          if (semesterName.isNotEmpty) semesterName,
        ].join(' · ');
      } else {
        headline = '${weekdayLong(selected)}, ${dayMonthText(selected)}';
      }
    }
    return PageHeaderWidget(
      icon: Icons.menu_book_outlined,
      title: _label,
      headline: headline,
      subtitle: subtitle,
      viewerName: widget.viewerName,
      bottom: week == null
          ? null
          : WeekStripWidget(
              days: week.days,
              selectedDate: _controller.selectedDate,
              onSelect: _controller.selectDate,
              onPrevious: _controller.previousWeek,
              onNext: _controller.nextWeek,
            ),
    );
  }

  List<Widget> _content() {
    final week = _controller.week;
    final error = _controller.error;
    if (_controller.hasNoSemester) {
      return const [
        EmptyStateWidget(
          icon: Icons.event_busy,
          message: 'Chưa có học kỳ nào để xem lịch học.',
        ),
      ];
    }
    if (week == null) {
      if (error != null) {
        return [
          ErrorRetryWidget(message: error.message, onRetry: _controller.reload),
        ];
      }
      return const [
        Padding(
          padding: EdgeInsets.all(32),
          child: Center(
            child: CircularProgressIndicator(color: AppColors.orange),
          ),
        ),
      ];
    }
    final day = _controller.selectedDay;
    return [
      if (_controller.isLoading)
        const LinearProgressIndicator(
          color: AppColors.orange,
          backgroundColor: AppColors.orangeLight,
        ),
      if (error != null)
        ErrorRetryWidget(message: error.message, onRetry: _controller.reload),
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 8, 8),
        child: Row(
          children: [
            Expanded(
              child: Text(
                _sectionTitle(day),
                style: const TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w700,
                  color: AppColors.navy,
                ),
              ),
            ),
            if (!_controller.isCurrentWeek)
              TextButton(
                onPressed: _controller.goToday,
                child: const Text('Hôm nay'),
              ),
            TextButton(
              onPressed: _openSessions,
              child: const Row(
                mainAxisSize: MainAxisSize.min,
                children: [Text('Xem lịch'), Icon(Icons.chevron_right)],
              ),
            ),
          ],
        ),
      ),
      if (day == null || day.items.isEmpty)
        const EmptyStateWidget(
          icon: Icons.event_available,
          message: 'Không có lịch học trong ngày này.',
        )
      else
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
          child: Column(
            children: [
              for (var i = 0; i < day.items.length; i++) _tile(day, i),
            ],
          ),
        ),
    ];
  }

  Widget _tile(ScheduleDayModel day, int index) {
    final item = day.items[index];
    final ongoing = _controller.isOngoing(day, item);
    return TimelineTileWidget(
      startText: formatApiTime(item.startTime),
      endText: formatApiTime(item.endTime),
      highlighted: ongoing,
      dimmed: item.isCancelled,
      isFirst: index == 0,
      isLast: index == day.items.length - 1,
      child: SessionCardWidget(
        item: item,
        ongoing: ongoing,
        showSection: widget.teacher,
      ),
    );
  }

  /// "Lịch dạy" của GV hoặc "Lịch học" của HS.
  String get _label => widget.teacher ? 'Lịch dạy' : 'Lịch học';

  String _sectionTitle(ScheduleDayModel? day) {
    if (day == null) {
      return _label;
    }
    if (day.today) {
      return '$_label hôm nay';
    }
    final date = parseApiDate(day.date);
    return date == null
        ? _label
        : '$_label ${weekdayLong(date).toLowerCase()}, ${dayMonthShort(date)}';
  }
}
