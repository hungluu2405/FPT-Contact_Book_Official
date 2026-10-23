import 'dart:async';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/department_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/sent_notification_model.dart';

import '../support/fakes.dart';

DepartmentNotificationFormModel form({
  String title = 'Họp tổ chuyên môn tháng 11',
  String content = 'Họp lúc 14:00 thứ Năm.',
  String priority = 'IMPORTANT',
}) => DepartmentNotificationFormModel(
  title: title,
  content: content,
  priority: priority,
);

void main() {
  final department = DepartmentModel.fromJson(departmentJson());

  group('compose', () {
    test('the whole department is the target by default', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'POST',
        '/head/notifications',
        FakeReply(201, sentDetailJson()),
      );
      final controller = app.departmentNotificationController;
      controller.startCompose(department: department);
      app.pickedFiles = [pickedFile('bien-ban.pdf')];
      await controller.pickFiles();

      final sent = await controller.send(form());

      final data = app.http.requests.single.data as FormData;
      expect(controller.targetLabel, 'Cả Tổ Toán – Tin học');
      expect(Map.fromEntries(data.fields), {
        'title': 'Họp tổ chuyên môn tháng 11',
        'content': 'Họp lúc 14:00 thứ Năm.',
        'priority': 'IMPORTANT',
        'departmentId': '1',
      });
      expect(data.files.single.value.filename, 'bien-ban.pdf');
      expect(sent?.recipientCount, 3);
      expect(controller.detail?.id, 70);
      expect(controller.files, isEmpty);
      expect(controller.isSending, isFalse);
    });

    test('one teacher is the target when chosen', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'POST',
        '/head/notifications',
        FakeReply(201, sentDetailJson(targetType: 'USER', recipients: 1)),
      );
      final controller = app.departmentNotificationController;
      controller.startCompose(
        department: department,
        teacherUserId: 3,
        teacherName: 'Nguyễn Thị Giáo',
      );

      await controller.send(form());

      final data = app.http.requests.single.data as FormData;
      expect(controller.targetLabel, 'Riêng thầy/cô Nguyễn Thị Giáo');
      expect(Map.fromEntries(data.fields)['teacherUserId'], '3');
      expect(
        data.fields.map((field) => field.key),
        isNot(contains('departmentId')),
      );
    });

    test('title and content are checked before sending', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final controller = app.departmentNotificationController;
      controller.startCompose(department: department);

      expect(await controller.send(form(title: '  ', content: '')), isNull);
      expect(controller.fieldErrors, {
        'title': 'Vui lòng nhập tiêu đề',
        'content': 'Vui lòng nhập nội dung',
      });

      await controller.send(form(title: 'a' * 201));
      expect(controller.fieldErrors, {'title': 'Tiêu đề tối đa 200 ký tự'});
      expect(app.http.requests, isEmpty);

      controller.clearFieldError('title');
      expect(controller.fieldErrors, isEmpty);
    });

    test(
      'backend errors: known fields by field, others as a form error',
      () async {
        final app = TestApp(accessToken: 'a', refreshToken: 'r');
        final controller = app.departmentNotificationController;
        controller.startCompose(department: department);
        app.http.reply(
          'POST',
          '/head/notifications',
          FakeReply.error(
            400,
            'VALIDATION_ERROR',
            'Dữ liệu gửi lên không hợp lệ',
            fieldErrors: [
              {'field': 'title', 'message': 'Tiêu đề tối đa 200 ký tự'},
              {
                'field': 'recipientPhone',
                'message': 'Không tìm thấy tài khoản đang hoạt động có số điện thoại này',
              },
            ],
          ),
        );

        expect(await controller.send(form()), isNull);
        expect(controller.fieldErrors, {'title': 'Tiêu đề tối đa 200 ký tự'});
        expect(
          controller.formError,
          'Không tìm thấy tài khoản đang hoạt động có số điện thoại này',
        );

        app.http.reply(
          'POST',
          '/head/notifications',
          FakeReply.error(
            400,
            'BUSINESS_ERROR',
            'Phạm vi đã chọn không có người nhận nào (ngoài chính bạn)',
          ),
        );
        await controller.send(form());
        expect(controller.fieldErrors, isEmpty);
        expect(
          controller.formError,
          'Phạm vi đã chọn không có người nhận nào (ngoài chính bạn)',
        );
      },
    );

    test('a new compose forgets files and errors of the last one', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final controller = app.departmentNotificationController;
      controller.startCompose(department: department);
      app.pickedFiles = [pickedFile('a.pdf'), pickedFile('b.txt')];
      final rejected = await controller.pickFiles();
      await controller.send(form(title: ''));

      controller.startCompose(department: department);

      expect(rejected, hasLength(1));
      expect(controller.files, isEmpty);
      expect(controller.fieldErrors, isEmpty);
    });
  });

  group('sent', () {
    test('the sent list loads pages', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.on('GET', '/notifications/sent', (request) async {
        final page = request.queryParameters['page'] as int;
        return FakeReply(
          200,
          pageJson([sentJson(id: page + 1)], page: page, totalPages: 2),
        );
      });
      final controller = app.departmentNotificationController;

      await controller.openSent();
      await controller.loadMoreSent();
      await controller.loadMoreSent();

      expect(controller.sentItems.map((item) => item.id), [1, 2]);
      expect(controller.sentHasMore, isFalse);
      expect(app.http.count('GET', '/notifications/sent'), 2);
    });

    test('the detail filters readers and loads more recipients', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.on('GET', '/notifications/sent/70', (request) async {
        final read = request.queryParameters['read'];
        final page = request.queryParameters['page'] as int;
        if (read == true) {
          return FakeReply(200, sentDetailJson(rows: [recipientJson()]));
        }
        return FakeReply(
          200,
          sentDetailJson(
            rows: [recipientJson(name: 'Người ${page + 1}', read: false)],
            page: page,
            totalPages: 2,
          ),
        );
      });
      final controller = app.departmentNotificationController;

      await controller.openSentDetail(70);
      await controller.loadMoreRecipients();
      expect(controller.recipients.map((r) => r.fullName), [
        'Người 1',
        'Người 2',
      ]);
      expect(controller.recipientsHasMore, isFalse);

      await controller.setReadFilter(true);

      expect(controller.readFilter, isTrue);
      expect(controller.recipients.single.read, isTrue);
      expect(app.http.requests.last.queryParameters, {'read': true, 'page': 0});
    });

    test('a late detail of another notification is dropped', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final slow = Completer<FakeReply>();
      app.http.on('GET', '/notifications/sent/70', (_) => slow.future);
      app.http.reply(
        'GET',
        '/notifications/sent/71',
        FakeReply(200, sentDetailJson(id: 71)),
      );
      final controller = app.departmentNotificationController;

      final old = controller.openSentDetail(70);
      await controller.openSentDetail(71);
      slow.complete(FakeReply(200, sentDetailJson()));
      await old;

      expect(controller.detail?.id, 71);
      expect(controller.isLoadingDetail, isFalse);
    });

    test('an attachment opens from the notification path', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      addTearDown(app.deleteAttachmentDir);
      app.http.reply(
        'GET',
        '/notifications/70/files/11',
        FakeReply(200, Uint8List.fromList([1])),
      );

      final error = await app.departmentNotificationController.openFile(
        70,
        AttachmentModel.fromJson(attachmentJson(fileName: 'bien-ban.pdf')),
      );

      expect(error, isNull);
      expect(app.openedFiles.single, endsWith('bien-ban.pdf'));
    });

    test('reset forgets everything', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/notifications/sent',
        FakeReply(200, pageJson([sentJson()])),
      );
      final controller = app.departmentNotificationController;
      await controller.openSent();
      controller.startCompose(department: department);
      app.pickedFiles = [pickedFile('a.pdf')];
      await controller.pickFiles();

      controller.reset();

      expect(controller.sentItems, isEmpty);
      expect(controller.files, isEmpty);
      expect(controller.targetLabel, isEmpty);
    });
  });
}
