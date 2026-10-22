import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/notification_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/profile_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';

import 'auth_contract_test.dart' show ContractClient, apiError, kSeedPassword;

/// Hợp đồng các API của trang chủ (giai đoạn 10b), chỉ đọc dữ liệu mẫu.
///
/// Chạy: `flutter test test_contract` khi backend đang chạy ở cổng 8080.
void main() {
  Future<ContractClient> signIn(String phone) async {
    final client = ContractClient();
    await client.auth.login(phone, kSeedPassword);
    return client;
  }

  test('student reads profile and unread count', () async {
    final client = await signIn('0900000004');

    final profile = await ProfileService(client.api).fetchProfile();
    final unread = await NotificationService(client.api).fetchUnreadCount();

    expect(profile.phone, '0900000004');
    expect(profile.fullName, isNotEmpty);
    expect(
      profile.roles.map((role) => role.roleCode),
      contains(RoleCodes.student),
    );
    expect(unread, greaterThanOrEqualTo(0));
  });

  test('parent sees the linked child SE190001', () async {
    final client = await signIn('0900000005');

    final children = await StudentService(client.api).fetchChildren();

    expect(children.map((child) => child.code), contains('SE190001'));
    expect(children.every((child) => child.id > 0), isTrue);
    expect(children.every((child) => child.name.isNotEmpty), isTrue);
  });

  test('student cannot list children of a parent', () async {
    final client = await signIn('0900000004');

    expect(
      StudentService(client.api).fetchChildren(),
      throwsA(apiError('FORBIDDEN')),
    );
  });
}
