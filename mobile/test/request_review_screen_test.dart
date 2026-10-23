import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_driver.dart';
import 'support/fakes.dart';

/// Giáo viên đã đăng nhập, có 2 đơn chờ duyệt; đơn 1 có tệp minh chứng.
Future<TestApp> pumpTeacher(WidgetTester tester) async {
  final app = TestApp(accessToken: 'a', refreshToken: 'r');
  addTearDown(app.deleteAttachmentDir);
  app.http.reply(
    'GET',
    '/me',
    FakeReply(
      200,
      userJson(id: 3, fullName: 'Nguyễn Thị Giáo', roles: ['TEACHER']),
    ),
  );
  app.stubHome();
  app.http.on('GET', '/teacher/requests', (request) async {
    if (request.queryParameters['status'] == 'APPROVED') {
      return FakeReply(
        200,
        pageJson([
          requestJson(id: 5, title: 'Nghỉ đi khám', status: 'APPROVED'),
        ]),
      );
    }
    return FakeReply(
      200,
      pageJson([
        requestJson(),
        requestJson(id: 2, title: 'Nghỉ việc gia đình'),
      ]),
    );
  });
  app.http.reply(
    'GET',
    '/teacher/requests/1',
    FakeReply(
      200,
      requestDetailJson(
        canCancel: false,
        canReview: true,
        sentByParent: true,
        files: [attachmentJson(fileName: 'giay-kham.pdf')],
      ),
    ),
  );
  await pumpApp(tester, app: app);
  return app;
}

Future<void> tapText(WidgetTester tester, String text) async {
  final target = find.text(text).last;
  await tester.ensureVisible(target);
  await tester.pumpAndSettle();
  await tester.tap(target);
  await tester.pumpAndSettle();
}

Future<void> openFirst(WidgetTester tester) async {
  await tapText(tester, 'Duyệt đơn');
  await tapText(tester, 'Đơn xin nghỉ học từ 22/10/2026 đến 23/10/2026');
}

Future<void> tapReview(WidgetTester tester, String label) async {
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  final button = find.widgetWithText(
    label == 'Duyệt' ? FilledButton : OutlinedButton,
    label,
  );
  await tester.ensureVisible(button);
  await tester.pumpAndSettle();
  await tester.tap(button);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('the home tile shows the pending count', (tester) async {
    await pumpTeacher(tester);

    final tile = find.ancestor(
      of: find.text('Duyệt đơn'),
      matching: find.byType(InkWell),
    );
    expect(
      find.descendant(of: tile, matching: find.byType(Badge)),
      findsOneWidget,
    );
    expect(find.descendant(of: tile, matching: find.text('2')), findsOneWidget);
    expect(find.text('Sắp có'), findsNothing);
  });

  testWidgets('the queue filters by status', (tester) async {
    final app = await pumpTeacher(tester);
    await tapText(tester, 'Duyệt đơn');

    expect(find.text('2 đơn chờ duyệt'), findsOneWidget);
    expect(find.text('Lê Minh Sinh · HS260001'), findsNothing);
    expect(find.text('Nguyễn Văn An · HS260001'), findsNWidgets(2));

    await tapText(tester, 'Đã duyệt');

    expect(app.http.requests.last.queryParameters, {
      'status': 'APPROVED',
      'page': 0,
    });
    expect(find.text('Nghỉ đi khám'), findsOneWidget);
  });

  testWidgets('approving asks first and reports excused sessions', (
    tester,
  ) async {
    final app = await pumpTeacher(tester);
    app.http.reply(
      'POST',
      '/teacher/requests/1/review',
      FakeReply(200, {
        'decision': 'APPROVED',
        'excusedSessions': 2,
        'request': requestDetailJson(
          status: 'APPROVED',
          canCancel: false,
          reviews: [reviewJson(comment: null)],
        ),
      }),
    );
    await openFirst(tester);

    expect(find.text('Phụ huynh gửi thay'), findsOneWidget);
    expect(find.text('Ghi chú cho học sinh, phụ huynh'), findsOneWidget);

    await tapReview(tester, 'Duyệt');
    expect(find.textContaining('vắng có phép'), findsOneWidget);
    await tester.tap(find.widgetWithText(FilledButton, 'Duyệt').last);
    await tester.pumpAndSettle();

    expect(app.http.requests.last.data, {'decision': 'APPROVED'});
    expect(
      find.text(
        'Đã duyệt đơn. 2 buổi vắng không phép đã chuyển thành vắng có phép.',
      ),
      findsOneWidget,
    );
    expect(find.text('Ghi chú cho học sinh, phụ huynh'), findsNothing);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.text('1 đơn chờ duyệt'), findsOneWidget);
    expect(
      find.text('Đơn xin nghỉ học từ 22/10/2026 đến 23/10/2026'),
      findsNothing,
    );
  });

  testWidgets('rejecting without a comment is stopped at the field', (
    tester,
  ) async {
    final app = await pumpTeacher(tester);
    app.http.reply(
      'POST',
      '/teacher/requests/1/review',
      FakeReply(200, {
        'decision': 'REJECTED',
        'excusedSessions': 0,
        'request': requestDetailJson(status: 'REJECTED', canCancel: false),
      }),
    );
    await openFirst(tester);

    await tapReview(tester, 'Từ chối');
    expect(find.byType(AlertDialog), findsNothing);
    expect(find.text('Vui lòng nhập ghi chú khi từ chối đơn'), findsOneWidget);

    await enter(tester, 'Ghi chú cho học sinh, phụ huynh', 'Thiếu giấy khám');
    await tapReview(tester, 'Từ chối');
    await tester.tap(find.widgetWithText(FilledButton, 'Từ chối'));
    await tester.pumpAndSettle();

    expect(app.http.requests.last.data, {
      'decision': 'REJECTED',
      'comment': 'Thiếu giấy khám',
    });
    expect(find.text('Đã từ chối đơn.'), findsOneWidget);
  });

  testWidgets('a request no longer pending shows the error', (tester) async {
    final app = await pumpTeacher(tester);
    app.http.reply(
      'POST',
      '/teacher/requests/1/review',
      FakeReply.error(
        400,
        'BUSINESS_ERROR',
        'Đơn không còn ở trạng thái chờ duyệt',
      ),
    );
    await openFirst(tester);
    app.http.reply(
      'GET',
      '/teacher/requests/1',
      FakeReply(200, requestDetailJson(status: 'CANCELLED', canCancel: false)),
    );

    await tapReview(tester, 'Duyệt');
    await tester.tap(find.widgetWithText(FilledButton, 'Duyệt').last);
    await tester.pumpAndSettle();

    expect(find.text('Đơn không còn ở trạng thái chờ duyệt'), findsOneWidget);
    expect(find.text('Đã hủy'), findsWidgets);
    expect(find.widgetWithText(FilledButton, 'Duyệt'), findsNothing);
  });

  testWidgets('an evidence file opens', (tester) async {
    final app = await pumpTeacher(tester);
    app.http.reply(
      'GET',
      '/requests/1/files/11',
      FakeReply(200, Uint8List.fromList([1])),
    );
    await openFirst(tester);

    await tapText(tester, 'giay-kham.pdf');

    expect(app.openedFiles.single, endsWith('giay-kham.pdf'));
  });
}
