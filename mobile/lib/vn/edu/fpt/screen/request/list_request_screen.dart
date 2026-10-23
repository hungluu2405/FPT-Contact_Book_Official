import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/request_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/request_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/request/add_leave_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/request/view_request_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/empty_state_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/request_status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/user_labels.dart';

/// Đơn từ của HS ([studentId] null) hoặc của con PH: mọi loại đơn, mới nhất
/// trước, cuộn để tải thêm; nút "Gửi đơn nghỉ" mở form. Các loại đơn khác
/// chỉ xem và hủy được trên app, gửi trên web.
class ListRequestScreen extends StatefulWidget {
  final RequestController controller;
  final int? studentId;
  final String viewerName;

  const ListRequestScreen({
    super.key,
    required this.controller,
    required this.studentId,
    required this.viewerName,
  });

  @override
  State<ListRequestScreen> createState() => _ListRequestScreenState();
}

class _ListRequestScreenState extends State<ListRequestScreen> {
  RequestController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.open(widget.studentId);
  }

  void _openRequest(int id) {
    _controller.openRequest(id);
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ViewRequestScreen(
          controller: _controller,
          requestId: id,
          viewerName: widget.viewerName,
        ),
      ),
    );
  }

  void _openLeaveForm() {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => AddLeaveScreen(
          controller: _controller,
          viewerName: widget.viewerName,
        ),
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
          final items = _controller.items;
          return NotificationListener<ScrollNotification>(
            onNotification: _onScroll,
            child: RefreshIndicator(
              color: AppColors.orange,
              onRefresh: _controller.reload,
              child: ListView.builder(
                padding: EdgeInsets.zero,
                itemCount: items.length + 2,
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return _header();
                  }
                  if (index == items.length + 1) {
                    return _footer();
                  }
                  final item = items[index - 1];
                  return Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                    child: _RequestCard(
                      item: item,
                      onTap: () => _openRequest(item.id),
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
    final ofChild = widget.studentId != null;
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: PageHeaderWidget(
        icon: Icons.mail_outline,
        title: 'Đơn từ',
        headline: ofChild ? 'Đơn của con' : 'Đơn của tôi',
        subtitle: ofChild
            ? 'Phụ huynh gửi đơn xin nghỉ học thay con.'
            : 'Đơn xin bảng điểm, xác nhận học sinh và đơn khác '
                  'gửi trên trang web của nhà trường.',
        viewerName: widget.viewerName,
        bottom: FilledButton.icon(
          onPressed: _openLeaveForm,
          icon: const Icon(Icons.edit_note),
          label: const Text('Gửi đơn nghỉ'),
        ),
      ),
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
        (_controller.items.isEmpty && _controller.hasMore)) {
      return const Padding(
        padding: EdgeInsets.all(24),
        child: Center(
          child: CircularProgressIndicator(color: AppColors.orange),
        ),
      );
    }
    if (_controller.items.isEmpty) {
      return const EmptyStateWidget(
        icon: Icons.mail_outline,
        message: 'Chưa có đơn nào.',
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

class _RequestCard extends StatelessWidget {
  final RequestModel item;
  final VoidCallback onTap;

  const _RequestCard({required this.item, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final dates = formatApiDateRange(item.fromDate, item.toDate);
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
                    Row(
                      children: [
                        RequestStatusBadgeWidget(status: item.status),
                        const SizedBox(width: 8),
                        Flexible(
                          child: Text(
                            kRequestTypeLabels[item.type] ?? item.type,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: AppColors.orangeDark,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
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
                      dates.isNotEmpty
                          ? 'Nghỉ: $dates'
                          : 'Gửi lúc ${formatApiDateTime(item.createdAt)}',
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
