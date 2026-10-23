import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/attendance_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/auth_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/club_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/exam_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/grade_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/home_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/notification_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/profile_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/request_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/schedule_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/view_profile_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/attendance/list_attendance_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/club/list_club_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/grade/view_grade_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/notification/list_notification_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/request/list_request_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/schedule/list_exam_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/schedule/view_schedule_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/app_shell_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/auth_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/child_selector_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/feature_tile_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/home_banner_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/user_labels.dart';

/// Khung app sau đăng nhập: thanh điều hướng dưới gồm Trang chủ, Thông báo
/// và Tài khoản.
class HomeScreen extends StatefulWidget {
  final AuthController controller;
  final HomeController homeController;
  final ProfileController profileController;
  final ScheduleController scheduleController;
  final ExamController examController;
  final GradeController gradeController;
  final AttendanceController attendanceController;
  final ClubController clubController;
  final NotificationController notificationController;
  final RequestController requestController;

  const HomeScreen({
    super.key,
    required this.controller,
    required this.homeController,
    required this.profileController,
    required this.scheduleController,
    required this.examController,
    required this.gradeController,
    required this.attendanceController,
    required this.clubController,
    required this.notificationController,
    required this.requestController,
  });

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  static const int _notificationTab = 1;
  static const int _accountTab = 2;

  @override
  void initState() {
    super.initState();
    widget.profileController.reset();
    widget.notificationController
      ..reset()
      ..onUnreadChanged = widget.homeController.reloadUnreadCount;
    widget.requestController.reset();
    final user = widget.controller.currentUser;
    if (user != null) {
      widget.homeController.start(user);
    }
  }

  @override
  void dispose() {
    // Rời khung app (đăng xuất): xóa tệp đính kèm đã tải và đơn đang soạn.
    widget.notificationController
      ..onUnreadChanged = null
      ..reset();
    widget.requestController.reset();
    super.dispose();
  }

  void _onTabOpened(int index) {
    if (index == _notificationTab) {
      widget.notificationController.open();
    } else if (index == _accountTab) {
      widget.profileController.load();
    }
  }

  @override
  Widget build(BuildContext context) {
    return AppShellWidget(
      badgeListenable: widget.homeController,
      onTabOpened: _onTabOpened,
      tabs: [
        AppShellTab(
          icon: Icons.home_outlined,
          selectedIcon: Icons.home,
          label: 'Trang chủ',
          rootBuilder: (_) => _DashboardTab(
            controller: widget.homeController,
            scheduleController: widget.scheduleController,
            examController: widget.examController,
            gradeController: widget.gradeController,
            attendanceController: widget.attendanceController,
            clubController: widget.clubController,
            requestController: widget.requestController,
          ),
        ),
        AppShellTab(
          icon: Icons.notifications_outlined,
          selectedIcon: Icons.notifications,
          label: 'Thông báo',
          rootBuilder: (_) => ListNotificationScreen(
            controller: widget.notificationController,
            homeController: widget.homeController,
          ),
          badgeCount: () => widget.homeController.unreadCount,
        ),
        AppShellTab(
          icon: Icons.person_outline,
          selectedIcon: Icons.person,
          label: 'Tài khoản',
          rootBuilder: (_) => ViewProfileScreen(
            controller: widget.profileController,
            authController: widget.controller,
          ),
        ),
      ],
    );
  }
}

/// Tab Trang chủ: lời chào, chọn con (PH), ảnh trường, lưới chức năng.
class _DashboardTab extends StatelessWidget {
  final HomeController controller;
  final ScheduleController scheduleController;
  final ExamController examController;
  final GradeController gradeController;
  final AttendanceController attendanceController;
  final ClubController clubController;
  final RequestController requestController;

  const _DashboardTab({
    required this.controller,
    required this.scheduleController,
    required this.examController,
    required this.gradeController,
    required this.attendanceController,
    required this.clubController,
    required this.requestController,
  });

  /// Tên người đang được xem: con đang chọn, hoặc chính SV.
  String get _viewerName =>
      controller.selectedChild?.name ?? controller.user?.fullName ?? '';

  void _openSchedule(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ViewScheduleScreen(
          controller: scheduleController,
          studentId: controller.selectedStudentId,
          viewerName: _viewerName,
        ),
      ),
    );
  }

  void _openGrades(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ViewGradeScreen(
          controller: gradeController,
          studentId: controller.selectedStudentId,
          viewerName: _viewerName,
        ),
      ),
    );
  }

  void _openAttendance(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ListAttendanceScreen(
          controller: attendanceController,
          studentId: controller.selectedStudentId,
          viewerName: _viewerName,
        ),
      ),
    );
  }

  void _openClubs(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ListClubScreen(
          controller: clubController,
          studentId: controller.selectedStudentId,
          viewerName: _viewerName,
        ),
      ),
    );
  }

  void _openRequests(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ListRequestScreen(
          controller: requestController,
          studentId: controller.selectedStudentId,
          viewerName: _viewerName,
        ),
      ),
    );
  }

  void _openExams(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ListExamScreen(
          controller: examController,
          studentId: controller.selectedStudentId,
          viewerName: _viewerName,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: ListenableBuilder(
        listenable: controller,
        builder: (context, _) => RefreshIndicator(
          color: AppColors.orange,
          onRefresh: controller.refresh,
          // Nội dung ngắn nên dựng hết một lần thay cho ListView.
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _Greeting(controller: controller),
                const SizedBox(height: 16),
                const HomeBannerWidget(),
                const SizedBox(height: 20),
                _body(context),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _body(BuildContext context) {
    final user = controller.user;
    if (user == null || !user.canUseMobile) {
      return const _Message(
        'Tài khoản quản trị dùng trên trang web của nhà trường.',
      );
    }
    if (!user.isStudent && !user.isParent) {
      return const _Message(
        'Chức năng giáo viên sẽ có trong bản cập nhật tới.',
      );
    }
    if (!user.isStudent) {
      if (controller.isLoadingChildren && controller.children.isEmpty) {
        return const Padding(
          padding: EdgeInsets.all(24),
          child: Center(
            child: CircularProgressIndicator(color: AppColors.orange),
          ),
        );
      }
      final error = controller.childrenError;
      if (error != null && controller.children.isEmpty) {
        return ErrorRetryWidget(
          message: error.message,
          onRetry: controller.refresh,
        );
      }
    }
    if (!controller.hasStudentData) {
      return const _Message(
        'Tài khoản chưa được liên kết với con nào. '
        'Vui lòng liên hệ nhà trường để được liên kết.',
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _TodayCard(
          controller: controller,
          onOpen: () => _openSchedule(context),
        ),
        const SizedBox(height: 20),
        _FeatureGrid(
          onSchedule: () => _openSchedule(context),
          onExams: () => _openExams(context),
          onGrades: () => _openGrades(context),
          onAttendance: () => _openAttendance(context),
          onClubs: () => _openClubs(context),
          onRequests: () => _openRequests(context),
        ),
      ],
    );
  }
}

class _Greeting extends StatelessWidget {
  final HomeController controller;

  const _Greeting({required this.controller});

  @override
  Widget build(BuildContext context) {
    final user = controller.user;
    final fullName = user?.fullName ?? '';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            CircleAvatar(
              radius: 28,
              backgroundColor: AppColors.orangeLight,
              child: Text(
                initialOf(fullName),
                style: const TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.w700,
                  color: AppColors.orangeDark,
                ),
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Xin chào,',
                    style: TextStyle(fontSize: 15, color: AppColors.textMuted),
                  ),
                  Text(
                    fullName,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.w700,
                      color: AppColors.navy,
                    ),
                  ),
                  Text(
                    roleLabelsOf(user),
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                      color: AppColors.orangeDark,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        const ThreeColorStripe(),
        if ((user?.isParent ?? false) && controller.hasStudentData) ...[
          const SizedBox(height: 12),
          ChildSelectorWidget(controller: controller),
        ],
      ],
    );
  }
}

/// Lưới chức năng của HS và PH.
class _FeatureGrid extends StatelessWidget {
  final VoidCallback onSchedule;
  final VoidCallback onExams;
  final VoidCallback onGrades;
  final VoidCallback onAttendance;
  final VoidCallback onClubs;
  final VoidCallback onRequests;

  const _FeatureGrid({
    required this.onSchedule,
    required this.onExams,
    required this.onGrades,
    required this.onAttendance,
    required this.onClubs,
    required this.onRequests,
  });

  @override
  Widget build(BuildContext context) {
    return GridView.count(
      crossAxisCount: 2,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      mainAxisSpacing: 12,
      crossAxisSpacing: 12,
      childAspectRatio: 1.25,
      children: [
        FeatureTileWidget(
          icon: Icons.calendar_month,
          label: 'Lịch học',
          onTap: onSchedule,
        ),
        FeatureTileWidget(
          icon: Icons.event_note,
          label: 'Lịch thi',
          onTap: onExams,
        ),
        FeatureTileWidget(
          icon: Icons.workspace_premium,
          label: 'Điểm',
          onTap: onGrades,
        ),
        FeatureTileWidget(
          icon: Icons.fact_check,
          label: 'Điểm danh',
          onTap: onAttendance,
        ),
        FeatureTileWidget(
          icon: Icons.groups,
          label: 'Câu lạc bộ',
          onTap: onClubs,
        ),
        FeatureTileWidget(
          icon: Icons.mail_outline,
          label: 'Đơn từ',
          onTap: onRequests,
        ),
      ],
    );
  }
}

/// Thẻ "Lịch học hôm nay": tối đa 3 buổi đầu, bấm để mở lịch học.
class _TodayCard extends StatelessWidget {
  static const int _maxItems = 3;

  final HomeController controller;
  final VoidCallback onOpen;

  const _TodayCard({required this.controller, required this.onOpen});

  @override
  Widget build(BuildContext context) {
    final items = controller.todayItems;
    final error = controller.todayError;
    final Widget content;
    if (controller.isLoadingToday && items.isEmpty) {
      content = const Padding(
        padding: EdgeInsets.all(12),
        child: Center(
          child: SizedBox(
            width: 24,
            height: 24,
            child: CircularProgressIndicator(
              strokeWidth: 2.5,
              color: AppColors.orange,
            ),
          ),
        ),
      );
    } else if (error != null) {
      content = Row(
        children: [
          Expanded(
            child: Text(
              error.message,
              style: const TextStyle(fontSize: 14, color: AppColors.error),
            ),
          ),
          TextButton(
            onPressed: controller.loadToday,
            child: const Text('Thử lại'),
          ),
        ],
      );
    } else if (items.isEmpty) {
      content = const Text(
        'Hôm nay không có buổi học nào.',
        style: TextStyle(fontSize: 15, color: AppColors.textMuted),
      );
    } else {
      content = Column(
        children: [
          for (final item in items.take(_maxItems))
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: Row(
                children: [
                  SizedBox(
                    width: 104,
                    child: Text(
                      '${formatApiTime(item.startTime)}–'
                      '${formatApiTime(item.endTime)}',
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: AppColors.navy,
                      ),
                    ),
                  ),
                  Expanded(
                    child: Text(
                      item.subjectCode,
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: AppColors.text,
                        decoration: item.isCancelled
                            ? TextDecoration.lineThrough
                            : null,
                      ),
                    ),
                  ),
                  Text(
                    item.roomCode ?? '',
                    style: const TextStyle(
                      fontSize: 14,
                      color: AppColors.textMuted,
                    ),
                  ),
                ],
              ),
            ),
          if (items.length > _maxItems)
            Align(
              alignment: Alignment.centerLeft,
              child: Text(
                'và ${items.length - _maxItems} buổi khác',
                style: const TextStyle(
                  fontSize: 14,
                  color: AppColors.textMuted,
                ),
              ),
            ),
        ],
      );
    }

    return Material(
      color: AppColors.orangeLight,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onOpen,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 8, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Row(
                children: [
                  Icon(Icons.today, color: AppColors.orange),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Lịch học hôm nay',
                      style: TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w700,
                        color: AppColors.navy,
                      ),
                    ),
                  ),
                  Text(
                    'Xem lịch',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: AppColors.orangeDark,
                    ),
                  ),
                  Icon(Icons.chevron_right, color: AppColors.orangeDark),
                ],
              ),
              const SizedBox(height: 8),
              Padding(padding: const EdgeInsets.only(right: 8), child: content),
            ],
          ),
        ),
      ),
    );
  }
}

class _Message extends StatelessWidget {
  final String text;

  const _Message(this.text);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Text(
        text,
        style: const TextStyle(fontSize: 16, color: AppColors.textMuted),
      ),
    );
  }
}
