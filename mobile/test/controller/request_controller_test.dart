import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/leave_option_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/picked_file_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/request_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/leave_rule.dart';

import '../support/fakes.dart';

/// Đơn hợp lệ với "hôm nay" của test (21/10/2026).
LeaveFormModel validForm() => LeaveFormModel(
  fromDate: DateTime(2026, 10, 22),
  toDate: DateTime(2026, 10, 23),
  reason: 'Em bị sốt',
);

void main() {
  group('list', () {
    test('student loads own requests, parent those of the child', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/student/requests',
        FakeReply(200, pageJson([requestJson()], totalPages: 2)),
      );
      app.http.reply(
        'GET',
        '/parent/children/7/requests',
        FakeReply(200, pageJson([requestJson(id: 9)])),
      );

      await app.requestController.open(null);
      expect(app.requestController.items.single.id, 1);
      expect(app.requestController.hasMore, isTrue);

      await app.requestController.open(7);
      expect(app.requestController.items.single.id, 9);
      expect(app.requestController.hasMore, isFalse);
    });

    test('a late page of the previous child is dropped', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final slow = Completer<FakeReply>();
      app.http.on('GET', '/parent/children/7/requests', (_) => slow.future);
      app.http.reply(
        'GET',
        '/parent/children/8/requests',
        FakeReply(200, pageJson([requestJson(id: 2)])),
      );

      final old = app.requestController.open(7);
      await app.requestController.open(8);
      slow.complete(FakeReply(200, pageJson([requestJson()])));
      await old;

      expect(app.requestController.items.single.id, 2);
      expect(app.requestController.isLoading, isFalse);
    });

    test('load more appends pages and errors can be retried', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.on('GET', '/student/requests', (request) async {
        final page = request.queryParameters['page'] as int;
        if (page == 1) {
          throw const SocketException('offline');
        }
        return FakeReply(
          200,
          pageJson([requestJson(id: page + 1)], page: page, totalPages: 2),
        );
      });
      await app.requestController.open(null);

      await app.requestController.loadMore();
      expect(app.requestController.error, isNotNull);
      expect(app.requestController.items, hasLength(1));

      app.http.on('GET', '/student/requests', (request) async {
        final page = request.queryParameters['page'] as int;
        return FakeReply(
          200,
          pageJson([requestJson(id: page + 1)], page: page, totalPages: 2),
        );
      });
      await app.requestController.loadMore();

      expect(app.requestController.items.map((item) => item.id), [1, 2]);
      expect(app.requestController.error, isNull);
    });
  });

  group('detail and cancel', () {
    test('cancel updates the detail and the list row', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/student/requests',
        FakeReply(200, pageJson([requestJson()])),
      );
      app.http.reply(
        'GET',
        '/student/requests/1',
        FakeReply(200, requestDetailJson(reviews: [reviewJson()])),
      );
      app.http.reply(
        'POST',
        '/student/requests/1/cancel',
        FakeReply(
          200,
          requestDetailJson(status: 'CANCELLED', canCancel: false),
        ),
      );
      await app.requestController.open(null);
      await app.requestController.openRequest(1);
      expect(
        app.requestController.detail?.reviews.single.reviewerName,
        'Giang NT',
      );

      final error = await app.requestController.cancel();

      expect(error, isNull);
      expect(app.requestController.detail?.status, RequestStatus.cancelled);
      expect(app.requestController.detail?.canCancel, isFalse);
      expect(
        app.requestController.items.single.status,
        RequestStatus.cancelled,
      );
      expect(app.requestController.isCancelling, isFalse);
    });

    test('cancel of a request that was reviewed meanwhile returns the error '
        'and reloads the detail', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      var detailCalls = 0;
      app.http.on('GET', '/parent/children/7/requests/1', (_) async {
        detailCalls++;
        return FakeReply(
          200,
          detailCalls == 1
              ? requestDetailJson()
              : requestDetailJson(status: 'APPROVED', canCancel: false),
        );
      });
      app.http.reply(
        'POST',
        '/parent/children/7/requests/1/cancel',
        FakeReply.error(400, 'BUSINESS_ERROR', 'Đơn không còn chờ duyệt'),
      );
      app.http.reply(
        'GET',
        '/parent/children/7/requests',
        FakeReply(200, pageJson([])),
      );
      await app.requestController.open(7);
      await app.requestController.openRequest(1);

      final error = await app.requestController.cancel();

      expect(error?.message, 'Đơn không còn chờ duyệt');
      expect(app.requestController.detail?.status, RequestStatus.approved);
    });

    test('a request file is downloaded from the request path', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      addTearDown(app.deleteAttachmentDir);
      app.http.reply(
        'GET',
        '/requests/1/files/11',
        FakeReply(200, Uint8List.fromList([1])),
      );

      final error = await app.requestController.openFile(
        1,
        AttachmentModel.fromJson(attachmentJson(fileName: 'giay-kham.pdf')),
      );

      expect(error, isNull);
      expect(app.openedFiles.single, endsWith('giay-kham.pdf'));
    });
  });

  group('leave form', () {
    test('start loads the options of the child', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/parent/children/7/requests/leave-options',
        FakeReply(200, leaveOptionsJson()),
      );
      app.http.reply(
        'GET',
        '/parent/children/7/requests',
        FakeReply(200, pageJson([])),
      );
      await app.requestController.open(7);

      await app.requestController.startLeave();

      expect(app.requestController.options?.sections, hasLength(2));
      expect(app.requestController.files, isEmpty);
      expect(app.requestController.fieldErrors, isEmpty);
    });

    test('picked files are checked like the backend', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.pickedFiles = [
        pickedFile('giay-kham.pdf'),
        pickedFile('ghi-chu.txt'),
        pickedFile('rong.png', size: 0),
        PickedFileModel(
          fileName: 'lon.jpg',
          sizeBytes: LeaveRule.maxFileBytes + 1,
        ),
        pickedFile('du.docx', size: LeaveRule.maxFileBytes),
      ];

      final messages = await app.requestController.pickFiles();

      expect(app.requestController.files.map((file) => file.fileName), [
        'giay-kham.pdf',
        'du.docx',
      ]);
      expect(messages, [
        'Tệp "ghi-chu.txt" không hợp lệ: chỉ nhận PDF, JPG, PNG, DOCX, XLSX',
        'Tệp "rong.png" rỗng',
        'Tệp "lon.jpg" vượt quá 5 MB',
      ]);
    });

    test('no more than five files are kept', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.pickedFiles = [for (var i = 1; i <= 4; i++) pickedFile('$i.pdf')];
      await app.requestController.pickFiles();
      app.photo = pickedFile('anh.jpg');
      await app.requestController.takePhoto();
      app.photo = pickedFile('anh-2.jpg');

      final messages = await app.requestController.takePhoto();

      expect(app.requestController.files, hasLength(LeaveRule.maxFiles));
      expect(messages, ['Tối đa 5 tệp đính kèm']);

      app.requestController.removeFile(0);
      expect(app.requestController.files.first.fileName, '2.pdf');
    });

    test('a picker failure becomes a message, cancel adds nothing', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.photo = null;
      expect(await app.requestController.takePhoto(), isEmpty);

      app.pickError = PlatformException(code: 'camera_access_denied');
      final messages = await app.requestController.takePhoto();

      expect(messages.single, contains('Không mở được máy ảnh'));
      expect(app.requestController.files, isEmpty);
    });

    test('dates and reason are checked before sending', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');

      final result = await app.requestController.submitLeave(
        LeaveFormModel(
          fromDate: DateTime(2026, 10, 6),
          toDate: DateTime(2026, 11, 5),
          reason: '   ',
        ),
      );

      expect(result, isNull);
      expect(app.requestController.fieldErrors, {
        'fromDate': LeaveRule.fromTooOld,
        'toDate': LeaveRule.spanTooLong,
        'reason': LeaveRule.reasonRequired,
      });
      expect(app.http.requests, isEmpty);
    });

    test('date boundaries match the backend rule', () {
      final today = DateTime(2026, 10, 21);
      Map<String, String> check(DateTime from, DateTime to) =>
          LeaveRule.validate(
            LeaveFormModel(fromDate: from, toDate: to, reason: 'x'),
            today,
          );

      expect(check(DateTime(2026, 10, 7), DateTime(2026, 10, 7)), isEmpty);
      expect(
        check(DateTime(2026, 10, 6), DateTime(2026, 10, 7))['fromDate'],
        LeaveRule.fromTooOld,
      );
      expect(check(DateTime(2026, 10, 22), DateTime(2026, 11, 20)), isEmpty);
      expect(
        check(DateTime(2026, 10, 22), DateTime(2026, 11, 21))['toDate'],
        LeaveRule.spanTooLong,
      );
      expect(
        check(DateTime(2026, 10, 22), DateTime(2026, 10, 21))['toDate'],
        LeaveRule.toBeforeFrom,
      );
      expect(LeaveRule.validate(LeaveFormModel(reason: 'x'), today).keys, [
        'fromDate',
        'toDate',
      ]);
      expect(
        LeaveRule.validate(
          LeaveFormModel(
            fromDate: today,
            toDate: today,
            title: 'a' * 201,
            reason: 'b' * 1001,
          ),
          today,
        ),
        {'title': LeaveRule.titleSize, 'reason': LeaveRule.reasonSize},
      );
    });

    test(
      'a valid form is sent with its files and opens the new request',
      () async {
        final app = TestApp(accessToken: 'a', refreshToken: 'r');
        app.http.reply(
          'POST',
          '/parent/children/7/requests/leave',
          FakeReply(201, requestDetailJson(id: 42, sentByParent: true)),
        );
        app.http.reply(
          'GET',
          '/parent/children/7/requests',
          FakeReply(200, pageJson([requestJson(id: 42)])),
        );
        await app.requestController.open(7);
        app.pickedFiles = [pickedFile('giay-kham.pdf')];
        await app.requestController.pickFiles();

        final created = await app.requestController.submitLeave(
          validForm()..sectionId = 5,
        );

        expect(created?.id, 42);
        expect(app.requestController.detail?.sentByParent, isTrue);
        expect(app.requestController.isSubmitting, isFalse);
        // Danh sách tải lại phía sau.
        await pumpEventQueue();
        expect(app.http.count('GET', '/parent/children/7/requests'), 2);
        expect(app.requestController.items.single.id, 42);
      },
    );

    test('reset forgets the list, the detail and the files not sent', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/parent/children/7/requests',
        FakeReply(200, pageJson([requestJson()])),
      );
      app.http.reply(
        'GET',
        '/parent/children/7/requests/1',
        FakeReply(200, requestDetailJson()),
      );
      await app.requestController.open(7);
      await app.requestController.openRequest(1);
      app.pickedFiles = [pickedFile('giay-kham.pdf')];
      await app.requestController.pickFiles();

      app.requestController.reset();

      expect(app.requestController.studentId, isNull);
      expect(app.requestController.items, isEmpty);
      expect(app.requestController.detail, isNull);
      expect(app.requestController.files, isEmpty);
    });

    test(
      'backend field errors are kept by field, others as a form error',
      () async {
        final app = TestApp(accessToken: 'a', refreshToken: 'r');
        app.http.reply(
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
        );
        app.http.reply(
          'GET',
          '/student/requests',
          FakeReply(200, pageJson([])),
        );
        await app.requestController.open(null);

        expect(await app.requestController.submitLeave(validForm()), isNull);
        expect(
          app.requestController.fieldErrors['sectionId'],
          'Học sinh không đang học lớp môn học đã chọn',
        );
        expect(app.requestController.formError, isNull);

        app.http.reply(
          'POST',
          '/student/requests/leave',
          FakeReply.error(
            400,
            'BUSINESS_ERROR',
            'Tệp "a.pdf" không hợp lệ: chỉ nhận PDF, JPG, PNG, DOCX, XLSX',
          ),
        );
        expect(await app.requestController.submitLeave(validForm()), isNull);
        expect(app.requestController.fieldErrors, isEmpty);
        expect(
          app.requestController.formError,
          'Tệp "a.pdf" không hợp lệ: chỉ nhận PDF, JPG, PNG, DOCX, XLSX',
        );

        app.requestController.clearFieldError('sectionId');
        expect(app.requestController.fieldErrors, isEmpty);
      },
    );
  });
}
