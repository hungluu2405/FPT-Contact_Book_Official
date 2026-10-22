import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/change_password_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/login_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/home/home_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/auth_widget.dart';

import 'support/app_driver.dart';
import 'support/fakes.dart';

void main() {
  testWidgets('app starts on the login screen without a saved session', (
    tester,
  ) async {
    await pumpApp(tester);

    expect(find.byType(LoginScreen), findsOneWidget);
    expect(find.widgetWithText(TextButton, 'Quên mật khẩu?'), findsOneWidget);
  });

  testWidgets('empty login shows a message next to each field', (tester) async {
    final app = await pumpApp(tester);

    await tapButton(tester, 'Đăng nhập');

    expect(find.text('Vui lòng nhập số điện thoại'), findsOneWidget);
    expect(find.text('Vui lòng nhập mật khẩu'), findsOneWidget);
    expect(app.http.requests, isEmpty);
  });

  testWidgets('wrong credentials show the message of the server', (
    tester,
  ) async {
    final app = await pumpApp(tester);
    app.http.reply(
      'POST',
      '/auth/login',
      FakeReply.error(
        401,
        'BAD_CREDENTIALS',
        'Số điện thoại hoặc mật khẩu không đúng',
      ),
    );

    await enter(tester, 'Số điện thoại', '0900000004');
    await enter(tester, 'Mật khẩu', 'sai');
    await tapButton(tester, 'Đăng nhập');

    expect(find.text('Số điện thoại hoặc mật khẩu không đúng'), findsOneWidget);
    expect(find.byType(LoginScreen), findsOneWidget);
  });

  testWidgets('login button is disabled while waiting for the server', (
    tester,
  ) async {
    final app = await pumpApp(tester);
    final reply = Completer<FakeReply>();
    app.http.on('POST', '/auth/login', (_) => reply.future);

    await enter(tester, 'Số điện thoại', '0900000004');
    await enter(tester, 'Mật khẩu', 'Fpt@12345');
    await tester.tap(find.widgetWithText(FilledButton, 'Đăng nhập'));
    await tester.pump();

    final button = tester.widget<FilledButton>(find.byType(FilledButton));
    expect(button.onPressed, isNull);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    reply.complete(FakeReply(200, tokenJson()));
    await tester.pumpAndSettle();

    expect(find.byType(HomeScreen), findsOneWidget);
    expect(find.text('Xin chào,'), findsOneWidget);
    expect(find.text('Nguyễn Văn An'), findsOneWidget);
    expect(find.text('Sinh viên'), findsOneWidget);
  });

  testWidgets('forced password change shows server errors per field', (
    tester,
  ) async {
    final app = await pumpApp(tester);
    app.http.reply(
      'POST',
      '/auth/login',
      FakeReply(200, tokenJson(user: userJson(mustChangePassword: true))),
    );
    app.http.reply(
      'POST',
      '/me/password',
      FakeReply.error(
        400,
        'VALIDATION_FAILED',
        'Dữ liệu gửi lên không hợp lệ',
        fieldErrors: [
          {
            'field': 'confirmPassword',
            'message': 'Mật khẩu nhập lại không khớp',
          },
        ],
      ),
    );

    await enter(tester, 'Số điện thoại', '0900000004');
    await enter(tester, 'Mật khẩu', 'Fpt@12345');
    await tapButton(tester, 'Đăng nhập');
    expect(find.byType(ChangePasswordScreen), findsOneWidget);

    await enter(tester, 'Mật khẩu hiện tại', 'Fpt@12345');
    await enter(tester, 'Mật khẩu mới', 'Moi@12345');
    await enter(tester, 'Nhập lại mật khẩu mới', 'Khac@12345');
    await tapButton(tester, 'Đổi mật khẩu');

    expect(find.text('Mật khẩu nhập lại không khớp'), findsOneWidget);
    expect(find.text('Dữ liệu gửi lên không hợp lệ'), findsOneWidget);
    expect(find.byType(ChangePasswordScreen), findsOneWidget);
  });

  testWidgets('logout asks for confirmation and returns to login', (
    tester,
  ) async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/me', FakeReply(200, userJson()));
    app.http.reply('GET', '/me/profile', FakeReply(200, profileJson()));
    app.http.reply('POST', '/auth/logout', const FakeReply(204));
    app.stubHome();
    await pumpApp(tester, app: app);
    await tester.tap(find.text('Tài khoản'));
    await tester.pumpAndSettle();
    final logout = find.widgetWithText(OutlinedButton, 'Đăng xuất');

    await tester.ensureVisible(logout);
    await tester.tap(logout);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Hủy'));
    await tester.pumpAndSettle();
    expect(find.byType(HomeScreen), findsOneWidget);

    await tester.tap(logout);
    await tester.pumpAndSettle();
    await tapButton(tester, 'Đăng xuất');

    expect(find.byType(LoginScreen), findsOneWidget);
    expect(app.storage.refreshToken, isNull);
  });

  testWidgets('admin-only account is told to use the web', (tester) async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/me', FakeReply(200, userJson(roles: ['ADMIN'])));

    await pumpApp(tester, app: app);

    expect(find.textContaining('trang web'), findsOneWidget);
  });

  testWidgets('network error on startup offers a retry', (tester) async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.offline('GET', '/me');
    await pumpApp(tester, app: app);

    expect(find.textContaining('Không kết nối được máy chủ'), findsOneWidget);

    app.http.reply('GET', '/me', FakeReply(200, userJson()));
    await tester.tap(find.text('Thử lại'));
    await tester.pumpAndSettle();

    expect(find.byType(HomeScreen), findsOneWidget);
  });

  testWidgets('three-colour stripe is drawn with its full height', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [ThreeColorStripe()],
          ),
        ),
      ),
    );

    final bars = find.descendant(
      of: find.byType(ThreeColorStripe),
      matching: find.byType(ColoredBox),
    );
    expect(bars, findsNWidgets(3));
    for (final bar in bars.evaluate()) {
      expect(tester.getSize(find.byWidget(bar.widget)).height, 4);
    }
  });
}
