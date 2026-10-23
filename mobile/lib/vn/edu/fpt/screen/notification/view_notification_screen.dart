import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/notification_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/attachment_tile_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/priority_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

/// Chi tiết thông báo: mức độ, người gửi, phạm vi, thời gian, nội dung (chọn
/// để sao chép được) và tệp đính kèm (bấm để tải về và mở). Lần đầu mở thì
/// backend đánh dấu đã đọc.
class ViewNotificationScreen extends StatefulWidget {
  final NotificationController controller;
  final int notificationId;

  const ViewNotificationScreen({
    super.key,
    required this.controller,
    required this.notificationId,
  });

  @override
  State<ViewNotificationScreen> createState() => _ViewNotificationScreenState();
}

class _ViewNotificationScreenState extends State<ViewNotificationScreen> {
  NotificationController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    // Màn hình danh sách bên dưới cũng nghe controller này, nên chỉ tải sau
    // khung hình đầu để không báo thay đổi giữa lúc đang dựng giao diện.
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => _controller.openNotification(widget.notificationId),
    );
  }

  Future<void> _openFile(AttachmentModel file) async {
    final messenger = ScaffoldMessenger.of(context);
    final error = await _controller.openFile(widget.notificationId, file);
    if (error != null) {
      messenger.showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          final detail = _controller.detail?.id == widget.notificationId
              ? _controller.detail
              : null;
          return RefreshIndicator(
            color: AppColors.orange,
            onRefresh: () =>
                _controller.openNotification(widget.notificationId),
            child: ListView(
              padding: EdgeInsets.zero,
              children: [
                PageHeaderWidget(
                  icon: Icons.notifications,
                  title: 'Thông báo',
                  headline: detail?.title ?? 'Thông báo',
                ),
                ..._content(detail),
              ],
            ),
          );
        },
      ),
    );
  }

  List<Widget> _content(NotificationDetailModel? detail) {
    final error = _controller.detailError;
    if (error != null) {
      return [
        ErrorRetryWidget(
          message: error.message,
          onRetry: () => _controller.openNotification(widget.notificationId),
        ),
      ];
    }
    if (detail == null) {
      return const [
        Padding(
          padding: EdgeInsets.all(24),
          child: Center(
            child: CircularProgressIndicator(color: AppColors.orange),
          ),
        ),
      ];
    }
    final badge = PriorityBadgeWidget.of(detail.priority);
    return [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (badge != null) ...[
              Align(alignment: Alignment.centerLeft, child: badge),
              const SizedBox(height: 12),
            ],
            _InfoRow(
              icon: Icons.person_outline,
              label: 'Người gửi',
              value: detail.senderName ?? '',
            ),
            _InfoRow(
              icon: Icons.groups_outlined,
              label: 'Gửi tới',
              value: detail.targetLabel ?? '',
            ),
            _InfoRow(
              icon: Icons.schedule,
              label: 'Thời gian',
              value: formatApiDateTime(detail.createdAt),
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.border),
              ),
              child: SelectableText(
                detail.content,
                style: const TextStyle(
                  fontSize: 16,
                  height: 1.5,
                  color: AppColors.text,
                ),
              ),
            ),
            if (detail.files.isNotEmpty) ...[
              const SizedBox(height: 20),
              Text(
                'Tệp đính kèm (${detail.files.length})',
                style: const TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w700,
                  color: AppColors.navy,
                ),
              ),
              const SizedBox(height: 10),
              for (final file in detail.files)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: AttachmentTileWidget(
                    file: file,
                    opening: _controller.openingFileId == file.id,
                    // Mỗi lúc chỉ tải một tệp.
                    onTap: _controller.openingFileId == null
                        ? () => _openFile(file)
                        : null,
                  ),
                ),
            ],
          ],
        ),
      ),
    ];
  }
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;

  const _InfoRow({
    required this.icon,
    required this.label,
    required this.value,
  });

  @override
  Widget build(BuildContext context) {
    if (value.isEmpty) {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 20, color: AppColors.orange),
          const SizedBox(width: 8),
          SizedBox(
            width: 84,
            child: Text(
              label,
              style: const TextStyle(fontSize: 15, color: AppColors.textMuted),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w600,
                color: AppColors.text,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
