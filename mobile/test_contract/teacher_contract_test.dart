import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/teacher_service.dart';

import 'auth_contract_test.dart' show ContractClient, apiError, kSeedPassword;

/// Hợp đồng lịch dạy, lịch thi, lớp và bảng điểm danh của giáo viên (giai
/// đoạn 10d-1, 10d-2), chỉ đọc; không lưu điểm danh vì làm đổi dữ liệu dev:
/// 0900000003 là giáo viên tổ Toán – Tin học, 0900000002 là tổ trưởng.
///
/// Chạy: `flutter test test_contract` khi backend đang chạy ở cổng 8080.
void main() {
  Future<StudentService> signIn(String phone) async {
    final client = ContractClient();
    await client.auth.login(phone, kSeedPassword);
    return StudentService(client.api);
  }

  test('a teacher reads the teaching week, sessions and exams', () async {
    for (final phone in ['0900000003', '0900000002']) {
      final service = await signIn(phone);

      final week = await service.fetchWeek(null, teacher: true);
      final sessions = await service.fetchSessions(null, teacher: true);
      final exams = await service.fetchExams(null, teacher: true);

      expect(week.data.days, hasLength(7));
      expect(week.semester.id, sessions.semester.id);
      for (final item in sessions.data.items) {
        expect(item.sectionCode, isNotEmpty);
        expect(item.date, matches(r'^\d{4}-\d{2}-\d{2}$'));
      }
      expect(exams.data.totalElements, greaterThanOrEqualTo(0));
    }
  });

  test('a student cannot read the teacher schedule', () async {
    final service = await signIn('0900000004');

    await expectLater(
      service.fetchWeek(null, teacher: true),
      throwsA(apiError('FORBIDDEN')),
    );
  });

  test('a teacher reads sections, a section and a session sheet', () async {
    final client = ContractClient();
    await client.auth.login('0900000003', kSeedPassword);
    final service = TeacherService(client.api);

    final sections = await service.fetchSections();

    expect(sections.data.items, isNotEmpty);
    final section = sections.data.items.first;
    final detail = await service.fetchSectionAttendance(section.id);
    expect(detail.section.id, section.id);
    expect(detail.sessions.totalElements, detail.totalSessions);
    final session = detail.sessions.items.firstWhere(
      (s) => !s.isCancelled,
      orElse: () => detail.sessions.items.first,
    );
    if (!session.isCancelled) {
      final sheet = await service.fetchSheet(section.id, session.id);
      expect(sheet.sessionId, session.id);
      expect(sheet.marked, session.hasAttendance);
      for (final row in sheet.rows) {
        expect(row.studentCode, isNotEmpty);
        if (sheet.marked) {
          expect(row.status, isNotNull);
        }
      }
    }
  });

  test('a teacher cannot open a section taught by someone else', () async {
    final client = ContractClient();
    await client.auth.login('0900000003', kSeedPassword);

    await expectLater(
      TeacherService(client.api).fetchSectionAttendance(27),
      throwsA(apiError('FORBIDDEN')),
    );
  });
}
