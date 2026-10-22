import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/notification_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/profile_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';

import '../support/fakes.dart';

void main() {
  test('rootPath picks /student or the child of the parent', () {
    expect(StudentService.rootPath(null), '/student');
    expect(StudentService.rootPath(7), '/parent/children/7');
  });

  test('fetchChildren sends the access token and reads every child', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/parent/children',
      FakeReply(200, [
        childJson(id: 1),
        childJson(id: 2, code: 'SE190002', name: 'Nguyễn Thị Bình'),
      ]),
    );

    final children = await StudentService(app.apiClient).fetchChildren();

    expect(children.map((child) => child.id), [1, 2]);
    expect(children.last.name, 'Nguyễn Thị Bình');
    expect(app.http.requests.single.headers['Authorization'], 'Bearer a');
  });

  test('fetchChildren of an account that is not a parent is 403', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/parent/children',
      FakeReply.error(403, 'FORBIDDEN', 'Bạn không có quyền'),
    );

    expect(
      StudentService(app.apiClient).fetchChildren(),
      throwsA(isA<ApiErrorModel>().having((e) => e.code, 'code', 'FORBIDDEN')),
    );
  });

  test('fetchProfile reads GET /me/profile', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/me/profile', FakeReply(200, profileJson()));

    final profile = await ProfileService(app.apiClient).fetchProfile();

    expect(profile.fullName, 'Nguyễn Văn An');
  });

  test('fetchUnreadCount reads the count', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/notifications/unread-count',
      const FakeReply(200, {'count': 12}),
    );

    expect(await NotificationService(app.apiClient).fetchUnreadCount(), 12);
  });
}
