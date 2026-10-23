import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/request_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/user_labels.dart';

/// Nhãn trạng thái đơn hoặc quyết định duyệt: Chờ duyệt (cam), Đã duyệt
/// (xanh lá), Từ chối (đỏ), Đã hủy (xám).
class RequestStatusBadgeWidget extends StatelessWidget {
  final String status;

  const RequestStatusBadgeWidget({super.key, required this.status});

  @override
  Widget build(BuildContext context) {
    return StatusBadgeWidget(
      label: kRequestStatusLabels[status] ?? status,
      tone: switch (status) {
        RequestStatus.approved => BadgeTone.success,
        RequestStatus.rejected => BadgeTone.danger,
        RequestStatus.cancelled => BadgeTone.muted,
        _ => BadgeTone.orange,
      },
    );
  }
}
