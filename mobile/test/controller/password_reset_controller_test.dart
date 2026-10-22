import 'package:flutter_test/flutter_test.dart';

import '../support/fakes.dart';

void main() {
  test('requestCode sends the trimmed phone without a token', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.on('POST', '/auth/password-reset/request', (request) async {
      expect(request.data, {'phone': '0900000004'});
      expect(request.headers['Authorization'], isNull);
      return const FakeReply(202);
    });

    final ok = await app.resetController.requestCode(' 0900000004 ');

    expect(ok, isTrue);
    expect(app.resetController.phone, '0900000004');
    expect(app.resetController.isLoading, isFalse);
  });

  test('requestCode keeps the server message on the phone field', () async {
    final app = TestApp();
    app.http.reply(
      'POST',
      '/auth/password-reset/request',
      FakeReply.error(
        400,
        'BUSINESS_ERROR',
        'Bạn vừa yêu cầu mã, vui lòng đợi ít phút rồi thử lại',
        fieldErrors: [
          {
            'field': 'phone',
            'message': 'Bạn vừa yêu cầu mã, vui lòng đợi ít phút rồi thử lại',
          },
        ],
      ),
    );

    final ok = await app.resetController.requestCode('0900000004');

    expect(ok, isFalse);
    expect(app.resetController.fieldErrors.keys, ['phone']);
  });

  test('resetPassword sends the phone of the request and the code', () async {
    final app = TestApp();
    app.http.reply(
      'POST',
      '/auth/password-reset/request',
      const FakeReply(202),
    );
    app.http.on('POST', '/auth/password-reset/confirm', (request) async {
      expect(request.data, {
        'phone': '0900000004',
        'code': '042917',
        'newPassword': 'Moi@12345',
        'confirmPassword': 'Moi@12345',
      });
      expect(request.headers['Authorization'], isNull);
      return const FakeReply(204);
    });
    await app.resetController.requestCode('0900000004');

    final ok = await app.resetController.resetPassword(
      code: ' 042917 ',
      newPassword: 'Moi@12345',
      confirmPassword: 'Moi@12345',
    );

    expect(ok, isTrue);
    expect(app.storage.accessToken, isNull);
  });

  test(
    'wrong code error is kept on the code field and can be cleared',
    () async {
      final app = TestApp();
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

      final ok = await app.resetController.resetPassword(
        code: '000000',
        newPassword: 'Moi@12345',
        confirmPassword: 'Moi@12345',
      );

      expect(ok, isFalse);
      expect(
        app.resetController.fieldErrors['code'],
        'Mã xác nhận không đúng hoặc đã hết hạn',
      );

      app.resetController.clearErrors();
      expect(app.resetController.fieldErrors, isEmpty);
      expect(app.resetController.errorMessage, isNull);
    },
  );

  test('network error is reported without field errors', () async {
    final app = TestApp();
    app.http.offline('POST', '/auth/password-reset/request');

    final ok = await app.resetController.requestCode('0900000004');

    expect(ok, isFalse);
    expect(app.resetController.errorMessage, contains('Không kết nối'));
    expect(app.resetController.fieldErrors, isEmpty);
  });
}
