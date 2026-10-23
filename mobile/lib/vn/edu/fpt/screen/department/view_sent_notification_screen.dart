import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/department_notification_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/sent_notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/attachment_tile_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/priority_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/read_progress_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_theme.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

/// Thông báo mình đã gửi: thống kê đã đọc, người nhận (lọc Tất cả / Đã đọc /
/// Chưa đọc, cuộn để tải thêm), nội dung và tệp đính kèm. Nơi mở màn hình
/// đã gọi [DepartmentNotificationController.openSentDetail] (hoặc vừa gửi).
class ViewSentNotificationScreen extends StatefulWidget {
  final DepartmentNotificationController controller;
  final int notificationId;

  const ViewSentNotificationScreen({
    super.key,
    required this.controller,
    required this.notificationId,
  });

  @override
  State<ViewSentNotificationScreen> createState() =>
      _ViewSentNotificationScreenState();
}

class _ViewSentNotificationScreenState
    extends State<ViewSentNotificationScreen> {
  DepartmentNotificationController get _controller => widget.controller;

  Future<void> _openFile(AttachmentModel file) async {
    final messenger = ScaffoldMessenger.of(context);
    final error = await _controller.openFile(widget.notificationId, file);
    if (error != null) {
      messenger.showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  bool _onScroll(ScrollNotification notification) {
    if (notification.metrics.extentAfter < 300) {
      _controller.loadMoreRecipients();
    }
    return false;
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
          return NotificationListener<ScrollNotification>(
            onNotification: _onScroll,
            child: RefreshIndicator(
              color: AppColors.orange,
              onRefresh: () =>
                  _controller.openSentDetail(widget.notificationId),
              child: ListView(
                padding: EdgeInsets.zero,
                children: [
                  PageHeaderWidget(
                    icon: Icons.outbox_outlined,
                    title: 'Thông báo đã gửi',
                    headline: detail?.title ?? 'Thông báo đã gửi',
                    subtitle: detail == null
                        ? null
                        : formatApiDateTime(detail.createdAt),
                  ),
                  ..._content(detail),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  List<Widget> _content(SentNotificationDetailModel? detail) {
    final error = _controller.detailError;
    if (error != null && detail == null) {
      return [
        ErrorRetryWidget(
          message: error.message,
          onRetry: () => _controller.openSentDetail(widget.notificationId),
        ),
      ];
    }
    if (detail == null) {
      return const [_Spinner()];
    }
    final badge = PriorityBadgeWidget.of(detail.priority);
    return [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.orangeLight,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Gửi tới: ${detail.targetLabel ?? ''}',
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                      color: AppColors.orangeDark,
                    ),
                  ),
                  const SizedBox(height: 10),
                  ReadProgressWidget(
                    readCount: detail.readCount,
                    recipientCount: detail.recipientCount,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),
            const _SectionTitle('Người nhận'),
            Wrap(
              spacing: 8,
              children: [
                _filterChip('Tất cả', null),
                _filterChip('Đã đọc', true),
                _filterChip('Chưa đọc', false),
              ],
            ),
            const SizedBox(height: 10),
            ..._recipientRows(),
            const SizedBox(height: 20),
            const _SectionTitle('Nội dung'),
            if (badge != null) ...[
              Align(alignment: Alignment.centerLeft, child: badge),
              const SizedBox(height: 8),
            ],
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
              _SectionTitle('Tệp đính kèm (${detail.files.length})'),
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

  Widget _filterChip(String label, bool? read) {
    final selected = _controller.readFilter == read;
    return ChoiceChip(
      label: Text(label),
      selected: selected,
      onSelected: (_) => _controller.setReadFilter(read),
      selectedColor: AppColors.orange,
      backgroundColor: Colors.white,
      showCheckmark: false,
      labelStyle: TextStyle(
        fontFamily: kAppFontFamily,
        fontSize: 14,
        fontWeight: FontWeight.w600,
        color: selected ? Colors.white : AppColors.text,
      ),
    );
  }

  List<Widget> _recipientRows() {
    final recipients = _controller.recipients;
    if (_controller.isLoadingDetail && recipients.isEmpty) {
      return const [_Spinner()];
    }
    if (recipients.isEmpty) {
      return [
        Text(switch (_controller.readFilter) {
          true => 'Chưa có ai đọc.',
          false => 'Mọi người đã đọc.',
          null => 'Không có người nhận.',
        }, style: const TextStyle(fontSize: 15, color: AppColors.textMuted)),
      ];
    }
    return [
      for (final recipient in recipients) _RecipientRow(recipient: recipient),
      if (_controller.isLoadingRecipients) const _Spinner(),
    ];
  }
}

class _SectionTitle extends StatelessWidget {
  final String text;

  const _SectionTitle(this.text);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Text(
        text,
        style: const TextStyle(
          fontSize: 17,
          fontWeight: FontWeight.w700,
          color: AppColors.navy,
        ),
      ),
    );
  }
}

class _Spinner extends StatelessWidget {
  const _Spinner();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.all(16),
      child: Center(child: CircularProgressIndicator(color: AppColors.orange)),
    );
  }
}

/// Một người nhận: dấu tích xanh và giờ đọc, hoặc vòng xám "Chưa đọc".
class _RecipientRow extends StatelessWidget {
  final RecipientModel recipient;

  const _RecipientRow({required this.recipient});

  @override
  Widget build(BuildContext context) {
    final read = recipient.read;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Icon(
            read ? Icons.check_circle : Icons.radio_button_unchecked,
            color: read ? AppColors.success : AppColors.hint,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              recipient.fullName,
              style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
                color: AppColors.text,
              ),
            ),
          ),
          Text(
            read ? 'Đã đọc ${formatApiDateTime(recipient.readAt)}' : 'Chưa đọc',
            style: TextStyle(
              fontSize: 13,
              color: read ? AppColors.success : AppColors.textMuted,
            ),
          ),
        ],
      ),
    );
  }
}
