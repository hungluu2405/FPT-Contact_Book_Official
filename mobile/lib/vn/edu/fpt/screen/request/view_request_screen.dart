import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/request_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/request_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/request_detail_body_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_theme.dart';

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
    return [
      RequestDetailBodyWidget(
        detail: detail,
        openingFileId: _controller.openingFileId,
        onOpenFile: _openFile,
        footer: [
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
    ];
  }
}
