import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/attendance_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attendance_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/schedule_item_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/attendance_summary_card_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/empty_state_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

/// Lịch sử điểm danh của một môn: thẻ chuyên cần rồi từng buổi (tải theo
/// trang), mỗi buổi có nhãn trạng thái và ghi chú của giáo viên. Dùng chung
/// [AttendanceController] với màn hình danh sách (cùng người xem).
class ViewAttendanceScreen extends StatefulWidget {
  final AttendanceController controller;
  final int sectionId;
  final String viewerName;

  const ViewAttendanceScreen({
    super.key,
    required this.controller,
    required this.sectionId,
    required this.viewerName,
  });

  @override
  State<ViewAttendanceScreen> createState() => _ViewAttendanceScreenState();
}

class _ViewAttendanceScreenState extends State<ViewAttendanceScreen> {
  AttendanceController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    // Màn hình danh sách bên dưới cũng nghe controller này, nên chỉ tải sau
    // khung hình đầu để không báo thay đổi giữa lúc đang dựng giao diện.
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => _controller.openSection(widget.sectionId),
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
          final section = _controller.section?.sectionId == widget.sectionId
              ? _controller.section
              : null;
          final sessions = section == null
              ? const <AttendanceSessionModel>[]
              : _controller.sessions;
          return NotificationListener<ScrollNotification>(
            onNotification: _onScroll,
            child: RefreshIndicator(
              color: AppColors.orange,
              onRefresh: () => _controller.openSection(widget.sectionId),
              child: ListView.builder(
                padding: EdgeInsets.zero,
                itemCount: sessions.length + 3,
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return PageHeaderWidget(
                      icon: Icons.fact_check,
                      title: 'Điểm danh',
                      headline: section?.subjectName ?? 'Điểm danh môn học',
                      subtitle: section?.sectionCode,
                      viewerName: widget.viewerName,
                    );
                  }
                  if (index == 1) {
                    if (section == null) {
                      return const SizedBox.shrink();
                    }
                    return Padding(
                      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                      child: AttendanceSummaryCardWidget(
                        row: section,
                        thresholdPercent:
                            _controller.overview?.thresholdPercent ?? 0,
                      ),
                    );
                  }
                  if (index == sessions.length + 2) {
                    return _footer(section == null);
                  }
                  return Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
                    child: _SessionRow(session: sessions[index - 2]),
                  );
                },
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _footer(bool waiting) {
    final error = _controller.sessionsError;
    if (error != null) {
      return ErrorRetryWidget(
        message: error.message,
        onRetry: _controller.loadMoreSessions,
      );
    }
    if (waiting || _controller.isLoadingSessions) {
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
        message: 'Môn này chưa có buổi học nào.',
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

/// Một buổi: số buổi, thứ ngày, giờ, phòng, nhãn trạng thái, ghi chú.
class _SessionRow extends StatelessWidget {
  final AttendanceSessionModel session;

  const _SessionRow({required this.session});

  @override
  Widget build(BuildContext context) {
    final date = parseApiDate(session.date);
    final cancelled = session.sessionStatus == SessionStatus.cancelled;
    final note = session.note ?? '';
    final room = session.roomCode ?? '';
    return Opacity(
      opacity: cancelled ? 0.55 : 1,
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: AppColors.border),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 52,
              child: Column(
                children: [
                  Text(
                    'Buổi',
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.textMuted,
                    ),
                  ),
                  Text(
                    '${session.sessionNo}',
                    style: const TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.w800,
                      color: AppColors.navy,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    date == null
                        ? session.date
                        : '${weekdayLong(date)}, ${formatApiDate(session.date)}',
                    style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: AppColors.text,
                      decoration: cancelled ? TextDecoration.lineThrough : null,
                    ),
                  ),
                  Text(
                    [
                      '${formatApiTime(session.startTime)} – '
                          '${formatApiTime(session.endTime)}',
                      if (room.isNotEmpty) 'Phòng $room',
                    ].join(' · '),
                    style: const TextStyle(
                      fontSize: 13,
                      color: AppColors.textMuted,
                    ),
                  ),
                  const SizedBox(height: 6),
                  _statusBadge(cancelled),
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
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _statusBadge(bool cancelled) {
    if (cancelled) {
      return const StatusBadgeWidget(
        label: 'Buổi đã hủy',
        tone: BadgeTone.muted,
      );
    }
    return switch (session.status) {
      AttendanceStatus.present => const StatusBadgeWidget(
        label: 'Có mặt',
        tone: BadgeTone.success,
      ),
      AttendanceStatus.absentExcused => const StatusBadgeWidget(
        label: 'Vắng có phép',
        tone: BadgeTone.orange,
      ),
      AttendanceStatus.absentUnexcused => const StatusBadgeWidget(
        label: 'Vắng không phép',
        tone: BadgeTone.danger,
      ),
      _ => const StatusBadgeWidget(
        label: 'Chưa điểm danh',
        tone: BadgeTone.muted,
      ),
    };
  }
}
