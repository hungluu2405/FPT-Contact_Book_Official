import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_driver.dart';
import 'support/fakes.dart';

/// Tổ trưởng (users.id 2) đã đăng nhập, tổ Toán – Tin học có 3 giáo viên khác.
Future<TestApp> pumpHead(WidgetTester tester) async {
  final app = TestApp(accessToken: 'a', refreshToken: 'r');
  addTearDown(app.deleteAttachmentDir);
  app.http.reply(
    'GET',
    '/me',
    FakeReply(
      200,
      userJson(
        id: 2,
        fullName: 'Trần Thị Giáo',
        roles: ['TEACHER', 'HEAD_OF_DEPARTMENT'],
      ),
    ),
  );
  app.stubHome();
  app.http.reply(
    'GET',
    '/head/departments',
    FakeReply(200, [departmentJson()]),
  );
  app.http.reply(
    'GET',
    '/head/teachers',
    FakeReply(
      200,
      pageJson([
        teacherJson(
          userId: 2,
          code: 'GV001',
          fullName: 'Trần Thị Giáo',
          head: true,
        ),
        teacherJson(),
        teacherJson(
          userId: 17,
          code: 'GV004',
          fullName: 'Đỗ Thuật',
          active: false,
        ),
      ]),
    ),
  );
  app.http.reply(
    'POST',
    '/head/notifications',
    FakeReply(201, sentDetailJson()),
  );
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

Future<void> fillForm(WidgetTester tester) async {
  await enter(tester, 'Tiêu đề', 'Họp tổ chuyên môn tháng 11');
  await enter(tester, 'Nội dung', 'Họp lúc 14:00 thứ Năm.');
}

Future<void> submit(WidgetTester tester) async {
  // Bỏ chọn ô nhập trước, nếu không ô đang gõ tự cuộn lại để hiện con trỏ.
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  final button = find.widgetWithText(FilledButton, 'Gửi thông báo');
  await tester.ensureVisible(button);
  await tester.pumpAndSettle();
  await tester.tap(button);
  await tester.pumpAndSettle();
}

FormData sentForm(TestApp app) =>
    app.http.requests
            .singleWhere(
              (r) => r.method == 'POST' && r.path == '/head/notifications',
            )
            .data
        as FormData;

void main() {
  testWidgets('an empty form shows errors without asking to confirm', (
    tester,
  ) async {
    final app = await pumpHead(tester);
    await tapText(tester, 'Gửi thông báo tổ');

    expect(find.text('Gửi tới: cả Tổ Toán – Tin học'), findsOneWidget);
    await submit(tester);

    expect(find.byType(AlertDialog), findsNothing);
    expect(find.text('Vui lòng nhập tiêu đề'), findsOneWidget);
    expect(find.text('Vui lòng nhập nội dung'), findsOneWidget);
    expect(app.http.count('POST', '/head/notifications'), 0);
  });

  testWidgets('the department is sent to after confirming', (tester) async {
    final app = await pumpHead(tester);
    await tapText(tester, 'Gửi thông báo tổ');
    await fillForm(tester);
    await tapText(tester, 'Khẩn');
    app.pickedFiles = [pickedFile('lich-du-gio.pdf')];
    await tapText(tester, 'Chọn tệp');

    await submit(tester);
    expect(
      find.text('Gửi thông báo này tới cả Tổ Toán – Tin học?'),
      findsOneWidget,
    );
    await tester.tap(find.widgetWithText(TextButton, 'Hủy'));
    await tester.pumpAndSettle();
    expect(app.http.count('POST', '/head/notifications'), 0);

    await submit(tester);
    await tester.tap(find.widgetWithText(FilledButton, 'Gửi'));
    await tester.pumpAndSettle();

    final form = sentForm(app);
    expect(Map.fromEntries(form.fields), {
      'title': 'Họp tổ chuyên môn tháng 11',
      'content': 'Họp lúc 14:00 thứ Năm.',
      'priority': 'URGENT',
      'departmentId': '1',
    });
    expect(form.files.single.value.filename, 'lich-du-gio.pdf');
    expect(find.text('Đã gửi thông báo.'), findsOneWidget);
    expect(find.text('1/3 đã đọc'), findsOneWidget);
    expect(find.text('Gửi tới: TT - Tổ Toán – Tin học'), findsOneWidget);
  });

  testWidgets('a backend error is shown at the top of the form', (
    tester,
  ) async {
    final app = await pumpHead(tester);
    app.http.reply(
      'POST',
      '/head/notifications',
      FakeReply.error(
        400,
        'BUSINESS_ERROR',
        'Phạm vi đã chọn không có người nhận nào (ngoài chính bạn)',
      ),
    );
    await tapText(tester, 'Gửi thông báo tổ');
    await fillForm(tester);

    await submit(tester);
    await tester.tap(find.widgetWithText(FilledButton, 'Gửi'));
    await tester.pumpAndSettle();

    final banner = find.text(
      'Phạm vi đã chọn không có người nhận nào (ngoài chính bạn)',
    );
    expect(banner, findsOneWidget);
    expect(tester.getTopLeft(banner).dy, inInclusiveRange(0, 600));
  });

  testWidgets('one teacher is sent to from the teacher page', (tester) async {
    final app = await pumpHead(tester);
    app.http.reply(
      'GET',
      '/head/teachers/3',
      FakeReply(200, teacherDetailJson()),
    );
    await tapText(tester, 'Giáo viên trong tổ');
    await tapText(tester, 'Nguyễn Thị Giáo');

    await tapText(tester, 'Gửi riêng thông báo');
    expect(find.text('Gửi riêng giáo viên'), findsOneWidget);
    expect(find.text('Gửi tới: riêng thầy/cô Nguyễn Thị Giáo'), findsOneWidget);
    await fillForm(tester);
    await submit(tester);
    await tester.tap(find.widgetWithText(FilledButton, 'Gửi'));
    await tester.pumpAndSettle();

    final fields = Map.fromEntries(sentForm(app).fields);
    expect(fields['teacherUserId'], '3');
    expect(fields.containsKey('departmentId'), isFalse);
  });

  testWidgets('no send button on my own page or a locked account', (
    tester,
  ) async {
    final app = await pumpHead(tester);
    app.http.reply(
      'GET',
      '/head/teachers/2',
      FakeReply(200, teacherDetailJson(userId: 2, code: 'GV001')),
    );
    app.http.reply(
      'GET',
      '/head/teachers/17',
      FakeReply(
        200,
        teacherDetailJson(userId: 17, code: 'GV004', active: false),
      ),
    );
    await tapText(tester, 'Giáo viên trong tổ');

    await tapText(tester, 'GV001 · 0900000003');
    expect(find.text('Gửi riêng thông báo'), findsNothing);
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();

    await tapText(tester, 'Đỗ Thuật');
    expect(find.text('Đã khóa'), findsOneWidget);
    expect(find.text('Gửi riêng thông báo'), findsNothing);
  });

  testWidgets('sent notifications show read progress and filter readers', (
    tester,
  ) async {
    final app = await pumpHead(tester);
    app.http.reply(
      'GET',
      '/notifications/sent',
      FakeReply(
        200,
        pageJson([
          sentJson(),
          sentJson(
            id: 71,
            title: 'Nhắc nộp đề',
            targetType: 'USER',
            recipients: 1,
            read: 0,
          ),
        ]),
      ),
    );
    app.http.on('GET', '/notifications/sent/70', (request) async {
      final read = request.queryParameters['read'];
      return FakeReply(
        200,
        read == false
            ? sentDetailJson(
                rows: [
                  recipientJson(name: 'Hoàng Văn Code', read: false),
                  recipientJson(name: 'Đỗ Thị Thuật', read: false),
                ],
              )
            : sentDetailJson(),
      );
    });
    await tapText(tester, 'Thông báo đã gửi');

    expect(find.text('1/3 đã đọc'), findsOneWidget);
    expect(find.text('0/1 đã đọc'), findsOneWidget);
    expect(find.text('Riêng một người · 23/10/2026 09:00'), findsOneWidget);

    await tapText(tester, 'Họp tổ chuyên môn tháng 11');
    expect(find.text('Đã đọc 23/10/2026 09:30'), findsOneWidget);
    expect(find.text('Chưa đọc'), findsNWidgets(3));

    final unreadChip = find.widgetWithText(ChoiceChip, 'Chưa đọc');
    await tester.ensureVisible(unreadChip);
    await tester.pumpAndSettle();
    await tester.tap(unreadChip);
    await tester.pumpAndSettle();

    expect(app.http.requests.last.queryParameters, {'read': false, 'page': 0});
    expect(find.text('Nguyễn Thị Giáo'), findsNothing);
    expect(find.text('Hoàng Văn Code'), findsOneWidget);
  });

  testWidgets('no sent notification yet shows the empty state', (tester) async {
    final app = await pumpHead(tester);
    app.http.reply('GET', '/notifications/sent', FakeReply(200, pageJson([])));

    await tapText(tester, 'Thông báo đã gửi');

    expect(find.text('Bạn chưa gửi thông báo nào.'), findsOneWidget);
  });
}
