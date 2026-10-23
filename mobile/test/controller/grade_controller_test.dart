import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import '../support/fakes.dart';

void main() {
  test('opens on the current semester with the semester list', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/semesters', FakeReply(200, semestersJson()));
    app.http.reply(
      'GET',
      '/student/grades',
      FakeReply(200, gradesJson([gradeRowJson()])),
    );

    await app.gradeController.open(null);

    expect(app.gradeController.semesters.map((s) => s.id), [38, 1, 37]);
    expect(app.gradeController.selectedSemesterId, 1);
    expect(app.gradeController.semester!.name, 'Học kỳ I năm học 2026–2027');
    expect(app.gradeController.rows.single.average, 8.3);
    expect(
      app.http.requests
          .firstWhere((r) => r.path == '/student/grades')
          .queryParameters,
      isEmpty,
    );
  });

  test('selecting a semester loads its grades', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/semesters', FakeReply(200, semestersJson()));
    app.http.on('GET', '/parent/children/7/grades', (request) async {
      return request.queryParameters['semesterId'] == 38
          ? FakeReply(200, gradesJson([], semesterId: 38))
          : FakeReply(200, gradesJson([gradeRowJson()]));
    });
    await app.gradeController.open(7);

    await app.gradeController.selectSemester(38);

    expect(app.gradeController.selectedSemesterId, 38);
    expect(app.gradeController.rows, isEmpty);
    expect(app.http.requests.last.queryParameters, {'semesterId': 38});
  });

  test('a slow reply of the previous semester is dropped', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    final slow = Completer<FakeReply>();
    app.http.reply('GET', '/semesters', FakeReply(200, semestersJson()));
    app.http.on('GET', '/student/grades', (request) {
      return request.queryParameters['semesterId'] == 37
          ? slow.future
          : Future.value(FakeReply(200, gradesJson([gradeRowJson()])));
    });
    await app.gradeController.open(null);

    final old = app.gradeController.selectSemester(37);
    await app.gradeController.selectSemester(1);
    slow.complete(FakeReply(200, gradesJson([])));
    await old;

    expect(app.gradeController.selectedSemesterId, 1);
    expect(app.gradeController.rows, hasLength(1));
    expect(app.gradeController.isLoading, isFalse);
  });

  test('semester list failing still shows the grades', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.offline('GET', '/semesters');
    app.http.reply(
      'GET',
      '/student/grades',
      FakeReply(200, gradesJson([gradeRowJson()])),
    );

    await app.gradeController.open(null);

    expect(app.gradeController.semesters, isEmpty);
    expect(app.gradeController.rows, hasLength(1));
    expect(app.gradeController.error, isNull);
  });

  test('no semester is empty, network error can be retried', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/semesters', const FakeReply(200, []));
    app.http.reply(
      'GET',
      '/student/grades',
      FakeReply.error(404, 'NOT_FOUND', 'Không tìm thấy học kỳ'),
    );
    await app.gradeController.open(null);
    expect(app.gradeController.hasNoSemester, isTrue);

    app.http.offline('GET', '/student/grades');
    await app.gradeController.reload();
    expect(app.gradeController.error!.isNetworkError, isTrue);
    expect(app.gradeController.hasNoSemester, isFalse);

    app.http.reply('GET', '/student/grades', FakeReply(200, gradesJson([])));
    await app.gradeController.reload();
    expect(app.gradeController.error, isNull);
  });
}
