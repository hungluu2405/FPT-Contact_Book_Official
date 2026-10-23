import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/schedule_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/schedule_item_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';

import '../support/fakes.dart';

ScheduleController controllerAt(TestApp app, DateTime now) =>
    ScheduleController(StudentService(app.apiClient), clock: () => now);

void main() {
  group('week', () {
    test('opens on the current week with today selected', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/student/schedule/week',
        FakeReply(
          200,
          weekJson(
            items: {
              '2026-10-21': [sessionJson()],
            },
          ),
        ),
      );

      await app.scheduleController.open(null);

      expect(app.scheduleController.selectedDate, '2026-10-21');
      expect(app.scheduleController.selectedDay!.items, hasLength(1));
      expect(app.scheduleController.isCurrentWeek, isTrue);
      expect(
        app.scheduleController.semester!.name,
        'Học kỳ I năm học 2026–2027',
      );
    });

    test('another week selects the first day with sessions', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.on('GET', '/student/schedule/week', (request) async {
        final date = request.queryParameters['date'];
        return date == null
            ? FakeReply(200, weekJson())
            : FakeReply(
                200,
                weekJson(
                  weekStart: '2026-10-26',
                  today: null,
                  items: {
                    '2026-10-28': [sessionJson(date: '2026-10-28')],
                  },
                ),
              );
      });
      await app.scheduleController.open(null);

      await app.scheduleController.nextWeek();

      expect(app.http.requests.last.queryParameters, {'date': '2026-10-26'});
      expect(app.scheduleController.isCurrentWeek, isFalse);
      expect(app.scheduleController.selectedDate, '2026-10-28');

      await app.scheduleController.goToday();

      expect(app.http.requests.last.queryParameters, isEmpty);
      expect(app.scheduleController.selectedDate, '2026-10-21');
    });

    test('a week without sessions outside today selects Monday', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/student/schedule/week',
        FakeReply(200, weekJson(today: null)),
      );

      await app.scheduleController.open(null);

      expect(app.scheduleController.selectedDate, '2026-10-19');
    });

    test('reload keeps the selected day of the same week', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/student/schedule/week',
        FakeReply(200, weekJson()),
      );
      await app.scheduleController.open(null);
      app.scheduleController.selectDate('2026-10-23');

      await app.scheduleController.reload();

      expect(app.scheduleController.selectedDate, '2026-10-23');
    });

    test('parent loads the week of the chosen child', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/parent/children/7/schedule/week',
        FakeReply(200, weekJson()),
      );

      await app.scheduleController.open(7);

      expect(app.http.requests.single.path, '/parent/children/7/schedule/week');
    });

    test('a slow reply of the previous week is dropped', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final slow = Completer<FakeReply>();
      app.http.on('GET', '/student/schedule/week', (request) {
        return request.queryParameters['date'] == '2026-10-12'
            ? slow.future
            : Future.value(FakeReply(200, weekJson()));
      });
      await app.scheduleController.open(null);

      final previous = app.scheduleController.previousWeek();
      await app.scheduleController.goToday();
      slow.complete(
        FakeReply(200, weekJson(weekStart: '2026-10-12', today: null)),
      );
      await previous;

      expect(app.scheduleController.week!.weekStart, '2026-10-19');
      expect(app.scheduleController.isLoading, isFalse);
    });

    test('no semester is shown as empty, not as an error', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/student/schedule/week',
        FakeReply.error(404, 'NOT_FOUND', 'Không tìm thấy học kỳ'),
      );

      await app.scheduleController.open(null);

      expect(app.scheduleController.hasNoSemester, isTrue);
    });

    test('network error can be retried', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.offline('GET', '/student/schedule/week');
      await app.scheduleController.open(null);

      expect(app.scheduleController.error!.isNetworkError, isTrue);
      expect(app.scheduleController.hasNoSemester, isFalse);

      app.http.reply(
        'GET',
        '/student/schedule/week',
        FakeReply(200, weekJson()),
      );
      await app.scheduleController.reload();

      expect(app.scheduleController.error, isNull);
      expect(app.scheduleController.week, isNotNull);
    });
  });

  group('isOngoing', () {
    Future<ScheduleController> opened(TestApp app, DateTime now) async {
      app.http.reply(
        'GET',
        '/student/schedule/week',
        FakeReply(
          200,
          weekJson(
            items: {
              '2026-10-21': [
                sessionJson(start: '09:30:00', end: '11:00:00'),
                sessionJson(
                  start: '09:30:00',
                  end: '11:00:00',
                  status: SessionStatus.cancelled,
                ),
              ],
              '2026-10-22': [
                sessionJson(date: '2026-10-22', start: '09:30:00'),
              ],
            },
          ),
        ),
      );
      final controller = controllerAt(app, now);
      await controller.open(null);
      return controller;
    }

    test('is true from start time until before end time', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final at = DateTime(2026, 10, 21, 9, 30);
      final controller = await opened(app, at);
      final today = controller.week!.days[2];

      expect(controller.isOngoing(today, today.items.first), isTrue);
    });

    test('is false at the end time, for cancelled or other days', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final controller = await opened(app, DateTime(2026, 10, 21, 11, 0));
      final today = controller.week!.days[2];
      final tomorrow = controller.week!.days[3];

      expect(controller.isOngoing(today, today.items.first), isFalse);

      final during = await opened(
        TestApp(accessToken: 'a', refreshToken: 'r'),
        DateTime(2026, 10, 21, 10, 0),
      );
      final duringToday = during.week!.days[2];
      expect(during.isOngoing(duringToday, duringToday.items.last), isFalse);
      expect(during.isOngoing(tomorrow, tomorrow.items.first), isFalse);
    });
  });

  group('sessions list', () {
    test('loads page by page until the last page', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.on('GET', '/student/schedule/sessions', (request) async {
        final page = request.queryParameters['page'] as int;
        return FakeReply(
          200,
          pagedJson(
            [sessionJson(subjectCode: 'S$page')],
            page: page,
            totalPages: 2,
          ),
        );
      });
      await app.scheduleController.reloadSessions();
      await app.scheduleController.loadMoreSessions();
      await app.scheduleController.loadMoreSessions();

      expect(app.scheduleController.sessions.map((item) => item.subjectCode), [
        'S0',
        'S1',
      ]);
      expect(app.scheduleController.sessionsHasMore, isFalse);
      expect(app.http.count('GET', '/student/schedule/sessions'), 2);
    });

    test('error keeps loaded sessions for a retry', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.offline('GET', '/student/schedule/sessions');

      await app.scheduleController.reloadSessions();

      expect(app.scheduleController.sessionsError!.isNetworkError, isTrue);
      expect(app.scheduleController.isLoadingSessions, isFalse);

      app.http.reply(
        'GET',
        '/student/schedule/sessions',
        FakeReply(200, pagedJson([sessionJson()])),
      );
      await app.scheduleController.loadMoreSessions();

      expect(app.scheduleController.sessions, hasLength(1));
      expect(app.scheduleController.sessionsError, isNull);
    });
  });
}
