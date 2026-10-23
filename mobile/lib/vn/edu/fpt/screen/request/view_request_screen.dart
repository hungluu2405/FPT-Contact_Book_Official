import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/request_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/request_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/attachment_tile_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/request_status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_theme.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/user_labels.dart';

/// Chi tiết đơn: thông tin, lý do, tệp minh chứng (bấm để mở), lịch sử duyệt
/// kèm ghi chú; nút "Hủy đơn" khi đơn còn chờ duyệt và người xem là người
/// gửi. Dùng chung [RequestController] với danh sách; nơi mở màn hình đã
/// gọi [RequestController.openRequest] (hoặc vừa gửi đơn xong).
class ViewRequestScreen extends StatefulWidget {
  final RequestController controller;
  final int requestId;
  final String viewerName;

  const ViewRequestScreen({
    super.key,
    required this.controller,
    required this.requestId,
    required this.viewerName,
  });

  @override
  State<ViewRequestScreen> createState() => _ViewRequestScreenState();
}

class _ViewRequestScreenState extends State<ViewRequestScreen> {
  RequestController get _controller => widget.controller;

  Future<void> _cancel() async {
    final messenger = ScaffoldMessenger.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Hủy đơn'),
        content: const Text(
          'Bạn có chắc muốn hủy đơn này? Đơn đã hủy không khôi phục được.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            style: TextButton.styleFrom(foregroundColor: AppColors.textMuted),
            child: const Text('Không'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: FilledButton.styleFrom(
              backgroundColor: AppColors.error,
              minimumSize: const Size(96, 48),
            ),
            child: const Text('Hủy đơn'),
          ),
        ],
      ),
    );
    if (confirmed != true) {
      return;
    }
    final error = await _controller.cancel();
    messenger.showSnackBar(
      SnackBar(content: Text(error?.message ?? 'Đã hủy đơn.')),
    );
  }

  Future<void> _openFile(AttachmentModel file) async {
    final messenger = ScaffoldMessenger.of(context);
    final error = await _controller.openFile(widget.requestId, file);
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
          final detail = _controller.detail?.id == widget.requestId
              ? _controller.detail
              : null;
          return RefreshIndicator(
            color: AppColors.orange,
            onRefresh: () => _controller.openRequest(widget.requestId),
            child: ListView(
              padding: EdgeInsets.zero,
              children: [
                PageHeaderWidget(
                  icon: Icons.mail_outline,
                  title: 'Đơn từ',
                  headline: detail?.title ?? 'Chi tiết đơn',
                  viewerName: widget.viewerName,
                ),
                ..._content(detail),
              ],
            ),
          );
        },
      ),
    );
  }

  List<Widget> _content(RequestDetailModel? detail) {
    final error = _controller.detailError;
    if (error != null) {
      return [
        ErrorRetryWidget(
          message: error.message,
          onRetry: () => _controller.openRequest(widget.requestId),
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
    final isLeave = detail.type == RequestType.leave;
    return [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                RequestStatusBadgeWidget(status: detail.status),
                const SizedBox(width: 8),
                Text(
                  kRequestTypeLabels[detail.type] ?? detail.type,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: AppColors.orangeDark,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            _InfoRow(
              label: 'Học sinh',
              value: [
                detail.studentName ?? '',
                detail.studentCode ?? '',
              ].where((part) => part.isNotEmpty).join(' · '),
            ),
            _InfoRow(
              label: 'Người gửi',
              value: detail.requesterName ?? '',
              trailing: detail.sentByParent
                  ? const StatusBadgeWidget(
                      label: 'Phụ huynh gửi thay',
                      tone: BadgeTone.info,
                    )
                  : null,
            ),
            if (isLeave) ...[
              _InfoRow(
                label: 'Thời gian nghỉ',
                value: formatApiDateRange(detail.fromDate, detail.toDate),
              ),
              _InfoRow(
                label: 'Lớp môn học',
                value: detail.sectionLabel ?? 'Tất cả các lớp',
              ),
            ],
            _InfoRow(
              label: 'Người duyệt',
              value: detail.reviewerName ?? 'Phòng quản trị',
            ),
            _InfoRow(
              label: 'Ngày gửi',
              value: formatApiDateTime(detail.createdAt),
            ),
            if (detail.status == RequestStatus.cancelled &&
                detail.updatedAt != null)
              _InfoRow(
                label: 'Đã hủy lúc',
                value: formatApiDateTime(detail.updatedAt),
              ),
            const SizedBox(height: 16),
            const _SectionTitle('Lý do'),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.border),
              ),
              child: SelectableText(
                detail.reason,
                style: const TextStyle(
                  fontSize: 16,
                  height: 1.5,
                  color: AppColors.text,
                ),
              ),
            ),
            if (detail.files.isNotEmpty) ...[
              const SizedBox(height: 20),
              _SectionTitle('Tệp minh chứng (${detail.files.length})'),
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
            const SizedBox(height: 20),
            const _SectionTitle('Lịch sử duyệt'),
            if (detail.reviews.isEmpty)
              const Text(
                'Chưa có.',
                style: TextStyle(fontSize: 15, color: AppColors.textMuted),
              )
            else
              for (final review in detail.reviews) _ReviewCard(review: review),
            if (detail.canCancel) ...[
              const SizedBox(height: 24),
              SizedBox(
                height: 52,
                child: OutlinedButton.icon(
                  onPressed: _controller.isCancelling ? null : _cancel,
                  icon: const Icon(Icons.cancel_outlined),
                  label: const Text('Hủy đơn'),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.error,
                    side: const BorderSide(color: AppColors.error),
                    textStyle: const TextStyle(
                      fontFamily: kAppFontFamily,
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
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

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;
  final Widget? trailing;

  const _InfoRow({required this.label, required this.value, this.trailing});

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
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: const TextStyle(fontSize: 15, color: AppColors.textMuted),
            ),
          ),
          Expanded(
            child: Wrap(
              spacing: 8,
              runSpacing: 4,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Text(
                  value,
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: AppColors.text,
                  ),
                ),
                ?trailing,
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Một lần duyệt: quyết định, người duyệt, thời gian, ghi chú.
class _ReviewCard extends StatelessWidget {
  final RequestReviewModel review;

  const _ReviewCard({required this.review});

  @override
  Widget build(BuildContext context) {
    final comment = review.comment ?? '';
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.orangeLight,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              RequestStatusBadgeWidget(status: review.decision),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  [
                    review.reviewerName ?? 'Phòng quản trị',
                    formatApiDateTime(review.reviewedAt),
                  ].where((part) => part.isNotEmpty).join(' · '),
                  style: const TextStyle(
                    fontSize: 14,
                    color: AppColors.textMuted,
                  ),
                ),
              ),
            ],
          ),
          if (comment.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(
              comment,
              style: const TextStyle(fontSize: 15, color: AppColors.text),
            ),
          ],
        ],
      ),
    );
  }
}
