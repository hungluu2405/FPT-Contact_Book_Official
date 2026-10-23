import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/attendance/list_attendance_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/attendance/view_attendance_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/grade/view_grade_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/feature_tile_widget.dart';

import 'support/app_driver.dart';
import 'support/fakes.dart';

/// Đã đăng nhập với [roles]; trả lời sẵn học kỳ, điểm và điểm danh của HS
/// và của con id 7.
Future<TestApp> pumpWithData(
  WidgetTester tester, {
  List<String> roles = const ['STUDENT'],
  List<Map<String, dynamic>> children = const [],
}) async {
  final app = TestApp(accessToken: 'a', refreshToken: 'r');
  app.http.reply('GET', '/me', FakeReply(200, userJson(roles: roles)));
  app.stubHome(children: children);
  app.http.reply('GET', '/semesters', FakeReply(200, semestersJson()));
  final grades = FakeReply(
    200,
    gradesJson([
      gradeRowJson(),
      gradeRowJson(
        subjectCode: 'TIN11',
        subjectName: 'Tin học 11',
        scores: [9, null],
        average: null,
      ),
      gradeRowJson(
        subjectCode: 'STEM',
        subjectName: 'STEM',
        scores: [],
        average: null,
      ),
    ]),
  );
  final attendance = FakeReply(
    200,
    attendanceJson([
      attendanceRowJson(),
      attendanceRowJson(
        sectionId: 27,
        subjectCode: 'TIN11',
        subjectName: 'Tin học 11',
        summary: summaryJson(
          present: 6,
          excused: 1,
          unexcused: 3,
          rate: 60,
          absentRate: 26.7,
          warning: true,
        ),
      ),
    ]),
  );
  final history = FakeReply(
    200,
    attendanceHistoryJson([
      attendanceSessionJson(),
      attendanceSessionJson(no: 2, status: 'PRESENT', note: 'Muộn 10 phút'),
      attendanceSessionJson(no: 3, status: 'ABSENT_EXCUSED'),
      attendanceSessionJson(no: 4, status: 'ABSENT_UNEXCUSED'),
      attendanceSessionJson(no: 5, status: null, sessionStatus: 'SCHEDULED'),
      attendanceSessionJson(no: 6, status: null, sessionStatus: 'CANCELLED'),
    ]),
  );
  for (final root in ['/student', '/parent/children/7']) {
    app.http.reply('GET', '$root/grades', grades);
    app.http.reply('GET', '$root/attendance', attendance);
    app.http.reply('GET', '$root/attendance/1', history);
  }
  await pumpApp(tester, app: app);
  return app;
}

Future<void> openTile(WidgetTester tester, String label) async {
  final tile = find.widgetWithText(FeatureTileWidget, label);
  await tester.ensureVisible(tile);
  await tester.tap(tile);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('grades show averages and unfinished subjects', (tester) async {
    await pumpWithData(tester);

    await openTile(tester, 'Điểm');

    expect(find.byType(ViewGradeScreen), findsOneWidget);
    expect(find.text('3 môn học'), findsOneWidget);
    expect(find.text('8,3'), findsOneWidget);
    expect(find.text('Chưa đủ điểm'), findsOneWidget);
    expect(find.text('Chưa có đầu điểm'), findsOneWidget);
  });

  testWidgets('tapping a subject shows its components', (tester) async {
    await pumpWithData(tester);
    await openTile(tester, 'Điểm');

    await tester.tap(find.text('Tin học 11'));
    await tester.pumpAndSettle();

    expect(find.text('Kiểm tra thường xuyên'), findsOneWidget);
    expect(find.text('Trọng số 20%'), findsOneWidget);
    expect(find.textContaining('–'), findsWidgets);
  });

  testWidgets('choosing another semester reloads the grades', (tester) async {
    final app = await pumpWithData(tester);
    await openTile(tester, 'Điểm');
    app.http.reply(
      'GET',
      '/student/grades',
      FakeReply(200, gradesJson([], semesterId: 38)),
    );

    final chip = find.text('Học kỳ II năm học 2026–2027');
    await tester.ensureVisible(chip);
    await tester.pumpAndSettle();
    await tester.tap(chip);
    await tester.pumpAndSettle();

    expect(app.http.requests.last.queryParameters, {'semesterId': 38});
    expect(find.text('Học kỳ này chưa có môn học nào.'), findsOneWidget);
  });

  testWidgets('attendance warns about subjects over the limit', (tester) async {
    await pumpWithData(tester);

    await openTile(tester, 'Điểm danh');

    expect(find.byType(ListAttendanceScreen), findsOneWidget);
    expect(find.text('2 môn học'), findsOneWidget);
    expect(find.textContaining('1 môn vượt ngưỡng vắng'), findsOneWidget);
    expect(find.text('Vượt ngưỡng vắng 20%'), findsOneWidget);
    expect(find.text('Đã điểm danh 10/15 buổi'), findsWidgets);
  });

  testWidgets('a subject shows every session with its status', (tester) async {
    // Màn hình cao để mọi buổi được dựng mà không cần cuộn.
    tester.view.physicalSize = const Size(800, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await pumpWithData(tester);
    await openTile(tester, 'Điểm danh');

    await tester.tap(find.text('Toán 10'));
    await tester.pumpAndSettle();

    expect(find.byType(ViewAttendanceScreen), findsOneWidget);
    expect(find.text('Có mặt'), findsNWidgets(2));
    expect(find.text('Muộn 10 phút'), findsOneWidget);
    expect(find.text('Vắng có phép'), findsOneWidget);
    expect(find.text('Vắng không phép'), findsOneWidget);
    expect(find.text('Chưa điểm danh'), findsOneWidget);
    expect(find.text('Buổi đã hủy'), findsOneWidget);
  });

  testWidgets('parent sees grades of the selected child', (tester) async {
    final app = await pumpWithData(
      tester,
      roles: ['PARENT'],
      children: [childJson(id: 7)],
    );

    await openTile(tester, 'Điểm');

    expect(
      app.http.requests.where((r) => r.path == '/parent/children/7/grades'),
      hasLength(1),
    );
    expect(find.text('3 môn học'), findsOneWidget);
  });

  testWidgets('grade error offers a retry', (tester) async {
    final app = await pumpWithData(tester);
    app.http.offline('GET', '/student/grades');

    await openTile(tester, 'Điểm');
    expect(find.textContaining('Không kết nối được máy chủ'), findsOneWidget);

    app.http.reply(
      'GET',
      '/student/grades',
      FakeReply(200, gradesJson([gradeRowJson()])),
    );
    await tester.tap(find.text('Thử lại'));
    await tester.pumpAndSettle();

    expect(find.text('1 môn học'), findsOneWidget);
  });
}
