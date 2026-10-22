import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import '../support/fakes.dart';

void main() {
  test('load reads the profile once and force reloads it', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/me/profile', FakeReply(200, profileJson()));

    await app.profileController.load();
    await app.profileController.load();

    expect(app.profileController.profile!.fullName, 'Nguyễn Văn An');
    expect(app.http.count('GET', '/me/profile'), 1);

    await app.profileController.load(force: true);

    expect(app.http.count('GET', '/me/profile'), 2);
  });

  test('network error is kept for a retry', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.offline('GET', '/me/profile');

    await app.profileController.load();

    expect(app.profileController.profile, isNull);
    expect(app.profileController.error!.isNetworkError, isTrue);
    expect(app.profileController.isLoading, isFalse);

    app.http.reply('GET', '/me/profile', FakeReply(200, profileJson()));
    await app.profileController.load(force: true);

    expect(app.profileController.error, isNull);
    expect(app.profileController.profile, isNotNull);
  });

  test('reset drops the profile of the previous session', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    final reply = Completer<FakeReply>();
    app.http.on('GET', '/me/profile', (_) => reply.future);
    final loading = app.profileController.load();

    app.profileController.reset();
    reply.complete(FakeReply(200, profileJson()));
    await loading;

    expect(app.profileController.profile, isNull);
    expect(app.profileController.isLoading, isFalse);
  });
}
