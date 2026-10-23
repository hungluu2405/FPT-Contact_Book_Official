import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';

import '../support/fakes.dart';

void main() {
  test('semesters come from GET /semesters', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/semesters', FakeReply(200, semestersJson()));

    final semesters = await StudentService(app.apiClient).fetchSemesters();

    expect(semesters.map((s) => s.code), ['HK2-2627', 'HK1-2627', 'HE-2026']);
  });

  test('grades send semesterId only when chosen', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/student/grades',
      FakeReply(200, gradesJson([gradeRowJson()])),
    );
    app.http.reply(
      'GET',
      '/parent/children/7/grades',
      FakeReply(200, gradesJson([], semesterId: 38)),
    );
    final service = StudentService(app.apiClient);

    final current = await service.fetchGrades(null);
    final chosen = await service.fetchGrades(7, semesterId: 38);

    expect(current.data.single.subjectName, 'Toán 10');
    expect(chosen.semester.id, 38);
    expect(app.http.requests.first.queryParameters, isEmpty);
    expect(app.http.requests.last.queryParameters, {'semesterId': 38});
  });

  test('attendance overview and history use the student root', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/parent/children/7/attendance',
      FakeReply(200, attendanceJson([attendanceRowJson(sectionId: 27)])),
    );
    app.http.reply(
      'GET',
      '/parent/children/7/attendance/27',
      FakeReply(200, attendanceHistoryJson([attendanceSessionJson()])),
    );
    final service = StudentService(app.apiClient);

    final overview = await service.fetchAttendance(7, semesterId: 1);
    final history = await service.fetchAttendanceHistory(7, 27, page: 2);

    expect(overview.sections.single.sectionId, 27);
    expect(history.sessions.items, hasLength(1));
    expect(app.http.requests.first.queryParameters, {'semesterId': 1});
    expect(app.http.requests.last.queryParameters, {'page': 2});
  });
}
