import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/schedule_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/schedule_item_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/empty_state_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/session_card_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/timeline_tile_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

/// Mọi buổi học của học kỳ, nhóm theo ngày, cuộn tới cuối thì tải thêm.
/// Dùng chung [ScheduleController] với màn hình lịch tuần (cùng người xem).
class ListSessionScreen extends StatefulWidget {
  final ScheduleController controller;
  final String viewerName;

  const ListSessionScreen({
    super.key,
    required this.controller,
    required this.viewerName,
  });

  @override
  State<ListSessionScreen> createState() => _ListSessionScreenState();
}

class _ListSessionScreenState extends State<ListSessionScreen> {
  ScheduleController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    // Màn hình lịch tuần bên dưới cũng nghe controller này, nên chỉ tải sau
    // khung hình đầu để không báo thay đổi giữa lúc đang dựng giao diện.
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => _controller.reloadSessions(),
    );
  }

  bool _onScroll(ScrollNotification notification) {
    if (notification.metrics.extentAfter < 300) {
      _controller.loadMoreSessions();
    }
    return false;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          final rows = _rows();
          return NotificationListener<ScrollNotification>(
            onNotification: _onScroll,
            child: RefreshIndicator(
              color: AppColors.orange,
              onRefresh: _controller.reloadSessions,
              child: ListView.builder(
                padding: EdgeInsets.zero,
                itemCount: rows.length + 2,
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return PageHeaderWidget(
                      icon: Icons.calendar_month,
                      title: 'Lịch học',
                      headline: 'Cả học kỳ',
                      subtitle: _controller.semester?.name,
                      viewerName: widget.viewerName,
                    );
                  }
                  if (index == rows.length + 1) {
                    return _footer();
                  }
                  return rows[index - 1];
                },
              ),
            ),
          );
        },
      ),
    );
  }

  /// Tiêu đề ngày rồi các buổi của ngày đó, theo thứ tự API trả về.
  List<Widget> _rows() {
    final sessions = _controller.sessions;
    final rows = <Widget>[];
    for (var i = 0; i < sessions.length; i++) {
      final item = sessions[i];
      final firstOfDay = i == 0 || sessions[i - 1].date != item.date;
      final lastOfDay =
          i == sessions.length - 1 || sessions[i + 1].date != item.date;
      if (firstOfDay) {
        rows.add(_DateHeader(date: item.date));
      }
      rows.add(
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: _tile(item, firstOfDay, lastOfDay),
        ),
      );
    }
    return rows;
  }

  Widget _tile(ScheduleItemModel item, bool first, bool last) {
    return TimelineTileWidget(
      startText: formatApiTime(item.startTime),
      endText: formatApiTime(item.endTime),
      dimmed: item.isCancelled,
      isFirst: first,
      isLast: last,
      child: SessionCardWidget(item: item),
    );
  }

  Widget _footer() {
    final error = _controller.sessionsError;
    if (error != null) {
      if (_controller.hasNoSemesterForSessions) {
        return const EmptyStateWidget(
          icon: Icons.event_busy,
          message: 'Chưa có học kỳ nào để xem lịch học.',
        );
      }
      return ErrorRetryWidget(
        message: error.message,
        onRetry: _controller.loadMoreSessions,
      );
    }
    // Chưa có trang nào và chưa báo hết: đang chờ tải trang đầu.
    if (_controller.isLoadingSessions ||
        (_controller.sessions.isEmpty && _controller.sessionsHasMore)) {
      return const Padding(
        padding: EdgeInsets.all(24),
        child: Center(
          child: CircularProgressIndicator(color: AppColors.orange),
        ),
      );
    }
    if (_controller.sessions.isEmpty) {
      return const EmptyStateWidget(
        icon: Icons.event_available,
        message: 'Học kỳ này chưa có buổi học nào.',
      );
    }
    if (_controller.sessionsHasMore) {
      // Dự phòng khi trang đầu chưa đủ dài để cuộn.
      return Center(
        child: TextButton(
          onPressed: _controller.loadMoreSessions,
          child: const Text('Tải thêm'),
        ),
      );
    }
    return const SizedBox(height: 24);
  }
}

class _DateHeader extends StatelessWidget {
  final String date;

  const _DateHeader({required this.date});

  @override
  Widget build(BuildContext context) {
    final parsed = parseApiDate(date);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Text(
        parsed == null
            ? date
            : '${weekdayLong(parsed)}, ${formatApiDate(date)}',
        style: const TextStyle(
          fontSize: 16,
          fontWeight: FontWeight.w700,
          color: AppColors.navy,
        ),
      ),
    );
  }
}
