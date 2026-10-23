import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/notification_service.dart';

import 'auth_contract_test.dart' show ContractClient, apiError, kSeedPassword;

/// Hợp đồng hộp thư thông báo (giai đoạn 10c-1), chỉ đọc. Không mở chi tiết
/// và không gọi read-all vì hai API đó đánh dấu đã đọc trong dữ liệu mẫu.
///
/// Chạy: `flutter test test_contract` khi backend đang chạy ở cổng 8080.
void main() {
  Future<NotificationService> signIn(String phone) async {
    final client = ContractClient();
    await client.auth.login(phone, kSeedPassword);
    return NotificationService(client.api);
  }

  test('the inbox page matches the model and the unread count', () async {
    for (final phone in ['0900000004', '0900000005', '0900000003']) {
      final service = await signIn(phone);

      final page = await service.fetchNotifications();
      final unread = await service.fetchUnreadCount();

      expect(page.page, 0);
      expect(unread, greaterThanOrEqualTo(0));
      expect(unread, lessThanOrEqualTo(page.totalElements));
      for (final item in page.items) {
        expect(item.title, isNotEmpty);
        expect(item.createdAt, matches(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}'));
        expect(
          item.priority,
          isIn([
            NotificationPriority.normal,
            NotificationPriority.important,
            NotificationPriority.urgent,
          ]),
        );
      }
      final created = page.items.map((item) => item.createdAt).toList();
      expect(created, [...created]..sort((a, b) => b.compareTo(a)));
    }
  });

  test('a notification of someone else cannot be read', () async {
    final service = await signIn('0900000004');

    await expectLater(
      service.fetchNotification(999999),
      throwsA(apiError('FORBIDDEN').having((e) => e.status, 'status', 403)),
    );
  });
}
