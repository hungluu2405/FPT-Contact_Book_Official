import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';

import '../support/fakes.dart';

UserModel user(List<String> roles) =>
    UserModel.fromJson(userJson(roles: roles));

void main() {
  test('student views own data and never asks for children', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.stubHome(unread: 3);

    await app.homeController.start(user([RoleCodes.student]));

    expect(app.homeController.selectedStudentId, isNull);
    expect(app.homeController.hasStudentData, isTrue);
    expect(app.homeController.canSwitchStudent, isFalse);
    expect(app.homeController.unreadCount, 3);
    expect(app.http.count('GET', '/parent/children'), 0);
  });

  test('parent views the first linked child', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.stubHome(
      children: [
        childJson(id: 1),
        childJson(id: 2, code: 'SE190002'),
      ],
    );

    await app.homeController.start(user([RoleCodes.parent]));

    expect(app.homeController.selectedStudentId, 1);
    expect(app.homeController.canSwitchStudent, isTrue);

    app.homeController.selectChild(app.homeController.children.last);

    expect(app.homeController.selectedStudentId, 2);
  });

  test('parent with one child has nothing to switch', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.stubHome(children: [childJson(id: 1)]);

    await app.homeController.start(user([RoleCodes.parent]));

    expect(app.homeController.selectedStudentId, 1);
    expect(app.homeController.canSwitchStudent, isFalse);
  });

  test('parent without linked children has no student data', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.stubHome();

    await app.homeController.start(user([RoleCodes.parent]));

    expect(app.homeController.hasStudentData, isFalse);
    expect(app.homeController.selectedChild, isNull);
    expect(app.homeController.childrenError, isNull);
  });

  test('parent cannot pick "my own data"', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.stubHome(children: [childJson(id: 1)]);
    await app.homeController.start(user([RoleCodes.parent]));

    app.homeController.selectChild(null);

    expect(app.homeController.selectedStudentId, 1);
  });

  test(
    'student who is also a parent starts on own data and can switch',
    () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.stubHome(children: [childJson(id: 5)]);

      await app.homeController.start(
        user([RoleCodes.student, RoleCodes.parent]),
      );

      expect(app.homeController.selectedStudentId, isNull);
      expect(app.homeController.canSwitchStudent, isTrue);

      app.homeController.selectChild(app.homeController.children.single);
      app.homeController.selectChild(null);

      expect(app.homeController.selectedStudentId, isNull);
    },
  );

  test('network error on children can be retried', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.stubHome();
    app.http.offline('GET', '/parent/children');

    await app.homeController.start(user([RoleCodes.parent]));

    expect(app.homeController.childrenError!.isNetworkError, isTrue);
    expect(app.homeController.isLoadingChildren, isFalse);

    app.http.reply('GET', '/parent/children', FakeReply(200, [childJson()]));
    await app.homeController.refresh();

    expect(app.homeController.childrenError, isNull);
    expect(app.homeController.selectedStudentId, 1);
  });

  test('refresh keeps the selected child while still linked', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.stubHome(children: [childJson(id: 1), childJson(id: 2)]);
    await app.homeController.start(user([RoleCodes.parent]));
    app.homeController.selectChild(app.homeController.children.last);

    await app.homeController.refresh();
    expect(app.homeController.selectedStudentId, 2);

    app.stubHome(children: [childJson(id: 1)]);
    await app.homeController.refresh();
    expect(app.homeController.selectedStudentId, 1);
  });

  test('unread count error is ignored', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.offline('GET', '/notifications/unread-count');

    await app.homeController.start(user([RoleCodes.student]));

    expect(app.homeController.unreadCount, isNull);
  });

  test('admin-only account calls nothing', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');

    await app.homeController.start(user([RoleCodes.admin]));

    expect(app.http.requests, isEmpty);
  });

  test('a late reply of the previous session is dropped', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.stubHome();
    final oldReply = Completer<FakeReply>();
    app.http.on('GET', '/parent/children', (_) => oldReply.future);
    final first = app.homeController.start(user([RoleCodes.parent]));

    app.stubHome(children: [childJson(id: 9)]);
    await app.homeController.start(user([RoleCodes.parent]));
    oldReply.complete(FakeReply(200, [childJson(id: 1)]));
    await first;

    expect(app.homeController.selectedStudentId, 9);
    expect(app.homeController.isLoadingChildren, isFalse);
  });
}
