import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/user_labels.dart';

/// Nhãn mức độ thông báo: "Khẩn" đỏ, "Quan trọng" cam.
class PriorityBadgeWidget extends StatelessWidget {
  final String priority;

  const PriorityBadgeWidget({super.key, required this.priority});

  /// Thông báo thường không có nhãn nên trả null.
  static Widget? of(String priority) => priority == NotificationPriority.normal
      ? null
      : PriorityBadgeWidget(priority: priority);

  @override
  Widget build(BuildContext context) {
    return StatusBadgeWidget(
      label: kNotificationPriorityLabels[priority] ?? priority,
      tone: priority == NotificationPriority.urgent
          ? BadgeTone.danger
          : BadgeTone.orange,
    );
  }
}
