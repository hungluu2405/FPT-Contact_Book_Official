import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/request_review_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/request_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/auth_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/request_detail_body_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_theme.dart';

/// Chi tiết đơn cho giáo viên duyệt: thân đơn như của HS, PH và phần duyệt
/// (ghi chú, nút Duyệt và Từ chối, có hộp xác nhận) khi đơn còn chờ duyệt.
/// Nơi mở màn hình đã gọi [RequestReviewController.openRequest].
class ViewReviewRequestScreen extends StatefulWidget {
  final RequestReviewController controller;
  final int requestId;
  final String viewerName;

  const ViewReviewRequestScreen({
    super.key,
    required this.controller,
    required this.requestId,
    required this.viewerName,
  });

  @override
  State<ViewReviewRequestScreen> createState() =>
      _ViewReviewRequestScreenState();
}

class _ViewReviewRequestScreenState extends State<ViewReviewRequestScreen> {
  final _commentCtrl = TextEditingController();

  RequestReviewController get _controller => widget.controller;

  @override
  void dispose() {
    _commentCtrl.dispose();
    super.dispose();
  }

  Future<void> _openFile(AttachmentModel file) async {
    final messenger = ScaffoldMessenger.of(context);
    final error = await _controller.openFile(widget.requestId, file);
    if (error != null) {
      messenger.showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  Future<void> _review({required bool approve}) async {
    FocusScope.of(context).unfocus();
    // Từ chối thiếu ghi chú thì báo lỗi ngay, không cần hỏi xác nhận.
    if (!approve && _commentCtrl.text.trim().isEmpty) {
      await _controller.review(approve: false, comment: '');
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(approve ? 'Duyệt đơn' : 'Từ chối đơn'),
        content: Text(
          approve
              ? 'Duyệt đơn này? Buổi vắng không phép trong thời gian nghỉ sẽ '
                    'được chuyển thành vắng có phép.'
              : 'Từ chối đơn này? Học sinh và phụ huynh sẽ thấy ghi chú của bạn.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            style: TextButton.styleFrom(foregroundColor: AppColors.textMuted),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: FilledButton.styleFrom(
              backgroundColor: approve ? AppColors.orange : AppColors.error,
              minimumSize: const Size(96, 48),
            ),
            child: Text(approve ? 'Duyệt' : 'Từ chối'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) {
      return;
    }
    final messenger = ScaffoldMessenger.of(context);
    final result = await _controller.review(
      approve: approve,
      comment: _commentCtrl.text,
    );
    if (result == null) {
      return;
    }
    _commentCtrl.clear();
    final String message;
    if (!approve) {
      message = 'Đã từ chối đơn.';
    } else if (result.excusedSessions > 0) {
      message =
          'Đã duyệt đơn. ${result.excusedSessions} buổi vắng không phép đã '
          'chuyển thành vắng có phép.';
    } else {
      message = 'Đã duyệt đơn.';
    }
    messenger.showSnackBar(SnackBar(content: Text(message)));
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
                  icon: Icons.fact_check_outlined,
                  title: 'Duyệt đơn',
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
    final reviewError = _controller.reviewError;
    return [
      RequestDetailBodyWidget(
        detail: detail,
        openingFileId: _controller.openingFileId,
        onOpenFile: _openFile,
        footer: [
          if (reviewError != null) ...[
            const SizedBox(height: 16),
            AuthErrorBanner(message: reviewError),
          ],
          if (detail.canReview) ...[
            const SizedBox(height: 24),
            AuthTextField(
              label: 'Ghi chú cho học sinh, phụ huynh',
              hint: 'Bắt buộc khi từ chối, ví dụ: Thiếu giấy khám bệnh',
              controller: _commentCtrl,
              maxLength: RequestReviewController.commentMax,
              minLines: 3,
              maxLines: 6,
              keyboardType: TextInputType.multiline,
              errorText: _controller.commentError,
              onChanged: (_) => _controller.clearCommentError(),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: SizedBox(
                    height: 52,
                    child: OutlinedButton.icon(
                      onPressed: _controller.isReviewing
                          ? null
                          : () => _review(approve: false),
                      icon: const Icon(Icons.close),
                      label: const Text('Từ chối'),
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
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: _controller.isReviewing
                        ? null
                        : () => _review(approve: true),
                    icon: const Icon(Icons.check),
                    label: const Text('Duyệt'),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    ];
  }
}
