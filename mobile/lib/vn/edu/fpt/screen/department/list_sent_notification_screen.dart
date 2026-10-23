import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/department_notification_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/sent_notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/department/view_sent_notification_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/empty_state_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/priority_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/read_progress_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

/// Thông báo mình đã gửi, mới nhất trước, mỗi dòng có thanh "đã đọc / người
/// nhận"; cuộn để tải thêm, bấm để xem ai đã đọc.
class ListSentNotificationScreen extends StatefulWidget {
  final DepartmentNotificationController controller;

  const ListSentNotificationScreen({super.key, required this.controller});

  @override
  State<ListSentNotificationScreen> createState() =>
      _ListSentNotificationScreenState();
}

class _ListSentNotificationScreenState
    extends State<ListSentNotificationScreen> {
  DepartmentNotificationController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.openSent();
  }

  void _openDetail(int id) {
    _controller.openSentDetail(id);
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ViewSentNotificationScreen(
          controller: _controller,
          notificationId: id,
        ),
      ),
    );
  }

  bool _onScroll(ScrollNotification notification) {
    if (notification.metrics.extentAfter < 300) {
      _controller.loadMoreSent();
    }
    return false;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          final items = _controller.sentItems;
          return NotificationListener<ScrollNotification>(
            onNotification: _onScroll,
            child: RefreshIndicator(
              color: AppColors.orange,
              onRefresh: _controller.openSent,
              child: ListView.builder(
                padding: EdgeInsets.zero,
                itemCount: items.length + 2,
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return const Padding(
                      padding: EdgeInsets.only(bottom: 16),
                      child: PageHeaderWidget(
                        icon: Icons.outbox_outlined,
                        title: 'Tổ chuyên môn',
                        headline: 'Thông báo đã gửi',
                        subtitle: 'Bấm vào thông báo để xem ai đã đọc.',
                      ),
                    );
                  }
                  if (index == items.length + 1) {
                    return _footer();
                  }
                  final item = items[index - 1];
                  return Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                    child: _SentCard(
                      item: item,
                      onTap: () => _openDetail(item.id),
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

  Widget _footer() {
    final error = _controller.sentError;
    if (error != null) {
      return ErrorRetryWidget(
        message: error.message,
        onRetry: _controller.loadMoreSent,
      );
    }
    if (_controller.isLoadingSent ||
        (_controller.sentItems.isEmpty && _controller.sentHasMore)) {
      return const Padding(
        padding: EdgeInsets.all(24),
        child: Center(
          child: CircularProgressIndicator(color: AppColors.orange),
        ),
      );
    }
    if (_controller.sentItems.isEmpty) {
      return const EmptyStateWidget(
        icon: Icons.outbox_outlined,
        message: 'Bạn chưa gửi thông báo nào.',
      );
    }
    if (_controller.sentHasMore) {
      // Dự phòng khi trang đầu chưa đủ dài để cuộn.
      return Center(
        child: TextButton(
          onPressed: _controller.loadMoreSent,
          child: const Text('Tải thêm'),
        ),
      );
    }
    return const SizedBox(height: 24);
  }
}

/// "Cả tổ" hoặc "Riêng một giáo viên"; phạm vi khác (gửi trên web) theo
/// nhãn chung.
String targetTypeLabel(String type) => switch (type) {
  NotificationTargetType.department => 'Cả tổ',
  NotificationTargetType.user => 'Riêng một người',
  _ => 'Gửi trên web',
};

class _SentCard extends StatelessWidget {
  final SentNotificationModel item;
  final VoidCallback onTap;

  const _SentCard({required this.item, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final badge = PriorityBadgeWidget.of(item.priority);
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
          padding: const EdgeInsets.fromLTRB(16, 14, 8, 14),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (badge != null) ...[badge, const SizedBox(height: 4)],
                    Text(
                      item.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: AppColors.navy,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${targetTypeLabel(item.targetType)} · '
                      '${formatApiDateTime(item.createdAt)}',
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppColors.textMuted,
                      ),
                    ),
                    const SizedBox(height: 10),
                    ReadProgressWidget(
                      readCount: item.readCount,
                      recipientCount: item.recipientCount,
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
