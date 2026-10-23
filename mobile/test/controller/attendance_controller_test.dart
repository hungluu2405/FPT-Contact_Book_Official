import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import '../support/fakes.dart';

void main() {
  group('overview', () {
    test('opens on the current semester and counts warnings', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply('GET', '/semesters', FakeReply(200, semestersJson()));
      app.http.reply(
        'GET',
        '/student/attendance',
        FakeReply(
          200,
          attendanceJson([
            attendanceRowJson(),
            attendanceRowJson(
              sectionId: 27,
              subjectCode: 'TIN11',
              summary: summaryJson(warning: true, absentRate: 26.7),
            ),
          ]),
        ),
      );

      await app.attendanceController.open(null);

      final overview = app.attendanceController.overview!;
      expect(overview.thresholdPercent, 20);
      expect(overview.sections, hasLength(2));
      expect(app.attendanceController.warningCount, 1);
      expect(app.attendanceController.selectedSemesterId, 1);
    });

    test('selecting a semester sends its id and drops slow replies', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final slow = Completer<FakeReply>();
      app.http.reply('GET', '/semesters', FakeReply(200, semestersJson()));
      app.http.on('GET', '/parent/children/7/attendance', (request) {
        final id = request.queryParameters['semesterId'];
        if (id == 37) {
          return slow.future;
        }
        return Future.value(
          FakeReply(
            200,
            id == 38
                ? attendanceJson([], semesterId: 38)
                : attendanceJson([attendanceRowJson()]),
          ),
        );
      });
      await app.attendanceController.open(7);

      final old = app.attendanceController.selectSemester(37);
      await app.attendanceController.selectSemester(38);
      slow.complete(FakeReply(200, attendanceJson([attendanceRowJson()])));
      await old;

      expect(app.attendanceController.selectedSemesterId, 38);
      expect(app.attendanceController.overview!.sections, isEmpty);
    });

    test('no semester is empty, not an error', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply('GET', '/semesters', const FakeReply(200, []));
      app.http.reply(
        'GET',
        '/student/attendance',
        FakeReply.error(404, 'NOT_FOUND', 'Không tìm thấy học kỳ'),
      );

      await app.attendanceController.open(null);

      expect(app.attendanceController.hasNoSemester, isTrue);
    });
  });

  group('history', () {
    test('loads sessions page by page for the chosen child', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply('GET', '/semesters', FakeReply(200, semestersJson()));
      app.http.reply(
        'GET',
        '/parent/children/7/attendance',
        FakeReply(200, attendanceJson([attendanceRowJson(sectionId: 27)])),
      );
      app.http.on('GET', '/parent/children/7/attendance/27', (request) async {
        final page = request.queryParameters['page'] as int;
        return FakeReply(
          200,
          attendanceHistoryJson(
            [attendanceSessionJson(no: page + 1)],
            page: page,
            totalPages: 2,
            row: attendanceRowJson(sectionId: 27),
          ),
        );
      });
      await app.attendanceController.open(7);

      await app.attendanceController.openSection(27);
      await app.attendanceController.loadMoreSessions();
      await app.attendanceController.loadMoreSessions();

      expect(app.attendanceController.sessions.map((s) => s.sessionNo), [1, 2]);
      expect(app.attendanceController.sessionsHasMore, isFalse);
      expect(app.attendanceController.section!.sectionId, 27);
      expect(app.http.count('GET', '/parent/children/7/attendance/27'), 2);
    });

    test('opening another section drops the slow first reply', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final slow = Completer<FakeReply>();
      app.http.on('GET', '/student/attendance/1', (_) => slow.future);
      app.http.reply(
        'GET',
        '/student/attendance/27',
        FakeReply(
          200,
          attendanceHistoryJson([
            attendanceSessionJson(no: 9),
          ], row: attendanceRowJson(sectionId: 27)),
        ),
      );

      final first = app.attendanceController.openSection(1);
      await app.attendanceController.openSection(27);
      slow.complete(FakeReply(200, attendanceHistoryJson([])));
      await first;

      expect(app.attendanceController.section!.sectionId, 27);
      expect(app.attendanceController.sessions.single.sessionNo, 9);
    });

    test('error keeps loaded sessions and can be retried', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.offline('GET', '/student/attendance/1');

      await app.attendanceController.openSection(1);

      expect(app.attendanceController.sessionsError!.isNetworkError, isTrue);
      expect(app.attendanceController.isLoadingSessions, isFalse);

      app.http.reply(
        'GET',
        '/student/attendance/1',
        FakeReply(200, attendanceHistoryJson([attendanceSessionJson()])),
      );
      await app.attendanceController.loadMoreSessions();

      expect(app.attendanceController.sessions, hasLength(1));
      expect(app.attendanceController.sessionsError, isNull);
    });
  });
}
