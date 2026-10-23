import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/department_service.dart';

import 'auth_contract_test.dart' show ContractClient, apiError, kSeedPassword;

/// Hợp đồng tổ chuyên môn (giai đoạn 10e-2), chỉ đọc: 0900000002 là tổ
/// trưởng tổ Toán – Tin học (TT) có 4 giáo viên GV001–GV004.
///
/// Chạy: `flutter test test_contract` khi backend đang chạy ở cổng 8080.
void main() {
  Future<DepartmentService> signIn(String phone) async {
    final client = ContractClient();
    await client.auth.login(phone, kSeedPassword);
    return DepartmentService(client.api);
  }

  test('the head lists the department and its teachers', () async {
    final service = await signIn('0900000002');

    final department = (await service.fetchDepartments()).single;
    final page = await service.fetchTeachers(departmentId: department.id);
    final found = await service.fetchTeachers(query: 'GV002');
    final detail = await service.fetchTeacher(found.items.single.userId);

    expect(department.code, 'TT');
    expect(page.totalElements, department.teacherCount);
    expect(
      page.items.map((t) => t.teacherCode),
      containsAll(['GV001', 'GV002', 'GV003', 'GV004']),
    );
    expect(page.items.where((t) => t.head).map((t) => t.teacherCode), [
      'GV001',
    ]);
    expect(detail.teacherCode, 'GV002');
    expect(detail.departmentName, department.name);
    expect(detail.roles, contains('TEACHER'));
  });

  test('a regular teacher cannot use the head API', () async {
    final service = await signIn('0900000003');

    await expectLater(
      service.fetchDepartments(),
      throwsA(apiError('FORBIDDEN')),
    );
  });
}
