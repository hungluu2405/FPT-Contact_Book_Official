import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attendance_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

import 'auth_contract_test.dart' show ContractClient, apiError, kSeedPassword;

/// Hợp đồng điểm và điểm danh (giai đoạn 10b-3), chỉ đọc dữ liệu mẫu.
///
/// Chạy: `flutter test test_contract` khi backend đang chạy ở cổng 8080.
void main() {
  Future<StudentService> signIn(String phone) async {
    final client = ContractClient();
    await client.auth.login(phone, kSeedPassword);
    return StudentService(client.api);
  }

  Future<int> seedChildId(StudentService parent) async {
    final children = await parent.fetchChildren();
    return children.firstWhere((child) => child.code == 'HS260001').id;
  }

  const statuses = {
    AttendanceStatus.present,
    AttendanceStatus.absentExcused,
    AttendanceStatus.absentUnexcused,
  };

  test('semester list includes the current semester', () async {
    final service = await signIn('0900000004');

    final semesters = await service.fetchSemesters();
    final grades = await service.fetchGrades(null);

    expect(semesters, isNotEmpty);
    expect(semesters.map((s) => s.id), contains(grades.semester.id));
  });

  test('grades have valid cells and averages on a 0-10 scale', () async {
    final service = await signIn('0900000004');

    final grades = await service.fetchGrades(null);

    for (final row in grades.data) {
      expect(row.subjectCode, isNotEmpty);
      for (final cell in row.cells) {
        expect(cell.componentName, isNotEmpty);
        expect(cell.maxScore, greaterThan(0));
        if (cell.score != null) {
          expect(cell.score, inInclusiveRange(0, cell.maxScore));
        }
      }
      if (row.average != null) {
        expect(row.average, inInclusiveRange(0, 10));
      }
    }
  });

  test('choosing a semester returns that semester', () async {
    final service = await signIn('0900000004');
    final semesters = await service.fetchSemesters();

    for (final semester in semesters) {
      final grades = await service.fetchGrades(null, semesterId: semester.id);
      final attendance = await service.fetchAttendance(
        null,
        semesterId: semester.id,
      );

      expect(grades.semester.id, semester.id);
      expect(attendance.semester.id, semester.id);
    }
  });

  test('parent reads attendance and history of the child', () async {
    final service = await signIn('0900000005');
    final childId = await seedChildId(service);

    final overview = await service.fetchAttendance(childId);

    expect(overview.thresholdPercent, inInclusiveRange(0, 100));
    for (final row in overview.sections) {
      final summary = row.summary;
      expect(summary.marked, lessThanOrEqualTo(summary.totalSessions));
      final history = await service.fetchAttendanceHistory(
        childId,
        row.sectionId,
      );
      expect(history.section.sectionId, row.sectionId);
      for (final session in history.sessions.items) {
        expect(parseApiDate(session.date), isNotNull);
        expect(
          session.status == null || statuses.contains(session.status),
          isTrue,
        );
      }
    }
  });

  test('student sees the same attendance as the parent of HS260001', () async {
    final student = await signIn('0900000004');
    final parent = await signIn('0900000005');
    final childId = await seedChildId(parent);

    final own = await student.fetchAttendance(null);
    final child = await parent.fetchAttendance(childId);

    expect(
      own.sections.map((row) => row.sectionId),
      child.sections.map((row) => row.sectionId),
    );
  });

  test('parent cannot read grades of an unlinked student', () async {
    final service = await signIn('0900000005');

    expect(service.fetchGrades(999999), throwsA(apiError('FORBIDDEN')));
  });
}
