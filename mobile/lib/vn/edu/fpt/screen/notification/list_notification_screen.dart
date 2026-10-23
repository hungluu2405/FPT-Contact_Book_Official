import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/home_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/notification_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/notification/view_notification_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/empty_state_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/priority_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_theme.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

/// Tab Thông báo: hộp thư mới nhất trước, cuộn để tải thêm, kéo xuống để tải
/// lại. Thông báo chưa đọc in đậm, có chấm cam. Danh sách được tải mỗi lần
/// mở tab (HomeScreen gọi [NotificationController.open]); số chưa đọc lấy từ
/// [homeController].
class ListNotificationScreen extends StatelessWidget {
  final NotificationController controller;
  final HomeController homeController;

  const ListNotificationScreen({
    super.key,
    required this.controller,
    required this.homeController,
  });

  void _openNotification(BuildContext context, int id) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) =>
            ViewNotificationScreen(controller: controller, notificationId: id),
      ),
    );
  }

  Future<void> _markAllRead(BuildContext context) async {
    final messenger = ScaffoldMessenger.of(context);
    final error = await controller.markAllRead();
    messenger.showSnackBar(
      SnackBar(
        content: Text(
          error?.message ?? 'Đã đánh dấu tất cả thông báo là đã đọc.',
        ),
      ),
    );
  }

  bool _onScroll(ScrollNotification notification) {
    if (notification.metrics.extentAfter < 300) {
      controller.loadMore();
    }
    return false;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListenableBuilder(
        listenable: Listenable.merge([controller, homeController]),
        builder: (context, _) {
          final items = controller.items;
          return NotificationListener<ScrollNotification>(
            onNotification: _onScroll,
            child: RefreshIndicator(
              color: AppColors.orange,
              onRefresh: () => Future.wait([
                controller.open(),
                homeController.reloadUnreadCount(),
              ]),
              child: ListView.builder(
                padding: EdgeInsets.zero,
                itemCount: items.length + 2,
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 16),
                      child: _header(context),
                    );
                  }
                  if (index == items.length + 1) {
                    return _footer();
                  }
                  final item = items[index - 1];
                  return Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                    child: _NotificationCard(
                      item: item,
                      onTap: () => _openNotification(context, item.id),
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

  Widget _header(BuildContext context) {
    // Chưa tải được số chưa đọc (lỗi mạng, tài khoản quản trị) thì dựa vào
    // các thông báo đã tải và không ghi con số.
    final count = homeController.unreadCount;
    final hasUnread = (count ?? 0) > 0 || controller.hasUnreadLoaded;
    return PageHeaderWidget(
      icon: Icons.notifications,
      title: 'Thông báo',
      headline: 'Hộp thư',
      subtitle: count == null
          ? null
          : (count > 0
                ? 'Bạn có $count thông báo chưa đọc'
                : 'Bạn đã đọc hết thông báo'),
      bottom: hasUnread
          ? OutlinedButton.icon(
              onPressed: controller.isMarkingAll
                  ? null
                  : () => _markAllRead(context),
              icon: const Icon(Icons.done_all),
              label: const Text('Đánh dấu tất cả đã đọc'),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.orangeDark,
                backgroundColor: Colors.white,
                side: const BorderSide(color: AppColors.orange),
                minimumSize: const Size(0, 48),
                textStyle: const TextStyle(
                  fontFamily: kAppFontFamily,
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                ),
              ),
            )
          : null,
    );
  }

  Widget _footer() {
    final error = controller.error;
    if (error != null) {
      return ErrorRetryWidget(
        message: error.message,
        onRetry: controller.loadMore,
      );
    }
    if (controller.isLoading ||
        (controller.items.isEmpty && controller.hasMore)) {
      return const Padding(
        padding: EdgeInsets.all(24),
        child: Center(
          child: CircularProgressIndicator(color: AppColors.orange),
        ),
      );
    }
    if (controller.items.isEmpty) {
      return const EmptyStateWidget(
        icon: Icons.notifications_none,
        message: 'Chưa có thông báo nào.',
      );
    }
    if (controller.hasMore) {
      // Dự phòng khi trang đầu chưa đủ dài để cuộn.
      return Center(
        child: TextButton(
          onPressed: controller.loadMore,
          child: const Text('Tải thêm'),
        ),
      );
    }
    return const SizedBox(height: 24);
  }
}

class _NotificationCard extends StatelessWidget {
  final NotificationModel item;
  final VoidCallback onTap;

  const _NotificationCard({required this.item, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final badge = PriorityBadgeWidget.of(item.priority);
    final unread = !item.read;
    return Material(
      color: unread ? AppColors.orangeLight : Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: AppColors.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 14, 8, 14),
          child: Row(
            children: [
              SizedBox(
                width: 16,
                child: unread
                    ? Semantics(
                        label: 'Chưa đọc',
                        child: Container(
                          width: 10,
                          height: 10,
                          decoration: const BoxDecoration(
                            color: AppColors.orange,
                            shape: BoxShape.circle,
                          ),
                        ),
                      )
                    : null,
              ),
              const SizedBox(width: 4),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (badge != null) ...[badge, const SizedBox(height: 4)],
                    Text(
                      item.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: unread ? FontWeight.w700 : FontWeight.w500,
                        color: unread ? AppColors.navy : AppColors.text,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      [
                        if ((item.senderName ?? '').isNotEmpty)
                          item.senderName!,
                        formatApiDateTime(item.createdAt),
                      ].join(' · '),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 13,
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
