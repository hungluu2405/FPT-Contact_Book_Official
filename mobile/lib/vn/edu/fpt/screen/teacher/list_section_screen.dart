import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/attendance_sheet_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/teacher_section_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/teacher_section_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/teacher/view_section_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/empty_state_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/semester_picker_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';

/// "Lớp của tôi": lớp môn học giáo viên dạy trong học kỳ đang chọn (ô chọn
/// học kỳ, cuộn để tải thêm); bấm để xem buổi học và điểm danh.
class ListSectionScreen extends StatefulWidget {
  final TeacherSectionController controller;
  final AttendanceSheetController sheetController;
  final String viewerName;

  const ListSectionScreen({
    super.key,
    required this.controller,
    required this.sheetController,
    required this.viewerName,
  });

  @override
  State<ListSectionScreen> createState() => _ListSectionScreenState();
}

class _ListSectionScreenState extends State<ListSectionScreen> {
  TeacherSectionController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.open();
  }

  void _openSection(int sectionId) {
    _controller.openSection(sectionId);
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ViewSectionScreen(
          controller: _controller,
          sheetController: widget.sheetController,
          sectionId: sectionId,
          viewerName: widget.viewerName,
        ),
      ),
    );
  }

  bool _onScroll(ScrollNotification notification) {
    if (notification.metrics.extentAfter < 300) {
      _controller.loadMoreSections();
    }
    return false;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          final sections = _controller.sections;
          return NotificationListener<ScrollNotification>(
            onNotification: _onScroll,
            child: RefreshIndicator(
              color: AppColors.orange,
              onRefresh: _controller.reloadSections,
              child: ListView.builder(
                padding: EdgeInsets.zero,
                itemCount: sections.length + 2,
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 16),
                      child: _header(),
                    );
                  }
                  if (index == sections.length + 1) {
                    return _footer();
                  }
                  final section = sections[index - 1];
                  return Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                    child: _SectionCard(
                      section: section,
                      onTap: () => _openSection(section.id),
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
    final semester = _controller.semester;
    return PageHeaderWidget(
      icon: Icons.class_outlined,
      title: 'Lớp của tôi',
      headline: 'Lớp đang dạy',
      subtitle: semester?.name,
      viewerName: widget.viewerName,
      bottom: _controller.semesters.isEmpty
          ? null
          : SemesterPickerWidget(
              semesters: _controller.semesters,
              selectedId: semester?.id,
              onSelect: _controller.selectSemester,
            ),
    );
  }

  Widget _footer() {
    final error = _controller.sectionsError;
    if (error != null) {
      return ErrorRetryWidget(
        message: error.message,
        onRetry: _controller.loadMoreSections,
      );
    }
    if (_controller.isLoadingSections ||
        (_controller.sections.isEmpty && _controller.sectionsHasMore)) {
      return const Padding(
        padding: EdgeInsets.all(24),
        child: Center(
          child: CircularProgressIndicator(color: AppColors.orange),
        ),
      );
    }
    if (_controller.sections.isEmpty) {
      return const EmptyStateWidget(
        icon: Icons.class_outlined,
        message: 'Học kỳ này bạn không dạy lớp nào.',
      );
    }
    if (_controller.sectionsHasMore) {
      // Dự phòng khi trang đầu chưa đủ dài để cuộn.
      return Center(
        child: TextButton(
          onPressed: _controller.loadMoreSections,
          child: const Text('Tải thêm'),
        ),
      );
    }
    return const SizedBox(height: 24);
  }
}

class _SectionCard extends StatelessWidget {
  final TeacherSectionModel section;
  final VoidCallback onTap;

  const _SectionCard({required this.section, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final badge = switch (section.status) {
      SectionStatus.closed => const StatusBadgeWidget(
        label: 'Đã đóng',
        tone: BadgeTone.muted,
      ),
      SectionStatus.cancelled => const StatusBadgeWidget(
        label: 'Đã hủy',
        tone: BadgeTone.danger,
      ),
      _ => null,
    };
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
          padding: const EdgeInsets.all(14),
          child: Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: const BoxDecoration(
                  color: AppColors.orangeLight,
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.class_outlined, color: AppColors.orange),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (badge != null) ...[badge, const SizedBox(height: 4)],
                    Text(
                      section.subjectName.isEmpty
                          ? section.subjectCode
                          : section.subjectName,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: AppColors.navy,
                      ),
                    ),
                    Text(
                      section.code,
                      style: const TextStyle(
                        fontSize: 14,
                        color: AppColors.textMuted,
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: AppColors.textMuted),
            ],
          ),
        ),
      ),
    );
  }
}
