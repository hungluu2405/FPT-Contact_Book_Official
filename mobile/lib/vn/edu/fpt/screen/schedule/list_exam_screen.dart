import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/exam_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/exam_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/empty_state_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

/// Chữ của `examType` (docs/API.md mục "Giá trị enum").
const Map<String, String> kExamTypeLabels = {
  ExamType.midterm: 'Giữa kỳ',
  ExamType.finalExam: 'Cuối kỳ',
  ExamType.retake: 'Thi lại',
};

/// Lịch thi của học kỳ: lịch gần nhất sắp tới có viền cam, lịch đã qua
/// hiện mờ; cuộn tới cuối thì tải thêm.
class ListExamScreen extends StatefulWidget {
  final ExamController controller;
  final int? studentId;
  final String viewerName;

  const ListExamScreen({
    super.key,
    required this.controller,
    required this.studentId,
    required this.viewerName,
  });

  @override
  State<ListExamScreen> createState() => _ListExamScreenState();
}

class _ListExamScreenState extends State<ListExamScreen> {
  ExamController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.open(widget.studentId);
  }

  bool _onScroll(ScrollNotification notification) {
    if (notification.metrics.extentAfter < 300) {
      _controller.loadMore();
    }
    return false;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          final exams = _controller.exams;
          final next = _controller.nextExam;
          return NotificationListener<ScrollNotification>(
            onNotification: _onScroll,
            child: RefreshIndicator(
              color: AppColors.orange,
              onRefresh: _controller.reload,
              child: ListView.builder(
                padding: EdgeInsets.zero,
                itemCount: exams.length + 2,
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return _header();
                  }
                  if (index == exams.length + 1) {
                    return _footer();
                  }
                  final exam = exams[index - 1];
                  return Padding(
                    padding: EdgeInsets.fromLTRB(
                      16,
                      index == 1 ? 16 : 0,
                      16,
                      12,
                    ),
                    child: _ExamCard(
                      exam: exam,
                      upcoming: identical(exam, next),
                      past: _controller.isPast(exam),
                    ),
                  );
                },
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _header() {
    final total = _controller.total;
    final String headline;
    if (!_controller.hasLoaded) {
      headline = 'Lịch thi học kỳ';
    } else if (total == 0) {
      headline = 'Chưa có lịch thi';
    } else {
      headline = '$total lịch thi trong học kỳ';
    }
    return PageHeaderWidget(
      icon: Icons.event_note,
      title: 'Lịch thi',
      headline: headline,
      subtitle: _controller.semester?.name,
      viewerName: widget.viewerName,
    );
  }

  Widget _footer() {
    final error = _controller.error;
    if (_controller.hasNoSemester) {
      return const EmptyStateWidget(
        icon: Icons.event_busy,
        message: 'Chưa có học kỳ nào để xem lịch thi.',
      );
    }
    if (error != null) {
      return ErrorRetryWidget(
        message: error.message,
        onRetry: _controller.hasLoaded
            ? _controller.loadMore
            : _controller.reload,
      );
    }
    if (_controller.isLoading) {
      return const Padding(
        padding: EdgeInsets.all(24),
        child: Center(
          child: CircularProgressIndicator(color: AppColors.orange),
        ),
      );
    }
    if (_controller.exams.isEmpty) {
      return const EmptyStateWidget(
        icon: Icons.event_available,
        message: 'Học kỳ này chưa có lịch thi.',
      );
    }
    if (_controller.hasMore) {
      // Dự phòng khi trang đầu chưa đủ dài để cuộn.
      return Center(
        child: TextButton(
          onPressed: _controller.loadMore,
          child: const Text('Tải thêm'),
        ),
      );
    }
    return const SizedBox(height: 24);
  }
}

class _ExamCard extends StatelessWidget {
  final ExamModel exam;
  final bool upcoming;
  final bool past;

  const _ExamCard({
    required this.exam,
    required this.upcoming,
    required this.past,
  });

  @override
  Widget build(BuildContext context) {
    final date = parseApiDate(exam.date);
    final room = exam.roomCode ?? '';
    final note = exam.note ?? '';
    return Opacity(
      opacity: past ? 0.55 : 1,
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: upcoming ? AppColors.orange : AppColors.border,
            width: upcoming ? 1.5 : 1,
          ),
          boxShadow: const [
            BoxShadow(
              color: Color(0x0F000000),
              blurRadius: 8,
              offset: Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 56,
              padding: const EdgeInsets.symmetric(vertical: 8),
              decoration: BoxDecoration(
                color: upcoming ? AppColors.orange : AppColors.orangeLight,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Column(
                children: [
                  Text(
                    date == null ? '' : '${date.day}',
                    style: TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w800,
                      color: upcoming ? Colors.white : AppColors.navy,
                    ),
                  ),
                  Text(
                    date == null ? '' : monthShort(date),
                    style: TextStyle(
                      fontSize: 13,
                      color: upcoming ? Colors.white : AppColors.orangeDark,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Wrap(
                    spacing: 6,
                    runSpacing: 4,
                    children: [
                      StatusBadgeWidget(
                        label: kExamTypeLabels[exam.examType] ?? exam.examType,
                        tone: exam.examType == ExamType.retake
                            ? BadgeTone.info
                            : BadgeTone.orange,
                      ),
                      if (upcoming)
                        const StatusBadgeWidget(label: 'Sắp tới', dot: true),
                      if (past)
                        const StatusBadgeWidget(
                          label: 'Đã thi',
                          tone: BadgeTone.muted,
                        ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    exam.subjectCode,
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w800,
                      color: AppColors.navy,
                    ),
                  ),
                  if (exam.subjectName.isNotEmpty)
                    Text(
                      exam.subjectName,
                      style: const TextStyle(
                        fontSize: 14,
                        color: AppColors.text,
                      ),
                    ),
                  const SizedBox(height: 6),
                  Row(
                    children: [
                      const Icon(
                        Icons.schedule,
                        size: 16,
                        color: AppColors.textMuted,
                      ),
                      const SizedBox(width: 4),
                      Expanded(
                        child: Text(
                          '${date == null ? '' : '${weekdayLong(date)}, '}'
                          '${formatApiTime(exam.startTime)} – '
                          '${formatApiTime(exam.endTime)}',
                          style: const TextStyle(
                            fontSize: 13,
                            color: AppColors.textMuted,
                          ),
                        ),
                      ),
                      if (room.isNotEmpty) ...[
                        const Icon(
                          Icons.location_on_outlined,
                          size: 16,
                          color: AppColors.textMuted,
                        ),
                        const SizedBox(width: 2),
                        Text(
                          room,
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
              ),
            ),
          ],
        ),
      ),
    );
  }
}
