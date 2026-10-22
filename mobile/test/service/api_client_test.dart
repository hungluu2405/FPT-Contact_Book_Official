import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';

import '../support/fakes.dart';

void main() {
  late TestApp app;

  setUp(() {
    app = TestApp(accessToken: 'old-access', refreshToken: 'old-refresh');
  });

  String? authHeader(int index) =>
      app.http.requests[index].headers['Authorization'] as String?;

  test('attaches the access token to authenticated requests only', () async {
    app.http.reply('GET', '/me', FakeReply(200, userJson()));
    app.http.reply('POST', '/auth/login', FakeReply(200, tokenJson()));

    await app.apiClient.get('/me');
    await app.apiClient.post('/auth/login', data: {}, authenticated: false);

    expect(authHeader(0), 'Bearer old-access');
    expect(authHeader(1), isNull);
  });

  test('refreshes once on 401 and resends the request', () async {
    app.http.on(
      'GET',
      '/me',
      (request) async => request.headers['Authorization'] == 'Bearer new-access'
          ? FakeReply(200, userJson())
          : FakeReply.error(401, 'UNAUTHORIZED', 'Hết hạn'),
    );
    app.http.on('POST', '/auth/refresh', (request) async {
      expect(request.data, {'refreshToken': 'old-refresh'});
      expect(request.headers['Authorization'], isNull);
      return FakeReply(
        200,
        tokenJson(access: 'new-access', refresh: 'new-refresh'),
      );
    });

    final data = await app.apiClient.get('/me');

    expect((data as Map<String, dynamic>)['id'], 4);
    expect(app.http.count('POST', '/auth/refresh'), 1);
    expect(app.http.count('GET', '/me'), 2);
    expect(app.storage.accessToken, 'new-access');
    expect(app.storage.refreshToken, 'new-refresh');
  });

  test(
    'requests failing with 401 at the same time share one refresh',
    () async {
      app.http.on(
        'GET',
        '/me',
        (request) async =>
            request.headers['Authorization'] == 'Bearer new-access'
            ? FakeReply(200, userJson())
            : FakeReply.error(401, 'UNAUTHORIZED', 'Hết hạn'),
      );
      app.http.on('POST', '/auth/refresh', (_) async {
        await Future<void>.delayed(const Duration(milliseconds: 20));
        return FakeReply(
          200,
          tokenJson(access: 'new-access', refresh: 'new-refresh'),
        );
      });

      await Future.wait([
        app.apiClient.get('/me'),
        app.apiClient.get('/me'),
        app.apiClient.get('/me'),
      ]);

      expect(app.http.count('POST', '/auth/refresh'), 1);
      expect(app.http.count('GET', '/me'), 6);
    },
  );

  test(
    'rejected refresh clears the tokens and reports an expired session',
    () async {
      var expired = 0;
      app.apiClient.onSessionExpired = () => expired++;
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

      await expectLater(
        app.apiClient.get('/me'),
        throwsA(
          isA<ApiErrorModel>().having(
            (e) => e.code,
            'code',
            ApiErrorModel.unauthorized,
          ),
        ),
      );
      expect(expired, 1);
      expect(app.storage.accessToken, isNull);
      expect(app.storage.refreshToken, isNull);
    },
  );

  test('refresh without network keeps the tokens', () async {
    var expired = 0;
    app.apiClient.onSessionExpired = () => expired++;
    app.http.reply(
      'GET',
      '/me',
      FakeReply.error(401, 'UNAUTHORIZED', 'Hết hạn'),
    );
    app.http.offline('POST', '/auth/refresh');

    await expectLater(
      app.apiClient.get('/me'),
      throwsA(
        isA<ApiErrorModel>().having((e) => e.isNetworkError, 'network', true),
      ),
    );
    expect(expired, 0);
    expect(app.storage.refreshToken, 'old-refresh');
  });

  test('wrong password on login does not try to refresh', () async {
    app.http.reply(
      'POST',
      '/auth/login',
      FakeReply.error(
        401,
        'BAD_CREDENTIALS',
        'Số điện thoại hoặc mật khẩu không đúng',
      ),
    );

    await expectLater(
      app.apiClient.post('/auth/login', data: {}, authenticated: false),
      throwsA(
        isA<ApiErrorModel>().having(
          (e) => e.message,
          'message',
          'Số điện thoại hoặc mật khẩu không đúng',
        ),
      ),
    );
    expect(app.http.count('POST', '/auth/refresh'), 0);
  });

  test('PASSWORD_CHANGE_REQUIRED is reported to the listener', () async {
    var required = 0;
    app.apiClient.onPasswordChangeRequired = () => required++;
    app.http.reply(
      'GET',
      '/semesters',
      FakeReply.error(403, 'PASSWORD_CHANGE_REQUIRED', 'Phải đổi mật khẩu'),
    );

    await expectLater(
      app.apiClient.get('/semesters'),
      throwsA(isA<ApiErrorModel>()),
    );
    expect(required, 1);
  });
}
