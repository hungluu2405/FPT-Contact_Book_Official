import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_driver.dart';
import 'support/fakes.dart';

const List<String> kHeadRoles = ['TEACHER', 'HEAD_OF_DEPARTMENT'];

/// Giáo viên (tổ trưởng khi [roles] có HEAD_OF_DEPARTMENT) đã đăng nhập.
Future<TestApp> pumpTeacher(
  WidgetTester tester, {
  List<String> roles = kHeadRoles,
  FakeReply? departments,
  List<Map<String, dynamic>>? teachers,
}) async {
  final app = TestApp(accessToken: 'a', refreshToken: 'r');
  app.http.reply(
    'GET',
    '/me',
    FakeReply(200, userJson(id: 2, fullName: 'Trần Thị Giáo', roles: roles)),
  );
  app.stubHome();
  app.http.reply(
    'GET',
    '/head/departments',
    departments ?? FakeReply(200, [departmentJson()]),
  );
  app.http.reply(
    'GET',
    '/head/teachers',
    FakeReply(
      200,
      pageJson(
        teachers ??
            [
              teacherJson(
                userId: 2,
                code: 'GV001',
                fullName: 'Trần Thị Giáo',
                phone: '0900000002',
                head: true,
              ),
              teacherJson(),
              teacherJson(
                userId: 17,
                code: 'GV004',
                fullName: 'Đỗ Thuật',
                phone: '0922334456',
                active: false,
              ),
            ],
      ),
    ),
  );
  await pumpApp(tester, app: app);
  return app;
}

Future<void> openTeachers(WidgetTester tester) async {
  final tile = find.text('Giáo viên trong tổ');
  await tester.ensureVisible(tile);
  await tester.pumpAndSettle();
  await tester.tap(tile);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('a head of department sees the department panel', (tester) async {
    await pumpTeacher(tester);

    expect(find.text('Tổ chuyên môn'), findsOneWidget);
    expect(
      find.text('Tổ trưởng · Tổ Toán – Tin học · 4 giáo viên'),
      findsOneWidget,
    );
    for (final label in [
      'Giáo viên trong tổ',
      'Gửi thông báo tổ',
      'Thông báo đã gửi',
    ]) {
      expect(find.text(label), findsOneWidget);
    }
    // Hai ô "Sắp có" thuộc lưới giáo viên (Lớp của tôi, Duyệt đơn).
    expect(find.text('Sắp có'), findsNWidgets(2));
    expect(find.text('Lịch dạy hôm nay'), findsOneWidget);
  });

  testWidgets('a regular teacher does not see it', (tester) async {
    final app = await pumpTeacher(tester, roles: const ['TEACHER']);

    expect(find.text('Tổ chuyên môn'), findsNothing);
    expect(app.http.count('GET', '/head/departments'), 0);
  });

  testWidgets('a head without a department is told to ask the admin', (
    tester,
  ) async {
    await pumpTeacher(tester, departments: const FakeReply(200, []));

    expect(
      find.textContaining('chưa được gán tổ chuyên môn nào'),
      findsOneWidget,
    );
    expect(find.text('Giáo viên trong tổ'), findsNothing);
  });

  testWidgets('a failed department load can be retried', (tester) async {
    final app = await pumpTeacher(
      tester,
      departments: FakeReply.error(500, 'INTERNAL_ERROR', 'Lỗi hệ thống'),
    );
    expect(find.text('Lỗi hệ thống'), findsOneWidget);

    app.http.reply(
      'GET',
      '/head/departments',
      FakeReply(200, [departmentJson()]),
    );
    final retry = find.widgetWithText(TextButton, 'Thử lại');
    await tester.ensureVisible(retry);
    await tester.pumpAndSettle();
    await tester.tap(retry);
    await tester.pumpAndSettle();

    expect(find.text('Giáo viên trong tổ'), findsOneWidget);
  });

  testWidgets('a head of two departments picks one', (tester) async {
    final app = await pumpTeacher(
      tester,
      departments: FakeReply(200, [
        departmentJson(),
        departmentJson(id: 38, code: 'NV', name: 'Tổ Ngữ văn', teacherCount: 6),
      ]),
    );

    final chip = find.widgetWithText(ChoiceChip, 'Tổ Ngữ văn');
    await tester.ensureVisible(chip);
    await tester.pumpAndSettle();
    await tester.tap(chip);
    await tester.pumpAndSettle();
    expect(find.text('Tổ trưởng · Tổ Ngữ văn · 6 giáo viên'), findsOneWidget);

    await openTeachers(tester);

    expect(
      app.http.requests
          .lastWhere((r) => r.path == '/head/teachers')
          .queryParameters['departmentId'],
      38,
    );
  });

  testWidgets('the teacher list shows badges and searches', (tester) async {
    final app = await pumpTeacher(tester);
    await openTeachers(tester);

    expect(find.text('Tổ Toán – Tin học · 4 giáo viên'), findsOneWidget);
    expect(find.text('Tổ trưởng'), findsOneWidget);
    expect(find.text('Đã khóa'), findsOneWidget);
    expect(find.text('GV002 · 0900000003'), findsOneWidget);

    await tester.enterText(find.byType(TextField), ' gv004 ');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    expect(app.http.requests.last.queryParameters, {
      'departmentId': 1,
      'q': 'gv004',
      'page': 0,
    });
  });

  testWidgets('an empty search result says so', (tester) async {
    final app = await pumpTeacher(tester);
    await openTeachers(tester);
    app.http.reply('GET', '/head/teachers', FakeReply(200, pageJson([])));

    await tester.enterText(find.byType(TextField), 'không có');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    expect(find.text('Không tìm thấy giáo viên phù hợp.'), findsOneWidget);
  });

  testWidgets('the teacher detail shows roles and copies the phone', (
    tester,
  ) async {
    final copied = <String>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copied.add((call.arguments as Map)['text'] as String);
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
    final app = await pumpTeacher(tester);
    app.http.reply(
      'GET',
      '/head/teachers/3',
      FakeReply(200, teacherDetailJson()),
    );
    await openTeachers(tester);

    await tester.tap(find.text('Nguyễn Thị Giáo'));
    await tester.pumpAndSettle();

    expect(find.text('Giáo viên'), findsOneWidget);
    expect(find.text('Đang hoạt động'), findsOneWidget);
    expect(find.text('Thạc sĩ'), findsOneWidget);
    expect(find.text('22/10/2026 07:05'), findsOneWidget);
    expect(find.text('teacher@fpt.edu.vn'), findsOneWidget);

    await tester.tap(find.byTooltip('Sao chép Số điện thoại'));
    await tester.pumpAndSettle();

    expect(copied, ['0900000003']);
    expect(find.text('Đã sao chép số điện thoại.'), findsOneWidget);
  });

  testWidgets('a teacher outside the department shows the error', (
    tester,
  ) async {
    final app = await pumpTeacher(tester);
    app.http.reply(
      'GET',
      '/head/teachers/3',
      FakeReply.error(403, 'FORBIDDEN', 'Bạn không có quyền xem nội dung này'),
    );
    await openTeachers(tester);

    await tester.tap(find.text('Nguyễn Thị Giáo'));
    await tester.pumpAndSettle();

    expect(find.text('Bạn không có quyền xem nội dung này'), findsOneWidget);
  });
}
