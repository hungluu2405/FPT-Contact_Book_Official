import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_driver.dart';
import 'support/fakes.dart';

const String kSheet = '/teacher/sections/1/sessions/2/attendance';

/// Giáo viên đã đăng nhập; lớp 1 có buổi 1 (đã điểm danh) và buổi 2.
Future<TestApp> pumpTeacher(
  WidgetTester tester, {
  Map<String, dynamic>? sheet,
}) async {
  final app = TestApp(accessToken: 'a', refreshToken: 'r');
  app.http.reply(
    'GET',
    '/me',
    FakeReply(
      200,
      userJson(id: 3, fullName: 'Nguyễn Thị Giáo', roles: ['TEACHER']),
    ),
  );
  app.stubHome();
  app.http.reply('GET', '/semesters', FakeReply(200, semestersJson()));
  app.http.reply(
    'GET',
    '/teacher/sections',
    FakeReply(
      200,
      pagedJson([
        teacherSectionJson(),
        teacherSectionJson(
          id: 2,
          code: 'TIN11-HK1-2627-01',
          subjectCode: 'TIN11',
          subjectName: 'Tin học 11',
          status: 'CLOSED',
        ),
      ]),
    ),
  );
  app.http.reply(
    'GET',
    '/teacher/sections/1/attendance',
    FakeReply(
      200,
      sectionAttendanceJson(
        students: [
          sectionStudentJson(),
          sectionStudentJson(
            studentId: 2,
            code: 'HS260002',
            name: 'Trần Thị Bình',
            summary: summaryJson(
              present: 5,
              unexcused: 4,
              absentRate: 26.7,
              warning: true,
            ),
          ),
        ],
      ),
    ),
  );
  app.http.reply('GET', kSheet, FakeReply(200, sheet ?? sheetJson()));
  await pumpApp(tester, app: app);
  return app;
}

Future<void> tapText(WidgetTester tester, String text) async {
  final target = find.text(text).last;
  await tester.ensureVisible(target);
  await tester.pumpAndSettle();
  await tester.tap(target);
  await tester.pumpAndSettle();
}

Future<void> openSheet(WidgetTester tester) async {
  await tapText(tester, 'Lớp của tôi');
  await tapText(tester, 'Toán 10');
  await tapText(tester, 'Buổi 2');
}

/// Bấm nút trạng thái [label] trên hàng của học sinh [name].
Future<void> choose(WidgetTester tester, String name, String label) async {
  final row = find
      .ancestor(of: find.text(name), matching: find.byType(Container))
      .first;
  final button = find.descendant(of: row, matching: find.text(label));
  await tester.ensureVisible(button);
  await tester.pumpAndSettle();
  await tester.tap(button);
  await tester.pumpAndSettle();
}

Future<void> save(WidgetTester tester) async {
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  final button = find.widgetWithText(FilledButton, 'Lưu điểm danh');
  await tester.ensureVisible(button);
  await tester.pumpAndSettle();
  await tester.tap(button);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('my classes lists sections with the semester', (tester) async {
    await pumpTeacher(tester);

    await tapText(tester, 'Lớp của tôi');

    expect(find.text('Lớp đang dạy'), findsOneWidget);
    expect(find.text('Học kỳ I năm học 2026–2027'), findsWidgets);
    expect(find.text('TOAN10-HK1-2627-01'), findsOneWidget);
    expect(find.text('Đã đóng'), findsOneWidget);
  });

  testWidgets('a section shows sessions and students with warnings', (
    tester,
  ) async {
    await pumpTeacher(tester);
    await tapText(tester, 'Lớp của tôi');
    await tapText(tester, 'Toán 10');

    expect(find.text('Buổi học (15)'), findsOneWidget);
    expect(find.text('Đã điểm danh'), findsOneWidget);
    expect(find.text('Chưa điểm danh'), findsOneWidget);

    await tapText(tester, 'Học sinh');

    expect(find.text('Ngưỡng cảnh báo: vắng quá 20% số buổi.'), findsOneWidget);
    expect(find.text('Vượt ngưỡng vắng'), findsOneWidget);
    expect(
      find.text('Có mặt 5 · Có phép 1 · Không phép 4 · Vắng 26,7%'),
      findsOneWidget,
    );
  });

  testWidgets('a first attendance is saved after confirming', (tester) async {
    final app = await pumpTeacher(tester);
    app.http.reply(
      'POST',
      kSheet,
      FakeReply(200, {
        'firstTime': true,
        'changed': 3,
        'sheet': sheetJson(marked: true),
      }),
    );
    await openSheet(tester);

    expect(find.textContaining('chọn sẵn "Có mặt"'), findsOneWidget);
    expect(find.text('Có đơn nghỉ'), findsOneWidget);
    expect(find.text('Có mặt 2'), findsOneWidget);

    await choose(tester, 'Trần Thị Bình', 'Không phép');
    expect(find.text('Không phép 1'), findsOneWidget);
    expect(find.text('Lý do điều chỉnh'), findsNothing);

    await save(tester);
    expect(
      find.textContaining('Lưu điểm danh cho 3 học sinh?'),
      findsOneWidget,
    );
    await tester.tap(find.widgetWithText(FilledButton, 'Lưu'));
    await tester.pumpAndSettle();

    expect(app.http.requests.lastWhere((r) => r.method == 'POST').data, {
      'statuses': {
        '1': 'PRESENT',
        '2': 'ABSENT_UNEXCUSED',
        '3': 'ABSENT_EXCUSED',
      },
    });
    expect(find.text('Đã lưu điểm danh (3 học sinh).'), findsOneWidget);
    expect(find.textContaining('Buổi này đã điểm danh'), findsOneWidget);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(app.http.count('GET', '/teacher/sections/1/attendance'), 2);
  });

  testWidgets('changing a marked session asks for a reason', (tester) async {
    final app = await pumpTeacher(tester, sheet: sheetJson(marked: true));
    app.http.reply(
      'POST',
      kSheet,
      FakeReply(200, {
        'firstTime': false,
        'changed': 1,
        'sheet': sheetJson(marked: true),
      }),
    );
    await openSheet(tester);

    await choose(tester, 'Trần Thị Bình', 'Có phép');
    expect(find.text('Lý do điều chỉnh'), findsOneWidget);

    await save(tester);
    await tester.tap(find.widgetWithText(FilledButton, 'Lưu'));
    await tester.pumpAndSettle();
    expect(
      find.text('Vui lòng nhập lý do điều chỉnh điểm danh'),
      findsOneWidget,
    );
    expect(app.http.count('POST', kSheet), 0);

    await enter(tester, 'Lý do điều chỉnh', 'Phụ huynh gửi giấy xin phép');
    await save(tester);
    await tester.tap(find.widgetWithText(FilledButton, 'Lưu'));
    await tester.pumpAndSettle();

    expect(
      (app.http.requests.lastWhere((r) => r.method == 'POST').data
          as Map)['reason'],
      'Phụ huynh gửi giấy xin phép',
    );
  });

  testWidgets('a locked session is view only', (tester) async {
    await pumpTeacher(tester, sheet: sheetJson(marked: true, locked: true));
    await openSheet(tester);

    expect(find.textContaining('Đã hết hạn điểm danh'), findsOneWidget);
    expect(find.text('Lưu điểm danh'), findsNothing);
    expect(find.text('Tất cả có mặt'), findsNothing);
    expect(find.text('Ghi chú: Không báo trước'), findsOneWidget);

    await choose(tester, 'Lê Minh Sinh', 'Không phép');
    expect(find.text('Có mặt 1'), findsOneWidget);
  });

  testWidgets('unsaved choices ask before the sheet reloads', (tester) async {
    final app = await pumpTeacher(tester);
    await openSheet(tester);
    await choose(tester, 'Trần Thị Bình', 'Không phép');
    final refresh = tester.widget<RefreshIndicator>(
      find.byType(RefreshIndicator),
    );

    unawaited(refresh.onRefresh());
    await tester.pumpAndSettle();
    expect(find.text('Tải lại bảng điểm danh?'), findsOneWidget);
    await tester.tap(find.text('Ở lại'));
    await tester.pumpAndSettle();
    expect(app.http.count('GET', kSheet), 1);

    unawaited(refresh.onRefresh());
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Tải lại'));
    await tester.pumpAndSettle();
    expect(app.http.count('GET', kSheet), 2);
  });

  testWidgets('leaving with unsaved choices asks first', (tester) async {
    await pumpTeacher(tester);
    await openSheet(tester);
    await choose(tester, 'Trần Thị Bình', 'Không phép');

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.text('Bỏ nội dung đang soạn?'), findsOneWidget);
    await tester.tap(find.text('Bỏ nội dung'));
    await tester.pumpAndSettle();

    expect(find.widgetWithText(FilledButton, 'Lưu điểm danh'), findsNothing);
  });

  testWidgets('a concurrent save shows the error and can reload', (
    tester,
  ) async {
    final app = await pumpTeacher(tester);
    app.http.reply(
      'POST',
      kSheet,
      FakeReply.error(
        400,
        'BUSINESS_ERROR',
        'Điểm danh vừa được người khác lưu, vui lòng tải lại trang và thử lại',
      ),
    );
    await openSheet(tester);

    await save(tester);
    await tester.tap(find.widgetWithText(FilledButton, 'Lưu'));
    await tester.pumpAndSettle();

    expect(
      find.text(
        'Điểm danh vừa được người khác lưu, vui lòng tải lại trang và thử lại',
      ),
      findsOneWidget,
    );
    await tapText(tester, 'Tải lại bảng điểm danh');
    expect(app.http.count('GET', kSheet), 2);
  });
}
