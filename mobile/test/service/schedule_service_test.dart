import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';

import '../support/fakes.dart';

void main() {
  test('student week has no date parameter by default', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/student/schedule/week', FakeReply(200, weekJson()));

    final result = await StudentService(app.apiClient).fetchWeek(null);

    expect(result.data.weekStart, '2026-10-19');
    expect(app.http.requests.single.queryParameters, isEmpty);
  });

  test('parent week of a child sends the date', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/parent/children/7/schedule/week',
      FakeReply(200, weekJson(weekStart: '2026-10-26', today: null)),
    );

    final result = await StudentService(app.apiClient)
        .fetchWeek(7, date: '2026-10-26');

    expect(result.data.todayDay, isNull);
    expect(app.http.requests.single.queryParameters, {'date': '2026-10-26'});
  });

  test('sessions and exams ask for the requested page', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/student/schedule/sessions',
      FakeReply(200, pagedJson([sessionJson()], page: 1, totalPages: 2)),
    );
    app.http.reply(
      'GET',
      '/parent/children/7/exams',
      FakeReply(200, pagedJson([examJson()])),
    );
    final service = StudentService(app.apiClient);

    final sessions = await service.fetchSessions(null, page: 1);
    final exams = await service.fetchExams(7);

    expect(sessions.data.page, 1);
    expect(sessions.data.hasMore, isFalse);
    expect(exams.data.items.single.subjectCode, 'TIN11');
    expect(app.http.requests.first.queryParameters, {'page': 1});
    expect(app.http.requests.last.queryParameters, {'page': 0});
  });
}
