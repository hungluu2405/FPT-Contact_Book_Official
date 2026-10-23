import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/request_review_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/request_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/teacher/view_review_request_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/empty_state_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/request_status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_theme.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/user_labels.dart';

/// Bộ lọc của hàng đợi duyệt đơn, theo thứ tự hiện.
const List<(String, String)> _filters = [
  (RequestReviewController.pending, 'Chờ duyệt'),
  (RequestStatus.approved, 'Đã duyệt'),
  (RequestStatus.rejected, 'Từ chối'),
  (RequestStatus.cancelled, 'Đã hủy'),
  (RequestReviewController.all, 'Tất cả'),
];

/// Đơn giáo viên phụ trách duyệt: lọc theo trạng thái (mặc định "Chờ duyệt"),
/// cuộn để tải thêm; bấm để xem và duyệt.
class ListReviewRequestScreen extends StatefulWidget {
  final RequestReviewController controller;
  final String viewerName;

  const ListReviewRequestScreen({
    super.key,
    required this.controller,
    required this.viewerName,
  });

  @override
  State<ListReviewRequestScreen> createState() =>
      _ListReviewRequestScreenState();
}

class _ListReviewRequestScreenState extends State<ListReviewRequestScreen> {
  RequestReviewController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    // Ô "Duyệt đơn" trên trang chủ bên dưới cũng nghe controller này.
    WidgetsBinding.instance.addPostFrameCallback((_) => _controller.open());
  }

  void _openRequest(int id) {
    _controller.openRequest(id);
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ViewReviewRequestScreen(
          controller: _controller,
          requestId: id,
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
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 16),
                      child: _header(),
                    );
                  }
                  if (index == items.length + 1) {
                    return _footer();
                  }
                  final item = items[index - 1];
                  return Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                    child: _ReviewCard(
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
    final count = _controller.pendingCount;
    return PageHeaderWidget(
      icon: Icons.fact_check_outlined,
      title: 'Duyệt đơn',
      headline: 'Đơn cần duyệt',
      subtitle: count == null
          ? null
          : (count == 0 ? 'Không có đơn chờ duyệt' : '$count đơn chờ duyệt'),
      viewerName: widget.viewerName,
      bottom: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            for (final (value, label) in _filters)
              Padding(
                padding: const EdgeInsets.only(right: 8),
                child: ChoiceChip(
                  label: Text(label),
                  selected: _controller.filter == value,
                  onSelected: (_) => _controller.setFilter(value),
                  selectedColor: AppColors.orange,
                  backgroundColor: Colors.white,
                  showCheckmark: false,
                  labelStyle: TextStyle(
                    fontFamily: kAppFontFamily,
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: _controller.filter == value
                        ? Colors.white
                        : AppColors.text,
                  ),
                ),
              ),
          ],
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
      return EmptyStateWidget(
        icon: Icons.fact_check_outlined,
        message: _controller.filter == RequestReviewController.pending
            ? 'Không có đơn nào chờ bạn duyệt.'
            : 'Không có đơn nào.',
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

/// Một đơn: trạng thái, loại, học sinh, tiêu đề, khoảng ngày nghỉ.
class _ReviewCard extends StatelessWidget {
  final RequestModel item;
  final VoidCallback onTap;

  const _ReviewCard({required this.item, required this.onTap});

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
                      [
                        item.studentName ?? '',
                        item.studentCode ?? '',
                      ].where((part) => part.isNotEmpty).join(' · '),
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: AppColors.navy,
                      ),
                    ),
                    Text(
                      item.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 14,
                        color: AppColors.text,
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
