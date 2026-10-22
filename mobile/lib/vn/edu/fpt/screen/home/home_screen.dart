import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/auth_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/home_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/profile_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/view_profile_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/auth_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/child_selector_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/feature_tile_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/home_banner_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/user_labels.dart';

/// Khung app sau đăng nhập: thanh điều hướng dưới gồm Trang chủ, Thông báo
/// và Tài khoản.
class HomeScreen extends StatefulWidget {
  final AuthController controller;
  final HomeController homeController;
  final ProfileController profileController;

  const HomeScreen({
    super.key,
    required this.controller,
    required this.homeController,
    required this.profileController,
  });

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  static const int _accountTab = 2;

  int _tab = 0;

  /// Tab chỉ được dựng khi mở lần đầu, để tab chưa xem không tải dữ liệu.
  final Set<int> _opened = {0};

  @override
  void initState() {
    super.initState();
    widget.profileController.reset();
    final user = widget.controller.currentUser;
    if (user != null) {
      widget.homeController.start(user);
    }
  }

  void _selectTab(int index) {
    setState(() {
      _tab = index;
      _opened.add(index);
    });
    if (index == _accountTab) {
      widget.profileController.load();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _tab,
        children: [
          _DashboardTab(controller: widget.homeController),
          const _NotificationTab(),
          if (_opened.contains(_accountTab))
            ViewProfileScreen(
              controller: widget.profileController,
              authController: widget.controller,
            )
          else
            const SizedBox.shrink(),
        ],
      ),
      bottomNavigationBar: ListenableBuilder(
        listenable: widget.homeController,
        builder: (context, _) {
          final unread = widget.homeController.unreadCount ?? 0;
          return NavigationBar(
            selectedIndex: _tab,
            onDestinationSelected: _selectTab,
            backgroundColor: Colors.white,
            indicatorColor: AppColors.orangeLight,
            surfaceTintColor: Colors.transparent,
            destinations: [
              const NavigationDestination(
                icon: Icon(Icons.home_outlined),
                selectedIcon: Icon(Icons.home, color: AppColors.orange),
                label: 'Trang chủ',
              ),
              NavigationDestination(
                icon: Badge(
                  isLabelVisible: unread > 0,
                  label: Text(unread > 99 ? '99+' : '$unread'),
                  child: const Icon(Icons.notifications_outlined),
                ),
                selectedIcon: Badge(
                  isLabelVisible: unread > 0,
                  label: Text(unread > 99 ? '99+' : '$unread'),
                  child: const Icon(
                    Icons.notifications,
                    color: AppColors.orange,
                  ),
                ),
                label: 'Thông báo',
              ),
              const NavigationDestination(
                icon: Icon(Icons.person_outline),
                selectedIcon: Icon(Icons.person, color: AppColors.orange),
                label: 'Tài khoản',
              ),
            ],
          );
        },
      ),
    );
  }
}

/// Tab Trang chủ: lời chào, chọn con (PH), ảnh trường, lưới chức năng.
class _DashboardTab extends StatelessWidget {
  final HomeController controller;

  const _DashboardTab({required this.controller});

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
                _body(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _body() {
    final user = controller.user;
    if (user == null || !user.canUseMobile) {
      return const _Message(
        'Tài khoản quản trị dùng trên trang web của nhà trường.',
      );
    }
    if (!user.isStudent && !user.isParent) {
      return const _Message(
        'Chức năng giảng viên sẽ có trong bản cập nhật tới.',
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
    return const _FeatureGrid();
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

/// Lưới chức năng. Các ô chưa có màn hình hiện "Sắp có"; lịch học và lịch
/// thi mở ở 10b-2, điểm và điểm danh ở 10b-3, CLB ở 10b-4, đơn từ ở 10c.
class _FeatureGrid extends StatelessWidget {
  const _FeatureGrid();

  @override
  Widget build(BuildContext context) {
    return GridView.count(
      crossAxisCount: 2,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      mainAxisSpacing: 12,
      crossAxisSpacing: 12,
      childAspectRatio: 1.25,
      children: const [
        FeatureTileWidget(icon: Icons.calendar_month, label: 'Lịch học'),
        FeatureTileWidget(icon: Icons.event_note, label: 'Lịch thi'),
        FeatureTileWidget(icon: Icons.workspace_premium, label: 'Điểm'),
        FeatureTileWidget(icon: Icons.fact_check, label: 'Điểm danh'),
        FeatureTileWidget(icon: Icons.groups, label: 'Câu lạc bộ'),
        FeatureTileWidget(icon: Icons.mail_outline, label: 'Đơn từ'),
      ],
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

/// Danh sách thông báo làm ở giai đoạn 10c.
class _NotificationTab extends StatelessWidget {
  const _NotificationTab();

  @override
  Widget build(BuildContext context) {
    return const SafeArea(
      child: Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.notifications_none,
                size: 56,
                color: AppColors.textMuted,
              ),
              SizedBox(height: 16),
              Text(
                'Danh sách thông báo sẽ có trong bản cập nhật tới.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 16, color: AppColors.textMuted),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
