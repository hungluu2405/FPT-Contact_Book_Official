import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/forgot_password_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/login_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/reset_password_screen.dart';

import 'support/app_driver.dart';
import 'support/fakes.dart';

Future<void> openForgotPassword(WidgetTester tester) async {
  await tester.tap(find.widgetWithText(TextButton, 'Quên mật khẩu?'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('login links to forgot password and back', (tester) async {
    await pumpApp(tester);

    await openForgotPassword(tester);
    expect(find.byType(ForgotPasswordScreen), findsOneWidget);

    await tester.tap(find.widgetWithText(TextButton, 'Quay lại đăng nhập'));
    await tester.pumpAndSettle();
    expect(find.byType(LoginScreen), findsOneWidget);
  });

  testWidgets('empty phone is required before calling the server', (
    tester,
  ) async {
    final app = await pumpApp(tester);
    await openForgotPassword(tester);

    await tapButton(tester, 'Gửi mã xác nhận');

    expect(find.text('Vui lòng nhập số điện thoại'), findsOneWidget);
    expect(app.http.requests, isEmpty);
  });

  testWidgets('full flow returns to login with a success message', (
    tester,
  ) async {
    final app = await pumpApp(tester);
    app.http.reply(
      'POST',
      '/auth/password-reset/request',
      const FakeReply(202),
    );
    app.http.reply(
      'POST',
      '/auth/password-reset/confirm',
      const FakeReply(204),
    );
    await openForgotPassword(tester);

    await enter(tester, 'Số điện thoại', '0900000004');
    await tapButton(tester, 'Gửi mã xác nhận');

    expect(find.byType(ResetPasswordScreen), findsOneWidget);
    expect(find.textContaining('mã xác nhận đã được gửi'), findsOneWidget);
    expect(find.text('Số điện thoại 0900000004'), findsOneWidget);

    await enter(tester, 'Mã xác nhận (6 chữ số)', '042917');
    await enter(tester, 'Mật khẩu mới', 'Moi@12345');
    await enter(tester, 'Nhập lại mật khẩu mới', 'Moi@12345');
    await tapButton(tester, 'Đặt lại mật khẩu');

    expect(find.byType(LoginScreen), findsOneWidget);
    expect(find.textContaining('Đặt lại mật khẩu thành công'), findsOneWidget);
    expect(app.http.count('POST', '/auth/password-reset/confirm'), 1);
  });

  testWidgets('wrong code shows the server message next to the code', (
    tester,
  ) async {
    final app = await pumpApp(tester);
    app.http.reply(
      'POST',
      '/auth/password-reset/request',
      const FakeReply(202),
    );
    app.http.reply(
      'POST',
      '/auth/password-reset/confirm',
      FakeReply.error(
        400,
        'BUSINESS_ERROR',
        'Mã xác nhận không đúng hoặc đã hết hạn',
        fieldErrors: [
          {
            'field': 'code',
            'message': 'Mã xác nhận không đúng hoặc đã hết hạn',
          },
        ],
      ),
    );
    await openForgotPassword(tester);
    await enter(tester, 'Số điện thoại', '0900000004');
    await tapButton(tester, 'Gửi mã xác nhận');

    await enter(tester, 'Mã xác nhận (6 chữ số)', '000000');
    await enter(tester, 'Mật khẩu mới', 'Moi@12345');
    await enter(tester, 'Nhập lại mật khẩu mới', 'Moi@12345');
    await tapButton(tester, 'Đặt lại mật khẩu');

    expect(find.byType(ResetPasswordScreen), findsOneWidget);
    // Một lần trong hộp lỗi, một lần cạnh ô mã.
    expect(
      find.text('Mã xác nhận không đúng hoặc đã hết hạn'),
      findsNWidgets(2),
    );
  });

  testWidgets('resend asks the server again for the same phone', (
    tester,
  ) async {
    final app = await pumpApp(tester);
    app.http.reply(
      'POST',
      '/auth/password-reset/request',
      const FakeReply(202),
    );
    await openForgotPassword(tester);
    await enter(tester, 'Số điện thoại', '0900000004');
    await tapButton(tester, 'Gửi mã xác nhận');

    final resend = find.widgetWithText(TextButton, 'Gửi lại mã');
    await tester.ensureVisible(resend);
    await tester.tap(resend);
    await tester.pumpAndSettle();

    expect(app.http.count('POST', '/auth/password-reset/request'), 2);
    expect(find.text('Đã gửi lại mã xác nhận'), findsOneWidget);
  });
}
