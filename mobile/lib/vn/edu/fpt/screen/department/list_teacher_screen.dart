import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/department_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/teacher_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/department/view_teacher_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/empty_state_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/user_labels.dart';

/// Giáo viên của tổ đang chọn: tìm theo tên, mã, số điện thoại; cuộn để tải
/// thêm; bấm để xem chi tiết.
class ListTeacherScreen extends StatefulWidget {
  final DepartmentController controller;

  const ListTeacherScreen({super.key, required this.controller});

  @override
  State<ListTeacherScreen> createState() => _ListTeacherScreenState();
}

class _ListTeacherScreenState extends State<ListTeacherScreen> {
  final _searchCtrl = TextEditingController();

  DepartmentController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    // Khối "Tổ chuyên môn" trên trang chủ bên dưới cũng nghe controller này.
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => _controller.openTeachers(),
    );
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  void _openTeacher(int userId) {
    _controller.openTeacher(userId);
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) =>
            ViewTeacherScreen(controller: _controller, userId: userId),
      ),
    );
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
          final teachers = _controller.teachers;
          return NotificationListener<ScrollNotification>(
            onNotification: _onScroll,
            child: RefreshIndicator(
              color: AppColors.orange,
              onRefresh: _controller.reload,
              child: ListView.builder(
                padding: EdgeInsets.zero,
                itemCount: teachers.length + 2,
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return _header();
                  }
                  if (index == teachers.length + 1) {
                    return _footer();
                  }
                  final teacher = teachers[index - 1];
                  return Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                    child: _TeacherCard(
                      teacher: teacher,
                      onTap: () => _openTeacher(teacher.userId),
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
    final department = _controller.selectedDepartment;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        PageHeaderWidget(
          icon: Icons.badge_outlined,
          title: 'Tổ chuyên môn',
          headline: 'Giáo viên trong tổ',
          subtitle: department == null
              ? null
              : '${department.name} · ${department.teacherCount} giáo viên',
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
          child: TextField(
            controller: _searchCtrl,
            textInputAction: TextInputAction.search,
            onSubmitted: _controller.search,
            style: const TextStyle(fontSize: 16, color: AppColors.text),
            decoration: InputDecoration(
              hintText: 'Tìm theo tên, mã hoặc số điện thoại',
              prefixIcon: const Icon(Icons.search),
              filled: true,
              fillColor: Colors.white,
              contentPadding: const EdgeInsets.symmetric(vertical: 14),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: const BorderSide(color: AppColors.border),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: const BorderSide(color: AppColors.border),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _footer() {
    final error = _controller.error;
    if (error != null) {
      return ErrorRetryWidget(
        message: error.message,
        onRetry: _controller.loadMore,
      );
    }
    if (_controller.isLoading ||
        (_controller.teachers.isEmpty && _controller.hasMore)) {
      return const Padding(
        padding: EdgeInsets.all(24),
        child: Center(
          child: CircularProgressIndicator(color: AppColors.orange),
        ),
      );
    }
    if (_controller.teachers.isEmpty) {
      return EmptyStateWidget(
        icon: Icons.badge_outlined,
        message: _controller.query.isEmpty
            ? 'Tổ chưa có giáo viên nào.'
            : 'Không tìm thấy giáo viên phù hợp.',
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

class _TeacherCard extends StatelessWidget {
  final TeacherModel teacher;
  final VoidCallback onTap;

  const _TeacherCard({required this.teacher, required this.onTap});

  @override
  Widget build(BuildContext context) {
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
              CircleAvatar(
                radius: 24,
                backgroundColor: AppColors.orangeLight,
                child: Text(
                  initialOf(teacher.fullName),
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w700,
                    color: AppColors.orangeDark,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (teacher.head || !teacher.active) ...[
                      Wrap(
                        spacing: 6,
                        children: [
                          if (teacher.head)
                            const StatusBadgeWidget(label: 'Tổ trưởng'),
                          if (!teacher.active)
                            const StatusBadgeWidget(
                              label: 'Đã khóa',
                              tone: BadgeTone.muted,
                            ),
                        ],
                      ),
                      const SizedBox(height: 4),
                    ],
                    Text(
                      teacher.fullName,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: AppColors.navy,
                      ),
                    ),
                    Text(
                      '${teacher.teacherCode} · ${teacher.phone}',
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
