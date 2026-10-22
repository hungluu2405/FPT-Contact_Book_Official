import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/auth_controller.dart';

import '../support/fakes.dart';

void main() {
  group('restoreSession', () {
    test('without a saved token goes to login and calls nothing', () async {
      final app = TestApp();

      await app.controller.restoreSession();

      expect(app.controller.status, AuthStatus.unauthenticated);
      expect(app.http.requests, isEmpty);
    });

    test('with a saved token loads the current user', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply('GET', '/me', FakeReply(200, userJson()));

      await app.controller.restoreSession();

      expect(app.controller.status, AuthStatus.authenticated);
      expect(app.controller.currentUser!.fullName, 'Nguyễn Văn An');
    });

    test('user who must change password goes to the change screen', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/me',
        FakeReply(200, userJson(mustChangePassword: true)),
      );

      await app.controller.restoreSession();

      expect(app.controller.status, AuthStatus.mustChangePassword);
    });

    test('network error keeps the token and can be retried', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.offline('GET', '/me');

      await app.controller.restoreSession();

      expect(app.controller.status, AuthStatus.unknown);
      expect(app.controller.startupError!.isNetworkError, isTrue);
      expect(app.storage.refreshToken, 'r');

      app.http.reply('GET', '/me', FakeReply(200, userJson()));
      await app.controller.restoreSession();

      expect(app.controller.status, AuthStatus.authenticated);
      expect(app.controller.startupError, isNull);
    });

    test('expired refresh token goes back to login', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/me',
        FakeReply.error(401, 'UNAUTHORIZED', 'Hết hạn'),
      );
      app.http.reply(
        'POST',
        '/auth/refresh',
        FakeReply.error(401, 'UNAUTHORIZED', 'Hết hạn'),
      );

      await app.controller.restoreSession();

      expect(app.controller.status, AuthStatus.unauthenticated);
      expect(app.storage.refreshToken, isNull);
    });
  });

  group('login', () {
    test('saves the tokens and opens the app', () async {
      final app = TestApp();
      app.http.on('POST', '/auth/login', (request) async {
        expect(request.data, {'phone': '0900000004', 'password': 'Fpt@12345'});
        return FakeReply(200, tokenJson());
      });

      final ok = await app.controller.login(' 0900000004 ', 'Fpt@12345');

      expect(ok, isTrue);
      expect(app.controller.status, AuthStatus.authenticated);
      expect(app.controller.isLoading, isFalse);
      expect(app.storage.accessToken, 'access-1');
      expect(app.storage.refreshToken, 'refresh-1');
    });

    test('must change password after login', () async {
      final app = TestApp();
      app.http.reply(
        'POST',
        '/auth/login',
        FakeReply(200, tokenJson(user: userJson(mustChangePassword: true))),
      );

      await app.controller.login('0900000004', 'Fpt@12345');

      expect(app.controller.status, AuthStatus.mustChangePassword);
    });

    test('shows the message of the server on wrong credentials', () async {
      final app = TestApp();
      app.http.reply(
        'POST',
        '/auth/login',
        FakeReply.error(
          401,
          'BAD_CREDENTIALS',
          'Số điện thoại hoặc mật khẩu không đúng',
        ),
      );

      await app.controller.restoreSession();
      final ok = await app.controller.login('0900000004', 'sai');

      expect(ok, isFalse);
      expect(app.controller.status, AuthStatus.unauthenticated);
      expect(
        app.controller.errorMessage,
        'Số điện thoại hoặc mật khẩu không đúng',
      );
      expect(app.storage.accessToken, isNull);
    });

    test('shows the temporary lock message', () async {
      final app = TestApp();
      app.http.reply(
        'POST',
        '/auth/login',
        FakeReply.error(
          429,
          'ACCOUNT_LOCKED_TEMPORARILY',
          'Tài khoản tạm khóa 15 phút',
        ),
      );

      await app.controller.login('0900000004', 'sai');

      expect(app.controller.errorMessage, 'Tài khoản tạm khóa 15 phút');
    });

    test('clearErrors removes the previous error', () async {
      final app = TestApp();
      app.http.offline('POST', '/auth/login');
      await app.controller.login('0900000004', 'x');
      expect(app.controller.errorMessage, isNotNull);

      app.controller.clearErrors();

      expect(app.controller.errorMessage, isNull);
    });
  });

  group('changePassword', () {
    test('field errors of the server are kept per field', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'POST',
        '/me/password',
        FakeReply.error(
          400,
          'VALIDATION_FAILED',
          'Dữ liệu gửi lên không hợp lệ',
          fieldErrors: [
            {'field': 'newPassword', 'message': 'Mật khẩu mới quá ngắn'},
          ],
        ),
      );

      final ok = await app.controller.changePassword(
        currentPassword: 'Fpt@12345',
        newPassword: '1',
        confirmPassword: '1',
      );

      expect(ok, isFalse);
      expect(app.controller.fieldErrors, {
        'newPassword': 'Mật khẩu mới quá ngắn',
      });
    });

    test('success stores the new tokens and opens the app', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'POST',
        '/me/password',
        FakeReply(200, tokenJson(access: 'a2', refresh: 'r2')),
      );

      final ok = await app.controller.changePassword(
        currentPassword: 'Fpt@12345',
        newPassword: 'Moi@12345',
        confirmPassword: 'Moi@12345',
      );

      expect(ok, isTrue);
      expect(app.controller.status, AuthStatus.authenticated);
      expect(app.storage.refreshToken, 'r2');
    });
  });

  test('logout clears the tokens even without network', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/me', FakeReply(200, userJson()));
    await app.controller.restoreSession();
    app.http.offline('POST', '/auth/logout');

    await app.controller.logout();

    expect(app.http.count('POST', '/auth/logout'), 1);
    expect(app.controller.status, AuthStatus.unauthenticated);
    expect(app.controller.currentUser, isNull);
    expect(app.storage.accessToken, isNull);
    expect(app.storage.refreshToken, isNull);
  });

  test(
    'PASSWORD_CHANGE_REQUIRED from any API opens the change screen',
    () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply('GET', '/me', FakeReply(200, userJson()));
      await app.controller.restoreSession();
      app.http.reply(
        'GET',
        '/semesters',
        FakeReply.error(403, 'PASSWORD_CHANGE_REQUIRED', 'Phải đổi mật khẩu'),
      );

      await expectLater(app.apiClient.get('/semesters'), throwsA(anything));

      expect(app.controller.status, AuthStatus.mustChangePassword);
    },
  );
}
