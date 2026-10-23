import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/app_driver.dart';
import '../support/fakes.dart';

/// Kích thước màn hình Pixel 6a (411x914 điểm logic), vẽ ở tỉ lệ 1.5 cho
/// ảnh đủ rõ mà file nhỏ. Cập nhật ảnh mẫu:
/// `flutter test --update-goldens test/golden`.
const Size kPhoneSize = Size(411, 914);
const double kPixelRatio = 1.5;

void usePhoneScreen(WidgetTester tester) {
  tester.view.devicePixelRatio = kPixelRatio;
  tester.view.physicalSize = kPhoneSize * kPixelRatio;
  addTearDown(tester.view.reset);
}

/// Ảnh asset được giải mã ngoài đồng hồ giả của test; không có bước này
/// logo sẽ trống trong ảnh chụp.
Future<void> loadImages(WidgetTester tester) async {
  await tester.runAsync(() async {
    for (final element in find.byType(Image).evaluate()) {
      final image = element.widget as Image;
      await precacheImage(image.image, element);
    }
  });
  await tester.pumpAndSettle();
}

Future<void> expectScreen(WidgetTester tester, String name) async {
  await loadImages(tester);
  await expectLater(
    find.byType(MaterialApp),
    matchesGoldenFile('goldens/$name.png'),
  );
}

/// Ba buổi của thứ Tư 21/10/2026 như mockup; lúc 09:45 buổi PRM393 đang
/// diễn ra.
final List<Map<String, dynamic>> kGoldenToday = [
  sessionJson(),
  sessionJson(
    start: '09:30:00',
    end: '11:00:00',
    subjectCode: 'PRM393',
    subjectName: 'Lập trình di động',
    room: 'BE202',
    teacher: 'TungVQ',
  ),
  sessionJson(
    start: '13:00:00',
    end: '14:30:00',
    subjectCode: 'JPD113',
    subjectName: 'Tiếng Nhật sơ cấp 1',
    room: 'BE205',
    teacher: 'HaTH',
  ),
];

/// SV đã đăng nhập, mở màn hình qua ô [tile] trên trang chủ.
Future<TestApp> openFromHome(
  WidgetTester tester,
  String tile, {
  void Function(TestApp app)? stub,
}) async {
  usePhoneScreen(tester);
  final app = TestApp(accessToken: 'a', refreshToken: 'r');
  app.http.reply(
    'GET',
    '/me',
    FakeReply(200, userJson(fullName: 'Lưu Khánh Hưng')),
  );
  app.stubHome(today: kGoldenToday);
  stub?.call(app);
  await pumpApp(tester, app: app);
  final target = find.text(tile).last;
  await tester.ensureVisible(target);
  await tester.tap(target);
  await tester.pumpAndSettle();
  return app;
}

void main() {
  testWidgets('login screen', (tester) async {
    usePhoneScreen(tester);
    await pumpApp(tester);

    await expectScreen(tester, 'login_empty');
  });

  testWidgets('login screen with required field messages', (tester) async {
    usePhoneScreen(tester);
    await pumpApp(tester);

    await tapButton(tester, 'Đăng nhập');

    await expectScreen(tester, 'login_required_fields');
  });

  testWidgets('login screen with a server error', (tester) async {
    usePhoneScreen(tester);
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
    await enter(tester, 'Mật khẩu', 'saimatkhau');
    await tapButton(tester, 'Đăng nhập');

    await expectScreen(tester, 'login_server_error');
  });

  testWidgets('forced password change with field errors', (tester) async {
    usePhoneScreen(tester);
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/me',
      FakeReply(200, userJson(mustChangePassword: true)),
    );
    app.http.reply(
      'POST',
      '/me/password',
      FakeReply.error(
        400,
        'BUSINESS_ERROR',
        'Mật khẩu nhập lại không khớp',
        fieldErrors: [
          {
            'field': 'confirmPassword',
            'message': 'Mật khẩu nhập lại không khớp',
          },
        ],
      ),
    );
    await pumpApp(tester, app: app);

    await enter(tester, 'Mật khẩu hiện tại', 'Tam@12345');
    await enter(tester, 'Mật khẩu mới', 'Moi@12345');
    await enter(tester, 'Nhập lại mật khẩu mới', 'Khac@12345');
    await tapButton(tester, 'Đổi mật khẩu');
    await tester.scrollUntilVisible(
      find.text('Đổi mật khẩu').first,
      -200,
      scrollable: find.byType(Scrollable).first,
    );

    await expectScreen(tester, 'change_password_errors');
  });

  testWidgets('student home', (tester) async {
    usePhoneScreen(tester);
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/me',
      FakeReply(200, userJson(fullName: 'Lê Minh Sinh')),
    );
    app.stubHome(unread: 3, today: kGoldenToday);
    await pumpApp(tester, app: app);

    await expectScreen(tester, 'home_student');
  });

  testWidgets('parent home with one child', (tester) async {
    usePhoneScreen(tester);
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/me',
      FakeReply(200, userJson(fullName: 'Lê Văn Phụ', roles: ['PARENT'])),
    );
    app.stubHome(children: [childJson(name: 'Lê Minh Sinh')]);
    await pumpApp(tester, app: app);

    await expectScreen(tester, 'home_parent_one_child');
  });

  testWidgets('parent picks one of two children', (tester) async {
    usePhoneScreen(tester);
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/me',
      FakeReply(200, userJson(fullName: 'Lê Văn Phụ', roles: ['PARENT'])),
    );
    app.stubHome(
      unread: 12,
      children: [
        childJson(id: 1, name: 'Lê Minh Sinh'),
        childJson(id: 2, code: 'SE190002', name: 'Lê Thu Hà'),
      ],
    );
    await pumpApp(tester, app: app);

    await tester.tap(find.byIcon(Icons.arrow_drop_down));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'home_parent_child_picker');
  });

  testWidgets('parent home without linked children', (tester) async {
    usePhoneScreen(tester);
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/me',
      FakeReply(200, userJson(fullName: 'Lê Văn Phụ', roles: ['PARENT'])),
    );
    app.stubHome();
    await pumpApp(tester, app: app);

    await expectScreen(tester, 'home_parent_no_children');
  });

  testWidgets('account tab', (tester) async {
    usePhoneScreen(tester);
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/me',
      FakeReply(200, userJson(fullName: 'Lê Minh Sinh')),
    );
    app.http.reply(
      'GET',
      '/me/profile',
      FakeReply(200, profileJson(fullName: 'Lê Minh Sinh')),
    );
    app.stubHome();
    await pumpApp(tester, app: app);

    await tester.tap(find.text('Tài khoản'));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'account_tab');
  });

  testWidgets('admin-only home', (tester) async {
    usePhoneScreen(tester);
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/me',
      FakeReply(200, userJson(fullName: 'Quản trị hệ thống', roles: ['ADMIN'])),
    );
    await pumpApp(tester, app: app);

    await expectScreen(tester, 'home_admin');
  });

  testWidgets('logout confirmation dialog', (tester) async {
    usePhoneScreen(tester);
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/me',
      FakeReply(200, userJson(fullName: 'Lê Văn Phụ', roles: ['PARENT'])),
    );
    app.http.reply(
      'GET',
      '/me/profile',
      FakeReply(200, profileJson(fullName: 'Lê Văn Phụ')),
    );
    app.stubHome(children: [childJson(name: 'Lê Minh Sinh')]);
    await pumpApp(tester, app: app);
    await tester.tap(find.text('Tài khoản'));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(OutlinedButton, 'Đăng xuất'));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'logout_dialog');
  });

  testWidgets('forgot password screen', (tester) async {
    usePhoneScreen(tester);
    await pumpApp(tester);

    await tester.tap(find.widgetWithText(TextButton, 'Quên mật khẩu?'));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'forgot_password');
  });

  testWidgets('reset password screen after the code is sent', (tester) async {
    usePhoneScreen(tester);
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
    await tester.tap(find.widgetWithText(TextButton, 'Quên mật khẩu?'));
    await tester.pumpAndSettle();
    await enter(tester, 'Số điện thoại', '0900000004');
    await tapButton(tester, 'Gửi mã xác nhận');

    await expectScreen(tester, 'reset_password_sent');

    await enter(tester, 'Mã xác nhận (6 chữ số)', '000000');
    await enter(tester, 'Mật khẩu mới', 'Moi@12345');
    await enter(tester, 'Nhập lại mật khẩu mới', 'Moi@12345');
    await tapButton(tester, 'Đặt lại mật khẩu');
    await tester.scrollUntilVisible(
      find.text('Đặt lại mật khẩu').first,
      -200,
      scrollable: find.byType(Scrollable).first,
    );

    await expectScreen(tester, 'reset_password_wrong_code');
  });

  testWidgets('network error on startup', (tester) async {
    usePhoneScreen(tester);
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.offline('GET', '/me');
    await pumpApp(tester, app: app);

    await expectScreen(tester, 'startup_network_error');
  });

  testWidgets('schedule today with an ongoing session', (tester) async {
    await openFromHome(tester, 'Lịch học');

    await expectScreen(tester, 'schedule_today');
  });

  testWidgets('schedule of a day without sessions', (tester) async {
    await openFromHome(tester, 'Lịch học');
    await tester.tap(find.text('22'));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'schedule_empty_day');
  });

  testWidgets('schedule of next week', (tester) async {
    final app = await openFromHome(tester, 'Lịch học');
    app.http.reply(
      'GET',
      '/student/schedule/week',
      FakeReply(
        200,
        weekJson(
          weekStart: '2026-10-26',
          today: null,
          items: {
            '2026-10-27': [
              sessionJson(
                date: '2026-10-27',
                subjectCode: 'SWE201',
                subjectName: 'Nhập môn kỹ thuật phần mềm',
                status: 'MAKEUP',
                note: 'Học bù cho buổi 13/10',
              ),
            ],
          },
        ),
      ),
    );
    await tester.tap(find.byTooltip('Tuần sau'));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'schedule_other_week');
  });

  testWidgets('schedule network error', (tester) async {
    await openFromHome(
      tester,
      'Lịch học',
      stub: (app) => app.http.offline('GET', '/student/schedule/week'),
    );

    await expectScreen(tester, 'schedule_error');
  });

  testWidgets('semester session list', (tester) async {
    await openFromHome(
      tester,
      'Lịch học',
      stub: (app) => app.http.reply(
        'GET',
        '/student/schedule/sessions',
        FakeReply(
          200,
          pagedJson([
            ...kGoldenToday,
            sessionJson(
              date: '2026-10-22',
              subjectCode: 'SWE201',
              subjectName: 'Nhập môn kỹ thuật phần mềm',
              status: 'CANCELLED',
            ),
          ]),
        ),
      ),
    );
    await tester.tap(find.text('Xem lịch'));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'session_list');
  });

  testWidgets('exam list', (tester) async {
    await openFromHome(
      tester,
      'Lịch thi',
      stub: (app) => app.http.reply(
        'GET',
        '/student/exams',
        FakeReply(
          200,
          pagedJson([
            examJson(
              id: 1,
              date: '2026-10-12',
              subjectCode: 'MAD101',
              subjectName: 'Toán rời rạc',
              room: 'DE205',
            ),
            examJson(id: 2, date: '2026-10-30'),
            examJson(
              id: 3,
              date: '2026-12-15',
              type: 'FINAL',
              subjectCode: 'JPD113',
              subjectName: 'Tiếng Nhật sơ cấp 1',
            ),
            examJson(
              id: 4,
              date: '2026-12-28',
              type: 'RETAKE',
              subjectCode: 'MAD101',
              subjectName: 'Toán rời rạc',
            ),
          ]),
        ),
      ),
    );

    await expectScreen(tester, 'exam_list');
  });
}
