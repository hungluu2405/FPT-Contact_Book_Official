import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/attendance_sheet_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/teacher_section_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/teacher_section_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/teacher/edit_attendance_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_theme.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/number_format.dart';

/// Chi tiết một lớp giáo viên dạy: hai phần "Buổi học" (bấm để điểm danh)
/// và "Học sinh" (chuyên cần, cảnh báo vượt ngưỡng vắng). Nơi mở màn hình
/// đã gọi [TeacherSectionController.openSection].
class ViewSectionScreen extends StatefulWidget {
  final TeacherSectionController controller;
  final AttendanceSheetController sheetController;
  final int sectionId;
  final String viewerName;

  const ViewSectionScreen({
    super.key,
    required this.controller,
    required this.sheetController,
    required this.sectionId,
    required this.viewerName,
  });

  @override
  State<ViewSectionScreen> createState() => _ViewSectionScreenState();
}

class _ViewSectionScreenState extends State<ViewSectionScreen> {
  bool _showStudents = false;

  TeacherSectionController get _controller => widget.controller;

  Future<void> _openSheet(SectionSessionModel session) async {
    widget.sheetController.open(widget.sectionId, session.id);
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => EditAttendanceScreen(
          controller: widget.sheetController,
          viewerName: widget.viewerName,
        ),
      ),
    );
    // Cập nhật "Đã điểm danh" và chuyên cần sau khi lưu.
    await _controller.reloadSection();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          final section = _controller.section;
          return RefreshIndicator(
            color: AppColors.orange,
            onRefresh: _controller.reloadSection,
            child: ListView(
              padding: EdgeInsets.zero,
              children: [
                PageHeaderWidget(
                  icon: Icons.class_outlined,
                  title: 'Lớp của tôi',
                  headline: section == null
                      ? 'Lớp môn học'
                      : (section.subjectName.isEmpty
                            ? section.subjectCode
                            : section.subjectName),
                  subtitle: section?.code,
                  viewerName: widget.viewerName,
                ),
                ..._content(section),
              ],
            ),
          );
        },
      ),
    );
  }

  List<Widget> _content(TeacherSectionModel? section) {
    final error = _controller.sectionError;
    if (error != null) {
      return [
        ErrorRetryWidget(
          message: error.message,
          onRetry: _controller.reloadSection,
        ),
      ];
    }
    if (section == null) {
      return const [_Spinner()];
    }
    return [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
        child: Wrap(
          spacing: 8,
          children: [
            _tab('Buổi học (${_controller.totalSessions})', false),
            _tab('Học sinh', true),
          ],
        ),
      ),
      if (_showStudents) ..._students() else ..._sessions(),
      const SizedBox(height: 24),
    ];
  }

  Widget _tab(String label, bool students) {
    final selected = _showStudents == students;
    return ChoiceChip(
      label: Text(label),
      selected: selected,
      onSelected: (_) => setState(() => _showStudents = students),
      selectedColor: AppColors.orange,
      backgroundColor: Colors.white,
      showCheckmark: false,
      labelStyle: TextStyle(
        fontFamily: kAppFontFamily,
        fontSize: 15,
        fontWeight: FontWeight.w600,
        color: selected ? Colors.white : AppColors.text,
      ),
    );
  }

  List<Widget> _sessions() {
    final sessions = _controller.sessions;
    if (sessions.isEmpty) {
      return const [_Note('Lớp chưa có buổi học nào.')];
    }
    return [
      for (final session in sessions)
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
          child: _SessionTile(
            session: session,
            onTap: session.isCancelled ? null : () => _openSheet(session),
          ),
        ),
      if (_controller.sessionsHasMore)
        _MoreButton(
          loading: _controller.isLoadingMore,
          onPressed: _controller.loadMoreSessions,
        ),
    ];
  }

  List<Widget> _students() {
    final students = _controller.students;
    if (students.isEmpty) {
      return const [_Note('Lớp chưa có học sinh đang học.')];
    }
    return [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
        child: Text(
          'Ngưỡng cảnh báo: vắng quá ${_controller.thresholdPercent}% số buổi.',
          style: const TextStyle(fontSize: 14, color: AppColors.textMuted),
        ),
      ),
      for (final student in students)
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
          child: _StudentTile(student: student),
        ),
      if (_controller.studentsHasMore)
        _MoreButton(
          loading: _controller.isLoadingMore,
          onPressed: _controller.loadMoreStudents,
        ),
    ];
  }
}

class _Spinner extends StatelessWidget {
  const _Spinner();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.all(24),
      child: Center(child: CircularProgressIndicator(color: AppColors.orange)),
    );
  }
}

class _Note extends StatelessWidget {
  final String text;

  const _Note(this.text);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Text(
        text,
        style: const TextStyle(fontSize: 15, color: AppColors.textMuted),
      ),
    );
  }
}

class _MoreButton extends StatelessWidget {
  final bool loading;
  final VoidCallback onPressed;

  const _MoreButton({required this.loading, required this.onPressed});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: loading
          ? const Padding(
              padding: EdgeInsets.all(12),
              child: CircularProgressIndicator(color: AppColors.orange),
            )
          : TextButton(onPressed: onPressed, child: const Text('Tải thêm')),
    );
  }
}

/// Một buổi học: số buổi, ngày, giờ, phòng và nhãn đã/chưa điểm danh.
class _SessionTile extends StatelessWidget {
  final SectionSessionModel session;
  final VoidCallback? onTap;

  const _SessionTile({required this.session, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final date = parseApiDate(session.date);
    final badge = session.isCancelled
        ? const StatusBadgeWidget(label: 'Đã hủy', tone: BadgeTone.muted)
        : session.hasAttendance
        ? const StatusBadgeWidget(
            label: 'Đã điểm danh',
            tone: BadgeTone.success,
          )
        : const StatusBadgeWidget(label: 'Chưa điểm danh');
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: AppColors.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 12, 8, 12),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          'Buổi ${session.sessionNo}',
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w700,
                            color: AppColors.navy,
                          ),
                        ),
                        const SizedBox(width: 8),
                        badge,
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      [
                        if (date != null) weekdayLong(date),
                        formatApiDate(session.date),
                        '${formatApiTime(session.startTime)}–'
                            '${formatApiTime(session.endTime)}',
                        if ((session.roomCode ?? '').isNotEmpty)
                          session.roomCode!,
                      ].join(' · '),
                      style: const TextStyle(
                        fontSize: 14,
                        color: AppColors.textMuted,
                      ),
                    ),
                  ],
                ),
              ),
              if (onTap != null)
                const Icon(Icons.chevron_right, color: AppColors.textMuted),
            ],
          ),
        ),
      ),
    );
  }
}

/// Chuyên cần một học sinh: số buổi theo trạng thái, tỷ lệ vắng; viền đỏ khi
/// vượt ngưỡng.
class _StudentTile extends StatelessWidget {
  final SectionStudentModel student;

  const _StudentTile({required this.student});

  @override
  Widget build(BuildContext context) {
    final summary = student.summary;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: summary.warning ? AppColors.error : AppColors.border,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  student.fullName,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: AppColors.navy,
                  ),
                ),
              ),
              if (summary.warning)
                const StatusBadgeWidget(
                  label: 'Vượt ngưỡng vắng',
                  tone: BadgeTone.danger,
                ),
            ],
          ),
          Text(
            student.studentCode,
            style: const TextStyle(fontSize: 14, color: AppColors.textMuted),
          ),
          const SizedBox(height: 6),
          Text(
            'Có mặt ${summary.present} · Có phép ${summary.absentExcused} · '
            'Không phép ${summary.absentUnexcused} · '
            'Vắng ${formatPercent(summary.absentRate)}',
            style: const TextStyle(fontSize: 14, color: AppColors.text),
          ),
        ],
      ),
    );
  }
}
