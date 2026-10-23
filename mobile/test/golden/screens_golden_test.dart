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

/// Ba buổi của thứ Tư 21/10/2026 như mockup; lúc 09:45 buổi TIN11 đang
/// diễn ra.
final List<Map<String, dynamic>> kGoldenToday = [
  sessionJson(),
  sessionJson(
    start: '09:30:00',
    end: '11:00:00',
    subjectCode: 'TIN11',
    subjectName: 'Tin học 11',
    room: 'BE202',
    teacher: 'TungVQ',
  ),
  sessionJson(
    start: '13:00:00',
    end: '14:30:00',
    subjectCode: 'ANH10',
    subjectName: 'Tiếng Anh 10',
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
                subjectCode: 'VAN10',
                subjectName: 'Ngữ văn 10',
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
              subjectCode: 'VAN10',
              subjectName: 'Ngữ văn 10',
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
              subjectCode: 'TOAN10',
              subjectName: 'Toán 10',
              room: 'DE205',
            ),
            examJson(id: 2, date: '2026-10-30'),
            examJson(
              id: 3,
              date: '2026-12-15',
              type: 'FINAL',
              subjectCode: 'ANH10',
              subjectName: 'Tiếng Anh 10',
            ),
            examJson(
              id: 4,
              date: '2026-12-28',
              type: 'RETAKE',
              subjectCode: 'TOAN10',
              subjectName: 'Toán 10',
            ),
          ]),
        ),
      ),
    );

    await expectScreen(tester, 'exam_list');
  });

  void stubGradesAndAttendance(TestApp app) {
    app.http.reply('GET', '/semesters', FakeReply(200, semestersJson()));
    app.http.reply(
      'GET',
      '/student/grades',
      FakeReply(
        200,
        gradesJson([
          gradeRowJson(),
          gradeRowJson(
            subjectCode: 'TIN11',
            subjectName: 'Tin học 11',
            scores: [9, 8.5, null],
            average: null,
          ),
          gradeRowJson(
            subjectCode: 'ANH10',
            subjectName: 'Tiếng Anh 10',
            scores: [7, 6.5, 8],
            average: 7.3,
          ),
        ]),
      ),
    );
    app.http.reply(
      'GET',
      '/student/attendance',
      FakeReply(
        200,
        attendanceJson([
          attendanceRowJson(
            summary: summaryJson(
              present: 9,
              excused: 1,
              unexcused: 0,
              rate: 90,
              absentRate: 6.7,
            ),
          ),
          attendanceRowJson(
            sectionId: 27,
            subjectCode: 'TIN11',
            subjectName: 'Tin học 11',
            summary: summaryJson(
              present: 6,
              excused: 1,
              unexcused: 3,
              rate: 60,
              absentRate: 26.7,
              warning: true,
            ),
          ),
        ]),
      ),
    );
    app.http.reply(
      'GET',
      '/student/attendance/1',
      FakeReply(
        200,
        attendanceHistoryJson(
          [
            attendanceSessionJson(),
            attendanceSessionJson(
              no: 2,
              date: '2026-10-07',
              note: 'Muộn 10 phút do xe hỏng',
            ),
            attendanceSessionJson(
              no: 3,
              date: '2026-10-12',
              status: 'ABSENT_EXCUSED',
            ),
            attendanceSessionJson(
              no: 4,
              date: '2026-10-14',
              sessionStatus: 'CANCELLED',
              status: null,
            ),
            attendanceSessionJson(
              no: 5,
              date: '2026-10-19',
              status: null,
              sessionStatus: 'SCHEDULED',
            ),
          ],
          row: attendanceRowJson(
            summary: summaryJson(
              present: 9,
              excused: 1,
              unexcused: 0,
              rate: 90,
              absentRate: 6.7,
            ),
          ),
        ),
      ),
    );
  }

  testWidgets('grades', (tester) async {
    await openFromHome(tester, 'Điểm', stub: stubGradesAndAttendance);

    await expectScreen(tester, 'grades');
  });

  testWidgets('grades with a subject opened', (tester) async {
    await openFromHome(tester, 'Điểm', stub: stubGradesAndAttendance);
    await tester.tap(find.text('Tin học 11'));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'grades_expanded');
  });

  testWidgets('attendance list', (tester) async {
    await openFromHome(tester, 'Điểm danh', stub: stubGradesAndAttendance);

    await expectScreen(tester, 'attendance_list');
  });

  testWidgets('attendance of one subject', (tester) async {
    await openFromHome(tester, 'Điểm danh', stub: stubGradesAndAttendance);
    await tester.tap(find.text('Toán 10'));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'attendance_detail');
  });

  void stubClubs(TestApp app) {
    app.http.reply(
      'GET',
      '/student/clubs',
      FakeReply(200, [
        myClubJson(),
        myClubJson(
          clubId: 2,
          code: 'F-GUITAR',
          name: 'Câu lạc bộ Âm nhạc F-Guitar',
          status: 'PENDING',
        ),
      ]),
    );
    app.http.reply(
      'GET',
      '/clubs',
      FakeReply(
        200,
        pageJson([
          clubJson(),
          clubJson(
            id: 2,
            code: 'F-GUITAR',
            name: 'Câu lạc bộ Âm nhạc F-Guitar',
            members: 1,
          ),
        ]),
      ),
    );
    app.http.reply(
      'GET',
      '/clubs/1',
      FakeReply(200, clubDetailJson(myStatus: 'ACTIVE', myRole: 'MEMBER')),
    );
    app.http.reply(
      'GET',
      '/clubs/1/activities',
      FakeReply(200, [
        clubActivityJson(myStatus: 'REGISTERED'),
        clubActivityJson(
          id: 2,
          title: 'Giao lưu dự án nhóm',
          start: '2026-10-31T08:00:00',
          end: '2026-10-31T11:00:00',
          max: null,
          taken: 8,
        ),
      ]),
    );
    app.http.reply(
      'GET',
      '/clubs/1/members',
      FakeReply(
        200,
        pageJson([
          clubMemberJson(),
          clubMemberJson(name: 'Vũ Thị Mai', role: 'MEMBER'),
        ]),
      ),
    );
  }

  testWidgets('club list', (tester) async {
    await openFromHome(tester, 'Câu lạc bộ', stub: stubClubs);

    await expectScreen(tester, 'club_list');
  });

  testWidgets('club detail', (tester) async {
    await openFromHome(tester, 'Câu lạc bộ', stub: stubClubs);
    await tester.tap(find.text('Câu lạc bộ Lập trình F-Code').first);
    await tester.pumpAndSettle();

    await expectScreen(tester, 'club_detail');
  });

  /// Hộp thư 4 thông báo: khẩn và quan trọng chưa đọc, hai thông báo đã đọc.
  void stubNotifications(TestApp app) {
    app.http.reply(
      'GET',
      '/notifications',
      FakeReply(
        200,
        pageJson([
          notificationJson(
            title: 'Nghỉ học do bão số 7 ngày 24/10',
            priority: 'URGENT',
            createdAt: '2026-10-21T07:15:00',
          ),
          notificationJson(
            id: 2,
            title: 'Lịch kiểm tra giữa kỳ I môn Toán',
            priority: 'IMPORTANT',
            createdAt: '2026-10-20T16:40:00',
          ),
          notificationJson(id: 3, read: true),
          notificationJson(
            id: 4,
            title: 'Họp phụ huynh đầu năm học 2026–2027',
            createdAt: '2026-09-12T10:00:00',
            read: true,
          ),
        ]),
      ),
    );
    app.http.reply(
      'GET',
      '/notifications/1',
      FakeReply(
        200,
        notificationDetailJson(
          title: 'Nghỉ học do bão số 7 ngày 24/10',
          priority: 'URGENT',
          files: [
            attachmentJson(fileName: 'cong-van-nghi-hoc.pdf'),
            attachmentJson(
              id: 12,
              fileName: 'lich-hoc-bu.xlsx',
              sizeBytes: 1572864,
            ),
          ],
        ),
      ),
    );
  }

  Future<void> openNotificationTab(WidgetTester tester) async {
    usePhoneScreen(tester);
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/me',
      FakeReply(200, userJson(fullName: 'Lưu Khánh Hưng')),
    );
    app.stubHome(unread: 2, today: kGoldenToday);
    stubNotifications(app);
    await pumpApp(tester, app: app);
    await tester.tap(
      find.descendant(
        of: find.byType(NavigationBar),
        matching: find.text('Thông báo'),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('notification list', (tester) async {
    await openNotificationTab(tester);

    await expectScreen(tester, 'notification_list');
  });

  testWidgets('notification detail with attachments', (tester) async {
    await openNotificationTab(tester);
    await tester.tap(find.text('Nghỉ học do bão số 7 ngày 24/10'));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'notification_detail');
  });

  /// Ba đơn của HS: đơn nghỉ chờ duyệt, đơn nghỉ đã duyệt (có minh chứng và
  /// ghi chú của GV), đơn xin bảng điểm bị từ chối.
  void stubRequests(TestApp app) {
    app.http.reply(
      'GET',
      '/student/requests',
      FakeReply(
        200,
        pageJson([
          requestJson(),
          requestJson(
            id: 2,
            title: 'Xin nghỉ đi khám răng',
            status: 'APPROVED',
            fromDate: '2026-10-12',
            toDate: '2026-10-12',
          ),
          requestJson(
            id: 3,
            type: 'TRANSCRIPT',
            title: 'Xin bảng điểm năm học 2025–2026',
            status: 'REJECTED',
            fromDate: null,
            toDate: null,
          ),
        ]),
      ),
    );
    app.http.reply(
      'GET',
      '/student/requests/2',
      FakeReply(
        200,
        requestDetailJson(
          id: 2,
          status: 'APPROVED',
          canCancel: false,
          files: [
            attachmentJson(
              fileName: 'giay-hen-nha-khoa.jpg',
              sizeBytes: 812000,
            ),
          ],
          reviews: [reviewJson()],
        ),
      ),
    );
    app.http.reply(
      'GET',
      '/student/requests/leave-options',
      FakeReply(200, leaveOptionsJson()),
    );
  }

  testWidgets('request list', (tester) async {
    await openFromHome(tester, 'Đơn từ', stub: stubRequests);

    await expectScreen(tester, 'request_list');
  });

  testWidgets('request detail with a review', (tester) async {
    await openFromHome(tester, 'Đơn từ', stub: stubRequests);
    await tester.tap(find.text('Xin nghỉ đi khám răng'));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'request_detail');
  });

  testWidgets('leave form', (tester) async {
    await openFromHome(tester, 'Đơn từ', stub: stubRequests);
    await tester.tap(find.text('Gửi đơn nghỉ'));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'leave_form');
  });

  testWidgets('leave form with errors', (tester) async {
    final app = await openFromHome(tester, 'Đơn từ', stub: stubRequests);
    await tester.tap(find.text('Gửi đơn nghỉ'));
    await tester.pumpAndSettle();
    app.pickedFiles = [pickedFile('giay-kham-benh.pdf', size: 245760)];
    await tester.ensureVisible(find.text('Chọn tệp'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Chọn tệp'));
    await tester.pumpAndSettle();
    final submit = find.widgetWithText(FilledButton, 'Gửi đơn');
    await tester.ensureVisible(submit);
    await tester.pumpAndSettle();
    await tester.tap(submit);
    await tester.pumpAndSettle();
    // Về đầu form để thấy lỗi của các ô ngày.
    await tester.drag(find.byType(ListView).last, const Offset(0, 2000));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'leave_form_errors');
  });

  /// Tổ trưởng tổ Toán – Tin học (cũng là giáo viên) đã đăng nhập.
  Future<TestApp> pumpHead(WidgetTester tester) async {
    usePhoneScreen(tester);
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/me',
      FakeReply(
        200,
        userJson(
          id: 2,
          fullName: 'Trần Thị Giáo',
          roles: ['TEACHER', 'HEAD_OF_DEPARTMENT'],
        ),
      ),
    );
    app.stubHome(unread: 1);
    app.http.reply(
      'GET',
      '/head/departments',
      FakeReply(200, [departmentJson()]),
    );
    app.http.reply(
      'GET',
      '/head/teachers',
      FakeReply(
        200,
        pageJson([
          teacherJson(
            userId: 2,
            code: 'GV001',
            fullName: 'Trần Thị Giáo',
            phone: '0900000002',
            head: true,
          ),
          teacherJson(),
          teacherJson(
            userId: 16,
            code: 'GV003',
            fullName: 'Hoàng Văn Code',
            phone: '0922334455',
          ),
          teacherJson(
            userId: 17,
            code: 'GV004',
            fullName: 'Đỗ Thị Thuật',
            phone: '0922334456',
            active: false,
          ),
        ]),
      ),
    );
    app.http.reply(
      'GET',
      '/head/teachers/3',
      FakeReply(200, teacherDetailJson()),
    );
    await pumpApp(tester, app: app);
    return app;
  }

  Future<void> openTeacherList(WidgetTester tester) async {
    final tile = find.text('Giáo viên trong tổ');
    await tester.ensureVisible(tile);
    await tester.pumpAndSettle();
    await tester.tap(tile);
    await tester.pumpAndSettle();
  }

  testWidgets('head of department home', (tester) async {
    await pumpHead(tester);
    final panel = find.text('Tổ chuyên môn');
    await tester.ensureVisible(panel);
    await tester.pumpAndSettle();

    await expectScreen(tester, 'home_head');
  });

  testWidgets('teacher list of the department', (tester) async {
    await pumpHead(tester);
    await openTeacherList(tester);

    await expectScreen(tester, 'teacher_list');
  });

  testWidgets('teacher detail', (tester) async {
    await pumpHead(tester);
    await openTeacherList(tester);
    await tester.tap(find.text('Nguyễn Thị Giáo'));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'teacher_detail');
  });

  /// Thông báo đã gửi của tổ trưởng: một cho cả tổ, một gửi riêng.
  void stubSent(TestApp app) {
    app.http.reply(
      'GET',
      '/notifications/sent',
      FakeReply(
        200,
        pageJson([
          sentJson(),
          sentJson(
            id: 71,
            title: 'Nhắc nộp đề kiểm tra giữa kỳ',
            priority: 'NORMAL',
            targetType: 'USER',
            recipients: 1,
            read: 0,
          ),
        ]),
      ),
    );
    app.http.reply(
      'GET',
      '/notifications/sent/70',
      FakeReply(
        200,
        sentDetailJson(
          files: [attachmentJson(fileName: 'lich-du-gio-thang-11.pdf')],
        ),
      ),
    );
  }

  testWidgets('department notification form', (tester) async {
    final app = await pumpHead(tester);
    final action = find.text('Gửi thông báo tổ');
    await tester.ensureVisible(action);
    await tester.pumpAndSettle();
    await tester.tap(action);
    await tester.pumpAndSettle();
    await enter(tester, 'Tiêu đề', 'Họp tổ chuyên môn tháng 11');
    await enter(
      tester,
      'Nội dung',
      'Kính gửi các thầy cô: họp tổ lúc 14:00 thứ Năm tại phòng 204.',
    );
    await tester.tap(find.text('Quan trọng'));
    app.pickedFiles = [pickedFile('lich-du-gio-thang-11.pdf', size: 245760)];
    await tester.ensureVisible(find.text('Chọn tệp'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Chọn tệp'));
    FocusManager.instance.primaryFocus?.unfocus();
    await tester.pumpAndSettle();
    await tester.drag(find.byType(ListView).last, const Offset(0, 2000));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'department_notification_form');
  });

  testWidgets('sent notification list', (tester) async {
    final app = await pumpHead(tester);
    stubSent(app);
    final action = find.text('Thông báo đã gửi');
    await tester.ensureVisible(action);
    await tester.pumpAndSettle();
    await tester.tap(action);
    await tester.pumpAndSettle();

    await expectScreen(tester, 'sent_notification_list');
  });

  testWidgets('sent notification detail with read stats', (tester) async {
    final app = await pumpHead(tester);
    stubSent(app);
    final action = find.text('Thông báo đã gửi');
    await tester.ensureVisible(action);
    await tester.pumpAndSettle();
    await tester.tap(action);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Họp tổ chuyên môn tháng 11'));
    await tester.pumpAndSettle();

    await expectScreen(tester, 'sent_notification_detail');
  });

  /// Giáo viên thường đã đăng nhập; hôm nay dạy ba buổi như [kGoldenToday].
  Future<TestApp> pumpTeacherHome(WidgetTester tester) async {
    usePhoneScreen(tester);
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/me',
      FakeReply(
        200,
        userJson(id: 3, fullName: 'Nguyễn Thị Giáo', roles: ['TEACHER']),
      ),
    );
    app.stubHome(unread: 2, today: kGoldenToday);
    await pumpApp(tester, app: app);
    return app;
  }

  testWidgets('teacher home', (tester) async {
    await pumpTeacherHome(tester);
    await tester.drag(
      find.byType(SingleChildScrollView).first,
      const Offset(0, -300),
    );
    await tester.pumpAndSettle();

    await expectScreen(tester, 'home_teacher');
  });

  testWidgets('teaching schedule of today', (tester) async {
    await pumpTeacherHome(tester);
    final tile = find.text('Lịch dạy');
    await tester.ensureVisible(tile);
    await tester.pumpAndSettle();
    await tester.tap(tile);
    await tester.pumpAndSettle();

    await expectScreen(tester, 'teaching_schedule');
  });
}
