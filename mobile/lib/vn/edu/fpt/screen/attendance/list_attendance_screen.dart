import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/attendance_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attendance_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/attendance/view_attendance_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/attendance_summary_card_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/empty_state_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/semester_picker_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';

/// Chuyên cần các môn của học kỳ; môn vượt ngưỡng vắng có viền và nhãn đỏ.
/// Bấm một môn để xem từng buổi. [studentId] null là HS xem của mình.
class ListAttendanceScreen extends StatefulWidget {
  final AttendanceController controller;
  final int? studentId;
  final String viewerName;

  const ListAttendanceScreen({
    super.key,
    required this.controller,
    required this.studentId,
    required this.viewerName,
  });

  @override
  State<ListAttendanceScreen> createState() => _ListAttendanceScreenState();
}

class _ListAttendanceScreenState extends State<ListAttendanceScreen> {
  AttendanceController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.open(widget.studentId);
  }

  void _openSection(AttendanceSectionModel row) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ViewAttendanceScreen(
          controller: _controller,
          sectionId: row.sectionId,
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
    final overview = _controller.overview;
    final warnings = _controller.warningCount;
    final subtitle = [
      ?overview?.semester.name,
      if (warnings > 0) '$warnings môn vượt ngưỡng vắng',
    ].join(' · ');
    return PageHeaderWidget(
      icon: Icons.fact_check,
      title: 'Điểm danh',
      headline: overview == null
          ? 'Điểm danh học kỳ'
          : '${overview.sections.length} môn học',
      subtitle: subtitle.isEmpty ? null : subtitle,
      viewerName: widget.viewerName,
      bottom: _controller.semesters.isEmpty
          ? null
          : SemesterPickerWidget(
              semesters: _controller.semesters,
              selectedId: _controller.selectedSemesterId,
              onSelect: _controller.selectSemester,
            ),
    );
  }

  List<Widget> _content() {
    final error = _controller.error;
    if (_controller.hasNoSemester) {
      return const [
        EmptyStateWidget(
          icon: Icons.event_busy,
          message: 'Chưa có học kỳ nào để xem điểm danh.',
        ),
      ];
    }
    if (error != null) {
      return [
        ErrorRetryWidget(message: error.message, onRetry: _controller.reload),
      ];
    }
    final overview = _controller.overview;
    if (overview == null) {
      return const [
        Padding(
          padding: EdgeInsets.all(32),
          child: Center(
            child: CircularProgressIndicator(color: AppColors.orange),
          ),
        ),
      ];
    }
    return [
      if (_controller.isLoading)
        const LinearProgressIndicator(
          color: AppColors.orange,
          backgroundColor: AppColors.orangeLight,
        ),
      if (overview.sections.isEmpty)
        const EmptyStateWidget(
          icon: Icons.fact_check,
          message: 'Học kỳ này chưa có môn học nào.',
        )
      else
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
          child: Column(
            children: [
              for (final row in overview.sections)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: AttendanceSummaryCardWidget(
                    row: row,
                    thresholdPercent: overview.thresholdPercent,
                    onTap: () => _openSection(row),
                  ),
                ),
            ],
          ),
        ),
    ];
  }
}
