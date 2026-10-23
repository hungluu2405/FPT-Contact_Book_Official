import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/schedule/list_exam_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/schedule/list_session_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/schedule/view_schedule_screen.dart';

import 'support/app_driver.dart';
import 'support/fakes.dart';

/// SV đã đăng nhập; [today] là các buổi hôm nay (thứ Tư 21/10/2026).
Future<TestApp> pumpStudent(
  WidgetTester tester, {
  List<Map<String, dynamic>> today = const [],
  List<String> roles = const ['STUDENT'],
  List<Map<String, dynamic>> children = const [],
}) async {
  final app = TestApp(accessToken: 'a', refreshToken: 'r');
  app.http.reply('GET', '/me', FakeReply(200, userJson(roles: roles)));
  app.stubHome(today: today, children: children);
  await pumpApp(tester, app: app);
  return app;
}

Future<void> tapText(WidgetTester tester, String text) async {
  await tester.ensureVisible(find.text(text).first);
  await tester.tap(find.text(text).first);
  await tester.pumpAndSettle();
}

final List<Map<String, dynamic>> kToday = [
  sessionJson(),
  sessionJson(
    start: '09:30:00',
    end: '11:00:00',
    subjectCode: 'TIN11',
    room: 'BE202',
    teacher: 'TungVQ',
  ),
  sessionJson(
    start: '13:00:00',
    end: '14:30:00',
    subjectCode: 'ANH10',
    teacher: 'HaTH',
    status: 'CANCELLED',
  ),
];

void main() {
  testWidgets('home shows today sessions in its card', (tester) async {
    await pumpStudent(tester, today: kToday);

    expect(find.text('Lịch học hôm nay'), findsOneWidget);
    expect(find.text('07:30–09:20'), findsOneWidget);
    expect(find.text('TIN11'), findsOneWidget);
  });

  testWidgets('home card says when there is no session today', (tester) async {
    await pumpStudent(tester);

    expect(find.text('Hôm nay không có buổi học nào.'), findsOneWidget);
  });

  testWidgets('schedule tile opens today with the ongoing session', (
    tester,
  ) async {
    await pumpStudent(tester, today: kToday);

    await tapText(tester, 'Lịch học');

    expect(find.byType(ViewScheduleScreen), findsOneWidget);
    expect(find.text('Hôm nay, 21 tháng 10'), findsOneWidget);
    expect(find.text('Thứ Tư · Học kỳ I năm học 2026–2027'), findsOneWidget);
    expect(find.text('Lịch học hôm nay'), findsOneWidget);
    expect(find.text('Đang diễn ra'), findsOneWidget);
    expect(find.text('Đã hủy'), findsOneWidget);
  });

  testWidgets('picking another day shows its sessions', (tester) async {
    await pumpStudent(tester, today: kToday);
    await tapText(tester, 'Lịch học');

    await tester.tap(find.text('22'));
    await tester.pumpAndSettle();

    expect(find.text('Thứ Năm, 22 tháng 10'), findsOneWidget);
    expect(find.text('Lịch học thứ năm, 22/10'), findsOneWidget);
    expect(find.text('Không có lịch học trong ngày này.'), findsOneWidget);
  });

  testWidgets('next week arrow loads it and offers back to today', (
    tester,
  ) async {
    final app = await pumpStudent(tester, today: kToday);
    await tapText(tester, 'Lịch học');
    app.http.reply(
      'GET',
      '/student/schedule/week',
      FakeReply(200, weekJson(weekStart: '2026-10-26', today: null)),
    );

    await tester.tap(find.byTooltip('Tuần sau'));
    await tester.pumpAndSettle();

    expect(find.text('26'), findsOneWidget);
    expect(find.widgetWithText(TextButton, 'Hôm nay'), findsOneWidget);
  });

  testWidgets('see all opens the semester list', (tester) async {
    final app = await pumpStudent(tester, today: kToday);
    app.http.reply(
      'GET',
      '/student/schedule/sessions',
      FakeReply(
        200,
        pagedJson([
          sessionJson(),
          sessionJson(date: '2026-10-22', subjectCode: 'VAN10'),
        ]),
      ),
    );
    await tapText(tester, 'Lịch học');

    await tapText(tester, 'Xem lịch');

    expect(find.byType(ListSessionScreen), findsOneWidget);
    expect(find.text('Thứ Tư, 21/10/2026'), findsOneWidget);
    expect(find.text('VAN10'), findsOneWidget);
  });

  testWidgets('exam tile lists exams with the next one marked', (tester) async {
    final app = await pumpStudent(tester);
    app.http.reply(
      'GET',
      '/student/exams',
      FakeReply(
        200,
        pagedJson([
          examJson(id: 1, date: '2026-10-10', subjectCode: 'TOAN10'),
          examJson(id: 2, date: '2026-10-30', type: 'FINAL'),
        ]),
      ),
    );

    await tapText(tester, 'Lịch thi');

    expect(find.byType(ListExamScreen), findsOneWidget);
    expect(find.text('2 lịch thi trong học kỳ'), findsOneWidget);
    expect(find.text('Đã thi'), findsOneWidget);
    expect(find.text('Sắp tới'), findsOneWidget);
    expect(find.text('Cuối kỳ'), findsOneWidget);
  });

  testWidgets('parent opens the schedule of the selected child', (
    tester,
  ) async {
    final app = await pumpStudent(
      tester,
      roles: ['PARENT'],
      children: [childJson(id: 7, name: 'Lê Minh Sinh')],
    );

    await tapText(tester, 'Lịch học');

    expect(
      app.http.requests.where(
        (request) => request.path == '/parent/children/7/schedule/week',
      ),
      hasLength(2),
    );
    expect(find.text('S'), findsOneWidget);
  });

  testWidgets('schedule error offers a retry', (tester) async {
    final app = await pumpStudent(tester);
    app.http.offline('GET', '/student/schedule/week');

    await tapText(tester, 'Lịch học');
    expect(find.textContaining('Không kết nối được máy chủ'), findsOneWidget);

    app.http.reply('GET', '/student/schedule/week', FakeReply(200, weekJson()));
    await tapText(tester, 'Thử lại');

    expect(find.text('Hôm nay, 21 tháng 10'), findsOneWidget);
  });
}
