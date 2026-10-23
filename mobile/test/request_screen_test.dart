import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/leave_rule.dart';

import 'support/app_driver.dart';
import 'support/fakes.dart';

/// HS (hoặc PH có một con khi [parent]) mở Đơn từ từ trang chủ.
Future<TestApp> openRequests(
  WidgetTester tester, {
  bool parent = false,
  List<Map<String, dynamic>>? items,
  void Function(TestApp app)? stub,
}) async {
  final app = TestApp(accessToken: 'a', refreshToken: 'r');
  addTearDown(app.deleteAttachmentDir);
  final root = parent ? '/parent/children/1' : '/student';
  app.http.reply(
    'GET',
    '/me',
    FakeReply(200, userJson(roles: [parent ? 'PARENT' : 'STUDENT'])),
  );
  app.stubHome(children: parent ? [childJson()] : const []);
  app.http.reply(
    'GET',
    '$root/requests',
    FakeReply(
      200,
      pageJson(
        items ??
            [
              requestJson(),
              requestJson(
                id: 2,
                type: 'TRANSCRIPT',
                title: 'Xin bảng điểm học kỳ I',
                status: 'APPROVED',
                fromDate: null,
                toDate: null,
              ),
            ],
      ),
    ),
  );
  app.http.reply(
    'GET',
    '$root/requests/leave-options',
    FakeReply(200, leaveOptionsJson()),
  );
  stub?.call(app);
  await pumpApp(tester, app: app);
  await tester.ensureVisible(find.text('Đơn từ'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Đơn từ'));
  await tester.pumpAndSettle();
  return app;
}

Future<void> openLeaveForm(WidgetTester tester) async {
  await tester.tap(find.text('Gửi đơn nghỉ'));
  await tester.pumpAndSettle();
}

/// Chọn ngày [day] của tháng 10/2026 trong lịch của ô [label].
Future<void> pickDay(WidgetTester tester, String label, int day) async {
  final input = find.descendant(
    of: field(label),
    matching: find.byType(InputDecorator),
  );
  await tester.ensureVisible(input);
  await tester.pumpAndSettle();
  await tester.tap(input);
  await tester.pumpAndSettle();
  await tester.tap(
    find.descendant(of: find.byType(Dialog), matching: find.text('$day')),
  );
  await tester.tap(find.text('OK'));
  await tester.pumpAndSettle();
}

Future<void> tapSubmit(WidgetTester tester) async {
  final button = find.widgetWithText(FilledButton, 'Gửi đơn');
  await tester.ensureVisible(button);
  await tester.pumpAndSettle();
  await tester.tap(button);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('the list shows status, type and leave dates', (tester) async {
    await openRequests(tester);

    expect(find.text('Đơn của tôi'), findsOneWidget);
    expect(find.text('Chờ duyệt'), findsOneWidget);
    expect(find.text('Đã duyệt'), findsOneWidget);
    expect(find.text('Xin nghỉ học'), findsOneWidget);
    expect(find.text('Xin bảng điểm'), findsOneWidget);
    expect(find.text('Nghỉ: 22/10/2026 – 23/10/2026'), findsOneWidget);
    expect(find.text('Gửi lúc 21/10/2026 08:15'), findsOneWidget);
  });

  testWidgets('a parent sees the requests of the child', (tester) async {
    final app = await openRequests(tester, parent: true);

    expect(find.text('Đơn của con'), findsOneWidget);
    expect(app.http.count('GET', '/parent/children/1/requests'), 1);
  });

  testWidgets('an empty list shows the empty state', (tester) async {
    await openRequests(tester, items: const []);

    expect(find.text('Chưa có đơn nào.'), findsOneWidget);
  });

  testWidgets('the detail shows reviews, files and who sent it', (
    tester,
  ) async {
    final app = await openRequests(
      tester,
      stub: (app) {
        app.http.reply(
          'GET',
          '/student/requests/1',
          FakeReply(
            200,
            requestDetailJson(
              sentByParent: true,
              canCancel: false,
              status: 'APPROVED',
              files: [attachmentJson(fileName: 'giay-kham.pdf')],
              reviews: [reviewJson()],
            ),
          ),
        );
        app.http.reply(
          'GET',
          '/requests/1/files/11',
          FakeReply(200, Uint8List.fromList([1])),
        );
      },
    );

    await tester.tap(find.textContaining('Đơn xin nghỉ học từ 22/10'));
    await tester.pumpAndSettle();

    expect(find.text('Phụ huynh gửi thay'), findsOneWidget);
    expect(find.text('Tất cả các lớp'), findsOneWidget);
    expect(find.text('Đã nhận giấy khám bệnh'), findsOneWidget);
    expect(find.text('Hủy đơn'), findsNothing);

    await tester.ensureVisible(find.text('giay-kham.pdf'));

    await tester.pumpAndSettle();
    await tester.tap(find.text('giay-kham.pdf'));
    await tester.pumpAndSettle();
    expect(app.openedFiles.single, endsWith('giay-kham.pdf'));
  });

  testWidgets('cancel asks first and then marks the request cancelled', (
    tester,
  ) async {
    final app = await openRequests(
      tester,
      stub: (app) {
        app.http.reply(
          'GET',
          '/student/requests/1',
          FakeReply(200, requestDetailJson()),
        );
        app.http.reply(
          'POST',
          '/student/requests/1/cancel',
          FakeReply(
            200,
            requestDetailJson(status: 'CANCELLED', canCancel: false),
          ),
        );
      },
    );
    await tester.tap(find.textContaining('Đơn xin nghỉ học từ 22/10'));
    await tester.pumpAndSettle();
    final cancel = find.widgetWithText(OutlinedButton, 'Hủy đơn');
    await tester.ensureVisible(cancel);
    await tester.pumpAndSettle();

    await tester.tap(cancel);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Không'));
    await tester.pumpAndSettle();
    expect(app.http.count('POST', '/student/requests/1/cancel'), 0);

    await tester.tap(cancel);
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Hủy đơn'));
    await tester.pumpAndSettle();

    expect(app.http.count('POST', '/student/requests/1/cancel'), 1);
    expect(find.text('Đã hủy đơn.'), findsOneWidget);
    expect(find.text('Đã hủy'), findsOneWidget);
    expect(find.widgetWithText(OutlinedButton, 'Hủy đơn'), findsNothing);

    // Nút back của hệ thống quay lại danh sách.
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.text('Đơn của tôi'), findsOneWidget);
    expect(find.text('Đã hủy'), findsOneWidget);
  });

  testWidgets('an empty leave form shows errors next to the fields', (
    tester,
  ) async {
    final app = await openRequests(tester);
    await openLeaveForm(tester);

    expect(find.text('Học sinh: Nguyễn Văn An (HS260001)'), findsOneWidget);
    await tapSubmit(tester);

    expect(find.text(LeaveRule.fromRequired), findsOneWidget);
    expect(find.text(LeaveRule.toRequired), findsOneWidget);
    // Form cuộn lên đầu để thấy lỗi của các ô ngày.
    expect(
      tester.getTopLeft(find.text(LeaveRule.fromRequired)).dy,
      inInclusiveRange(
        0,
        tester.view.physicalSize.height / tester.view.devicePixelRatio,
      ),
    );
    expect(find.text(LeaveRule.reasonRequired), findsOneWidget);
    expect(app.http.count('POST', '/student/requests/leave'), 0);
  });

  testWidgets('a leave with a photo is sent and the new request opens', (
    tester,
  ) async {
    final app = await openRequests(
      tester,
      stub: (app) => app.http.reply(
        'POST',
        '/student/requests/leave',
        FakeReply(201, requestDetailJson(id: 42)),
      ),
    );
    await openLeaveForm(tester);

    await pickDay(tester, 'Từ ngày', 22);
    expect(find.text('22/10/2026'), findsNWidgets(2));
    await enter(tester, 'Lý do', '  Em bị sốt  ');
    app.photo = pickedFile('anh.jpg');
    await tester.ensureVisible(find.text('Chụp ảnh'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Chụp ảnh'));
    await tester.pumpAndSettle();
    expect(find.text('anh.jpg · 1 KB'), findsOneWidget);
    await tapSubmit(tester);

    final sent = app.http.requests.singleWhere(
      (request) => request.method == 'POST',
    );
    final form = sent.data as FormData;
    expect(Map.fromEntries(form.fields), {
      'fromDate': '2026-10-22',
      'toDate': '2026-10-22',
      'reason': 'Em bị sốt',
    });
    expect(form.files.single.key, 'files');
    expect(form.files.single.value.filename, 'anh.jpg');
    expect(form.files.single.value.contentType.toString(), 'image/jpeg');
    expect(find.text('Đã gửi đơn nghỉ.'), findsOneWidget);
    expect(find.text('Lý do'), findsOneWidget);
    expect(find.text('Gửi đơn'), findsNothing);
  });

  testWidgets('a rejected file is reported and not kept', (tester) async {
    final app = await openRequests(tester);
    await openLeaveForm(tester);
    app.pickedFiles = [pickedFile('bang-diem.xls'), pickedFile('don.pdf')];

    await tester.ensureVisible(find.text('Chọn tệp'));

    await tester.pumpAndSettle();
    await tester.tap(find.text('Chọn tệp'));
    await tester.pumpAndSettle();

    expect(
      find.text(
        'Tệp "bang-diem.xls" không hợp lệ: chỉ nhận PDF, JPG, PNG, DOCX, XLSX',
      ),
      findsOneWidget,
    );
    expect(find.text('Tệp minh chứng (1/5)'), findsOneWidget);

    await tester.tap(find.byTooltip('Bỏ tệp don.pdf'));
    await tester.pumpAndSettle();
    expect(find.text('Tệp minh chứng (0/5)'), findsOneWidget);
  });

  testWidgets('a backend error is shown next to its field', (tester) async {
    final app = await openRequests(
      tester,
      stub: (app) => app.http.reply(
        'POST',
        '/student/requests/leave',
        FakeReply.error(
          400,
          'VALIDATION_ERROR',
          'Dữ liệu gửi lên không hợp lệ',
          fieldErrors: [
            {
              'field': 'sectionId',
              'message': 'Học sinh không đang học lớp môn học đã chọn',
            },
          ],
        ),
      ),
    );
    await openLeaveForm(tester);
    await pickDay(tester, 'Từ ngày', 22);
    await enter(tester, 'Lý do', 'Em bị sốt');

    await tapSubmit(tester);

    expect(
      find.text('Học sinh không đang học lớp môn học đã chọn'),
      findsOneWidget,
    );
    expect(app.http.count('POST', '/student/requests/leave'), 1);
    expect(find.text('Gửi đơn'), findsOneWidget);
  });
}
