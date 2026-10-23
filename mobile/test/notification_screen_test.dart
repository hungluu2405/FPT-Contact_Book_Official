import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:open_filex/open_filex.dart';

import 'support/app_driver.dart';
import 'support/fakes.dart';

/// HS đã đăng nhập, có [unread] thông báo chưa đọc; mở tab Thông báo.
Future<TestApp> openNotificationTab(
  WidgetTester tester, {
  int unread = 2,
  List<Map<String, dynamic>>? items,
}) async {
  final app = TestApp(accessToken: 'a', refreshToken: 'r');
  addTearDown(app.deleteAttachmentDir);
  app.http.reply('GET', '/me', FakeReply(200, userJson()));
  app.stubHome(unread: unread);
  app.http.reply(
    'GET',
    '/notifications',
    FakeReply(
      200,
      pageJson(
        items ??
            [
              notificationJson(priority: 'URGENT'),
              notificationJson(id: 2, title: 'Họp phụ huynh', read: true),
            ],
      ),
    ),
  );
  await pumpApp(tester, app: app);
  await tester.tap(
    find.descendant(
      of: find.byType(NavigationBar),
      matching: find.text('Thông báo'),
    ),
  );
  await tester.pumpAndSettle();
  return app;
}

/// Chấm cam của thông báo chưa đọc.
final Finder unreadDot = find.byWidgetPredicate(
  (widget) => widget is Semantics && widget.properties.label == 'Chưa đọc',
);

FontWeight? weightOf(WidgetTester tester, String text) =>
    tester.widget<Text>(find.text(text)).style?.fontWeight;

void main() {
  testWidgets('the tab lists notifications and marks unread ones', (
    tester,
  ) async {
    await openNotificationTab(tester);

    expect(find.text('Bạn có 2 thông báo chưa đọc'), findsOneWidget);
    expect(find.text('Khẩn'), findsOneWidget);
    expect(unreadDot, findsOneWidget);
    expect(weightOf(tester, 'Lịch nghỉ lễ 20/11'), FontWeight.w700);
    expect(weightOf(tester, 'Họp phụ huynh'), FontWeight.w500);
    expect(
      find.text('Phòng Công tác học sinh · 20/10/2026 08:30'),
      findsNWidgets(2),
    );
  });

  testWidgets('opening a notification shows it and lowers the count', (
    tester,
  ) async {
    final app = await openNotificationTab(tester);
    app.http.reply(
      'GET',
      '/notifications/1',
      FakeReply(
        200,
        notificationDetailJson(priority: 'URGENT', files: [attachmentJson()]),
      ),
    );
    app.http.reply(
      'GET',
      '/notifications/unread-count',
      const FakeReply(200, {'count': 1}),
    );

    await tester.tap(find.text('Lịch nghỉ lễ 20/11'));
    await tester.pumpAndSettle();

    expect(find.text('Toàn trường'), findsOneWidget);
    expect(find.byType(SelectableText), findsOneWidget);
    expect(find.text('Tệp đính kèm (1)'), findsOneWidget);
    expect(find.text('240 KB'), findsOneWidget);
    expect(app.http.count('GET', '/notifications/unread-count'), 2);

    await tester.tap(find.byTooltip('Quay lại'));
    await tester.pumpAndSettle();

    expect(find.text('Bạn có 1 thông báo chưa đọc'), findsOneWidget);
    expect(weightOf(tester, 'Lịch nghỉ lễ 20/11'), FontWeight.w500);
    expect(unreadDot, findsNothing);
  });

  testWidgets('tapping a file downloads and opens it', (tester) async {
    final app = await openNotificationTab(tester);
    app.http.reply(
      'GET',
      '/notifications/1',
      FakeReply(200, notificationDetailJson(files: [attachmentJson()])),
    );
    app.http.reply(
      'GET',
      '/notifications/1/files/11',
      FakeReply(200, Uint8List.fromList([1, 2, 3])),
    );
    await tester.tap(find.text('Lịch nghỉ lễ 20/11'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('lich-nghi-le.pdf'));
    await tester.pumpAndSettle();

    expect(app.openedFiles.single, endsWith('lich-nghi-le.pdf'));
    expect(find.byType(SnackBar), findsNothing);
  });

  testWidgets('a file that no app can open shows a message', (tester) async {
    final app = await openNotificationTab(tester);
    app.openResult = OpenResult(type: ResultType.noAppToOpen);
    app.http.reply(
      'GET',
      '/notifications/1',
      FakeReply(200, notificationDetailJson(files: [attachmentJson()])),
    );
    app.http.reply(
      'GET',
      '/notifications/1/files/11',
      FakeReply(200, Uint8List.fromList([1])),
    );
    await tester.tap(find.text('Lịch nghỉ lễ 20/11'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('lich-nghi-le.pdf'));
    await tester.pumpAndSettle();

    expect(find.text('Máy chưa có ứng dụng mở được tệp này.'), findsOneWidget);
  });

  testWidgets('mark all read clears the count and the bold titles', (
    tester,
  ) async {
    final app = await openNotificationTab(tester);
    app.http.reply(
      'POST',
      '/notifications/read-all',
      const FakeReply(200, {'count': 0}),
    );
    app.http.reply(
      'GET',
      '/notifications/unread-count',
      const FakeReply(200, {'count': 0}),
    );

    await tester.tap(find.text('Đánh dấu tất cả đã đọc'));
    await tester.pumpAndSettle();

    expect(
      find.text('Đã đánh dấu tất cả thông báo là đã đọc.'),
      findsOneWidget,
    );
    expect(find.text('Bạn đã đọc hết thông báo'), findsOneWidget);
    expect(find.text('Đánh dấu tất cả đã đọc'), findsNothing);
    expect(weightOf(tester, 'Lịch nghỉ lễ 20/11'), FontWeight.w500);
    expect(
      find.descendant(
        of: find.byType(NavigationBar),
        matching: find.byType(Badge),
      ),
      findsNothing,
    );
  });

  testWidgets('an empty inbox shows the empty state', (tester) async {
    await openNotificationTab(tester, unread: 0, items: const []);

    expect(find.text('Chưa có thông báo nào.'), findsOneWidget);
    expect(find.text('Đánh dấu tất cả đã đọc'), findsNothing);
  });

  testWidgets('a network error can be retried', (tester) async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/me', FakeReply(200, userJson()));
    app.stubHome();
    app.http.offline('GET', '/notifications');
    await pumpApp(tester, app: app);
    await tester.tap(
      find.descendant(
        of: find.byType(NavigationBar),
        matching: find.text('Thông báo'),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Thử lại'), findsOneWidget);

    app.http.reply(
      'GET',
      '/notifications',
      FakeReply(200, pageJson([notificationJson()])),
    );
    await tester.tap(find.text('Thử lại'));
    await tester.pumpAndSettle();

    expect(find.text('Lịch nghỉ lễ 20/11'), findsOneWidget);
  });

  testWidgets('choosing the tab again reloads the inbox', (tester) async {
    final app = await openNotificationTab(tester);
    final tab = find.descendant(
      of: find.byType(NavigationBar),
      matching: find.text('Thông báo'),
    );

    await tester.tap(tab);
    await tester.pumpAndSettle();

    expect(app.http.count('GET', '/notifications'), 2);
  });

  testWidgets('without the unread count the header relies on loaded items', (
    tester,
  ) async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/me', FakeReply(200, userJson()));
    app.stubHome();
    app.http.offline('GET', '/notifications/unread-count');
    app.http.reply(
      'GET',
      '/notifications',
      FakeReply(200, pageJson([notificationJson()])),
    );
    await pumpApp(tester, app: app);
    await tester.tap(
      find.descendant(
        of: find.byType(NavigationBar),
        matching: find.text('Thông báo'),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Bạn đã đọc hết thông báo'), findsNothing);
    expect(find.text('Đánh dấu tất cả đã đọc'), findsOneWidget);
  });

  testWidgets('logging out deletes downloaded attachments', (tester) async {
    final app = await openNotificationTab(tester);
    app.http.reply(
      'GET',
      '/notifications/1',
      FakeReply(200, notificationDetailJson(files: [attachmentJson()])),
    );
    app.http.reply(
      'GET',
      '/notifications/1/files/11',
      FakeReply(200, Uint8List.fromList([1])),
    );
    app.http.reply('GET', '/me/profile', FakeReply(200, profileJson()));
    app.http.reply('POST', '/auth/logout', const FakeReply(204));
    await tester.tap(find.text('Lịch nghỉ lễ 20/11'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('lich-nghi-le.pdf'));
    await tester.pumpAndSettle();
    final downloaded = File(app.openedFiles.single);
    expect(downloaded.existsSync(), isTrue);

    await tester.tap(find.text('Tài khoản'));
    await tester.pumpAndSettle();
    final logout = find.widgetWithText(OutlinedButton, 'Đăng xuất');
    await tester.ensureVisible(logout);
    await tester.tap(logout);
    await tester.pumpAndSettle();
    await tapButton(tester, 'Đăng xuất');

    expect(downloaded.existsSync(), isFalse);
  });
}
