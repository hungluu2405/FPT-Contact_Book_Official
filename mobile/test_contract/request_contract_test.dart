import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/request_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/request_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';

import 'auth_contract_test.dart' show ContractClient, apiError, kSeedPassword;

/// Hợp đồng đơn từ (giai đoạn 10c-2), chỉ đọc. Không gửi hay hủy đơn vì làm
/// đổi dữ liệu dev; hai việc đó có test tích hợp ở backend.
///
/// Chạy: `flutter test test_contract` khi backend đang chạy ở cổng 8080.
void main() {
  Future<ContractClient> signIn(String phone) async {
    final client = ContractClient();
    await client.auth.login(phone, kSeedPassword);
    return client;
  }

  const statuses = [
    RequestStatus.pending,
    RequestStatus.approved,
    RequestStatus.rejected,
    RequestStatus.cancelled,
  ];

  test('student lists own requests and opens each of them', () async {
    final service = RequestService((await signIn('0900000004')).api);

    final page = await service.fetchRequests(null);

    expect(page.page, 0);
    for (final item in page.items) {
      expect(item.studentCode, 'HS260001');
      expect(item.status, isIn(statuses));
      if (item.type == RequestType.leave) {
        expect(item.fromDate, matches(r'^\d{4}-\d{2}-\d{2}$'));
      }
      final detail = await service.fetchRequest(null, item.id);
      expect(detail.id, item.id);
      expect(detail.reason, isNotEmpty);
      // Chỉ người gửi hủy được, và chỉ khi đơn còn chờ duyệt.
      if (detail.canCancel) {
        expect(detail.status, RequestStatus.pending);
      }
    }
  });

  test('student and parent get the same leave options of HS260001', () async {
    final student = RequestService((await signIn('0900000004')).api);
    final parentClient = await signIn('0900000005');
    final child = (await StudentService(
      parentClient.api,
    ).fetchChildren()).firstWhere((c) => c.code == 'HS260001');
    final parent = RequestService(parentClient.api);

    final mine = await student.fetchLeaveOptions(null);
    final ofChild = await parent.fetchLeaveOptions(child.id);

    expect(mine.studentCode, 'HS260001');
    expect(ofChild.studentId, mine.studentId);
    expect(ofChild.sections.map((s) => s.id), mine.sections.map((s) => s.id));
    final childRequests = await parent.fetchRequests(child.id);
    final ownRequests = await student.fetchRequests(null);
    expect(childRequests.totalElements, ownRequests.totalElements);
  });

  test('parent cannot read requests of a student that is not linked', () async {
    final parent = RequestService((await signIn('0900000005')).api);

    await expectLater(
      parent.fetchRequests(999999),
      throwsA(apiError('FORBIDDEN')),
    );
  });
}
