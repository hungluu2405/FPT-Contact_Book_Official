import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import '../support/fakes.dart';

void main() {
  test('loads exams page by page and counts every exam', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.on('GET', '/student/exams', (request) async {
      final page = request.queryParameters['page'] as int;
      return FakeReply(
        200,
        pagedJson(
          [examJson(id: page + 1)],
          page: page,
          totalPages: 2,
          totalElements: 2,
        ),
      );
    });

    await app.examController.open(null);

    expect(app.examController.total, 2);
    expect(app.examController.hasMore, isTrue);

    await app.examController.loadMore();
    await app.examController.loadMore();

    expect(app.examController.exams.map((exam) => exam.id), [1, 2]);
    expect(app.examController.hasMore, isFalse);
    expect(app.http.count('GET', '/student/exams'), 2);
  });

  test('next exam is the first one from today on', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/parent/children/7/exams',
      FakeReply(
        200,
        pagedJson([
          examJson(id: 1, date: '2026-10-20'),
          examJson(id: 2, date: '2026-10-21'),
          examJson(id: 3, date: '2026-11-02'),
        ]),
      ),
    );

    await app.examController.open(7);

    final exams = app.examController.exams;
    expect(app.examController.isPast(exams[0]), isTrue);
    expect(app.examController.isPast(exams[1]), isFalse);
    expect(app.examController.nextExam!.id, 2);
  });

  test('no exam ahead has no next exam', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/student/exams',
      FakeReply(200, pagedJson([examJson(date: '2026-09-01')])),
    );

    await app.examController.open(null);

    expect(app.examController.nextExam, isNull);
  });

  test('no semester is not shown as an error', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/student/exams',
      FakeReply.error(404, 'NOT_FOUND', 'Không tìm thấy học kỳ'),
    );

    await app.examController.open(null);

    expect(app.examController.hasNoSemester, isTrue);
    expect(app.examController.hasLoaded, isFalse);
  });

  test('reopening for another child drops the slow first reply', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    final slow = Completer<FakeReply>();
    app.http.on('GET', '/parent/children/1/exams', (_) => slow.future);
    app.http.reply(
      'GET',
      '/parent/children/2/exams',
      FakeReply(200, pagedJson([examJson(id: 20)])),
    );

    final first = app.examController.open(1);
    await app.examController.open(2);
    slow.complete(FakeReply(200, pagedJson([examJson(id: 10)])));
    await first;

    expect(app.examController.exams.map((exam) => exam.id), [20]);
  });
}
