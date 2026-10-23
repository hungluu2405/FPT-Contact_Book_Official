import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

import 'auth_contract_test.dart' show ContractClient, apiError, kSeedPassword;

/// Hợp đồng lịch học và lịch thi (giai đoạn 10b-2), chỉ đọc dữ liệu mẫu.
///
/// Chạy: `flutter test test_contract` khi backend đang chạy ở cổng 8080.
void main() {
  Future<StudentService> signIn(String phone) async {
    final client = ContractClient();
    await client.auth.login(phone, kSeedPassword);
    return StudentService(client.api);
  }

  /// `students.id` của HS260001 theo danh sách con của phụ huynh mẫu.
  Future<int> seedChildId(StudentService parent) async {
    final children = await parent.fetchChildren();
    return children.firstWhere((child) => child.code == 'HS260001').id;
  }

  test('student week has seven days from Monday with valid fields', () async {
    final service = await signIn('0900000004');

    final result = await service.fetchWeek(null);

    final week = result.data;
    expect(result.semester.name, isNotEmpty);
    expect(week.days, hasLength(7));
    expect(parseApiDate(week.weekStart)!.weekday, DateTime.monday);
    expect(week.days.where((day) => day.today).length, lessThanOrEqualTo(1));
    for (final item in week.days.expand((day) => day.items)) {
      expect(item.subjectCode, isNotEmpty);
      expect(minutesOfDay(item.startTime), isNotNull);
      expect(minutesOfDay(item.endTime), isNotNull);
    }
  });

  test('week of a given date returns that week', () async {
    final service = await signIn('0900000004');
    final current = await service.fetchWeek(null);

    final next = await service.fetchWeek(null, date: current.data.next);

    expect(next.data.weekStart, current.data.next);
    expect(next.data.previous, current.data.weekStart);
  });

  test('parent reads the week, sessions and exams of the child', () async {
    final service = await signIn('0900000005');
    final childId = await seedChildId(service);

    final week = await service.fetchWeek(childId);
    final sessions = await service.fetchSessions(childId);
    final exams = await service.fetchExams(childId);

    expect(week.data.days, hasLength(7));
    expect(sessions.data.page, 0);
    expect(sessions.data.items.length, lessThanOrEqualTo(sessions.data.size));
    expect(
      exams.data.totalElements,
      greaterThanOrEqualTo(exams.data.items.length),
    );
    for (final exam in exams.data.items) {
      expect(['MIDTERM', 'FINAL', 'RETAKE'], contains(exam.examType));
      expect(parseApiDate(exam.date), isNotNull);
    }
  });

  test('student sees the same sessions as the parent of HS260001', () async {
    final student = await signIn('0900000004');
    final parent = await signIn('0900000005');
    final childId = await seedChildId(parent);

    final own = await student.fetchSessions(null);
    final child = await parent.fetchSessions(childId);

    expect(own.data.totalElements, child.data.totalElements);
  });

  test('parent cannot read a student that is not linked', () async {
    final service = await signIn('0900000005');

    expect(service.fetchWeek(999999), throwsA(apiError('FORBIDDEN')));
  });
}
