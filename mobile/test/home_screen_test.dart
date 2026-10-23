import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/change_password_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/feature_tile_widget.dart';

import 'support/app_driver.dart';
import 'support/fakes.dart';

/// App đã đăng nhập với các vai trò [roles], trang chủ đã có dữ liệu giả.
Future<TestApp> pumpHome(
  WidgetTester tester, {
  List<String> roles = const ['STUDENT'],
  List<Map<String, dynamic>> children = const [],
  int unread = 0,
}) async {
  final app = TestApp(accessToken: 'a', refreshToken: 'r');
  app.http.reply('GET', '/me', FakeReply(200, userJson(roles: roles)));
  app.http.reply('GET', '/me/profile', FakeReply(200, profileJson()));
  app.stubHome(unread: unread, children: children);
  await pumpApp(tester, app: app);
  return app;
}

Future<void> tapOutlined(WidgetTester tester, String label) async {
  final button = find.widgetWithText(OutlinedButton, label);
  await tester.ensureVisible(button);
  await tester.tap(button);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('student sees six features, four still coming soon', (
    tester,
  ) async {
    await pumpHome(tester, unread: 3);

    for (final label in [
      'Lịch học',
      'Lịch thi',
      'Điểm',
      'Điểm danh',
      'Câu lạc bộ',
      'Đơn từ',
    ]) {
      expect(find.widgetWithText(FeatureTileWidget, label), findsOneWidget);
    }
    expect(find.text('Sắp có'), findsNWidgets(4));
    expect(find.text('3'), findsOneWidget);
    expect(find.textContaining('Đang xem'), findsNothing);
  });

  testWidgets('a coming-soon tile opens nothing', (tester) async {
    await pumpHome(tester);

    await tester.ensureVisible(find.text('Điểm'));
    await tester.tap(find.text('Điểm'));
    await tester.pumpAndSettle();

    expect(find.byType(FeatureTileWidget), findsNWidgets(6));
  });

  testWidgets('parent with one child sees the child without a picker', (
    tester,
  ) async {
    await pumpHome(tester, roles: ['PARENT'], children: [childJson()]);

    expect(find.textContaining('Nguyễn Văn An · HS260001'), findsOneWidget);
    expect(find.byIcon(Icons.arrow_drop_down), findsNothing);
  });

  testWidgets('parent with two children switches the child', (tester) async {
    final app = await pumpHome(
      tester,
      roles: ['PARENT'],
      children: [
        childJson(id: 1),
        childJson(id: 2, code: 'SE190002', name: 'Nguyễn Thị Bình'),
      ],
    );

    await tester.tap(find.byIcon(Icons.arrow_drop_down));
    await tester.pumpAndSettle();
    expect(find.text('Chọn người cần xem'), findsOneWidget);

    await tester.tap(find.text('Nguyễn Thị Bình · SE190002'));
    await tester.pumpAndSettle();

    expect(find.text('Chọn người cần xem'), findsNothing);
    expect(
      find.textContaining('Đang xem: Nguyễn Thị Bình · SE190002'),
      findsOneWidget,
    );
    expect(app.homeController.selectedStudentId, 2);
  });

  testWidgets('parent without children is told to contact the school', (
    tester,
  ) async {
    await pumpHome(tester, roles: ['PARENT']);

    expect(find.textContaining('chưa được liên kết'), findsOneWidget);
    expect(find.byType(FeatureTileWidget), findsNothing);
  });

  testWidgets('parent sees an error with retry when children fail', (
    tester,
  ) async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/me', FakeReply(200, userJson(roles: ['PARENT'])));
    app.stubHome();
    app.http.offline('GET', '/parent/children');
    await pumpApp(tester, app: app);

    expect(find.textContaining('Không kết nối được máy chủ'), findsOneWidget);

    app.http.reply('GET', '/parent/children', FakeReply(200, [childJson()]));
    await tester.ensureVisible(find.text('Thử lại'));
    await tester.tap(find.text('Thử lại'));
    await tester.pumpAndSettle();

    expect(find.byType(FeatureTileWidget), findsNWidgets(6));
  });

  testWidgets('teacher-only account waits for phase 10d', (tester) async {
    await pumpHome(tester, roles: ['TEACHER']);

    expect(find.textContaining('Chức năng giảng viên'), findsOneWidget);
    expect(find.byType(FeatureTileWidget), findsNothing);
  });

  testWidgets('account tab shows the profile', (tester) async {
    await pumpHome(tester);

    await tester.tap(find.text('Tài khoản'));
    await tester.pumpAndSettle();

    expect(find.text('an.nv@fpt.edu.vn'), findsOneWidget);
    expect(find.text('15/03/2005'), findsOneWidget);
    expect(find.text('Nam'), findsOneWidget);
  });

  testWidgets('voluntary password change closes and confirms', (tester) async {
    final app = await pumpHome(tester);
    app.http.reply('POST', '/me/password', FakeReply(200, tokenJson()));
    await tester.tap(find.text('Tài khoản'));
    await tester.pumpAndSettle();

    await tapOutlined(tester, 'Đổi mật khẩu');
    expect(find.byType(ChangePasswordScreen), findsOneWidget);
    expect(find.widgetWithText(TextButton, 'Quay lại'), findsOneWidget);

    await enter(tester, 'Mật khẩu hiện tại', 'Fpt@12345');
    await enter(tester, 'Mật khẩu mới', 'Moi@12345');
    await enter(tester, 'Nhập lại mật khẩu mới', 'Moi@12345');
    await tapButton(tester, 'Đổi mật khẩu');

    expect(find.byType(ChangePasswordScreen), findsNothing);
    expect(find.text('Đổi mật khẩu thành công'), findsOneWidget);
    expect(app.storage.accessToken, 'access-1');
  });

  testWidgets('voluntary password change can go back', (tester) async {
    await pumpHome(tester);
    await tester.tap(find.text('Tài khoản'));
    await tester.pumpAndSettle();
    await tapOutlined(tester, 'Đổi mật khẩu');

    final back = find.widgetWithText(TextButton, 'Quay lại');
    await tester.ensureVisible(back);
    await tester.tap(back);
    await tester.pumpAndSettle();

    expect(find.byType(ChangePasswordScreen), findsNothing);
    expect(find.text('Đổi mật khẩu thành công'), findsNothing);
  });
}
