import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/leave_option_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/request_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/attachment_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/request_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

import '../support/fakes.dart';

LeaveFormModel form({String title = '', int? sectionId}) => LeaveFormModel(
  fromDate: DateTime(2026, 10, 22),
  toDate: DateTime(2026, 10, 23),
  sectionId: sectionId,
  title: title,
  reason: ' Em bị sốt ',
);

void main() {
  group('models', () {
    test('request models read the API fields', () {
      final item = RequestModel.fromJson(
        requestJson(type: 'TRANSCRIPT', fromDate: null, toDate: null),
      );
      final detail = RequestDetailModel.fromJson(
        requestDetailJson(
          sentByParent: true,
          files: [attachmentJson()],
          reviews: [reviewJson(decision: 'REJECTED', comment: null)],
        ),
      );
      final options = LeaveOptionsModel.fromJson(leaveOptionsJson());

      expect(item.type, RequestType.transcript);
      expect(item.fromDate, isNull);
      expect(detail.sentByParent, isTrue);
      expect(detail.canCancel, isTrue);
      expect(detail.files.single.fileName, 'lich-nghi-le.pdf');
      expect(detail.reviews.single.decision, RequestStatus.rejected);
      expect(detail.reviews.single.comment, isNull);
      expect(detail.toJson()['reviews'], [
        reviewJson(decision: 'REJECTED', comment: null),
      ]);
      expect(options.sections.last.name, 'TIN11 - Tin học 11 (HK1-2627)');
    });

    test('missing optional fields get safe defaults', () {
      final detail = RequestDetailModel.fromJson({'id': 1});

      expect(detail.type, RequestType.other);
      expect(detail.status, RequestStatus.pending);
      expect(detail.files, isEmpty);
      expect(detail.reviews, isEmpty);
      expect(detail.canCancel, isFalse);
    });

    test('date ranges are shown as dd/MM/yyyy', () {
      expect(
        formatApiDateRange('2026-10-22', '2026-10-23'),
        '22/10/2026 – 23/10/2026',
      );
      expect(formatApiDateRange('2026-10-22', '2026-10-22'), '22/10/2026');
      expect(formatApiDateRange('2026-10-22', null), '22/10/2026');
      expect(formatApiDateRange(null, '2026-10-22'), '');
    });
  });

  group('service', () {
    test('student and parent paths', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/parent/children/7/requests',
        FakeReply(200, pageJson([requestJson()])),
      );
      app.http.reply(
        'GET',
        '/student/requests/3',
        FakeReply(200, requestDetailJson(id: 3)),
      );
      app.http.reply(
        'GET',
        '/parent/children/7/requests/leave-options',
        FakeReply(200, leaveOptionsJson()),
      );
      app.http.reply(
        'POST',
        '/student/requests/3/cancel',
        FakeReply(200, requestDetailJson(id: 3, status: 'CANCELLED')),
      );
      final service = RequestService(app.apiClient);

      await service.fetchRequests(7, page: 2);
      await service.fetchRequest(null, 3);
      await service.fetchLeaveOptions(7);
      final cancelled = await service.cancel(null, 3);

      expect(app.http.requests.first.queryParameters, {'page': 2});
      expect(cancelled.status, RequestStatus.cancelled);
      expect(RequestService.filePath(3, 11), '/requests/3/files/11');
    });

    test('a leave is sent as multipart with typed files', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'POST',
        '/student/requests/leave',
        FakeReply(201, requestDetailJson(id: 42)),
      );
      final service = RequestService(app.apiClient);

      final created = await service.submitLeave(
        null,
        form(title: '  Nghỉ ốm  ', sectionId: 5),
        [
          pickedFile('giay-kham.PDF'),
          pickedFile('anh.jpeg'),
          pickedFile('bang.xlsx'),
        ],
      );

      final request = app.http.requests.single;
      final data = request.data as FormData;
      expect(created.id, 42);
      expect(
        request.headers['content-type'],
        startsWith('multipart/form-data'),
      );
      expect(Map.fromEntries(data.fields), {
        'fromDate': '2026-10-22',
        'toDate': '2026-10-23',
        'reason': 'Em bị sốt',
        'title': 'Nghỉ ốm',
        'sectionId': '5',
      });
      expect(data.files.map((file) => file.key).toSet(), {'files'});
      expect(
        data.files.map((file) => file.value.contentType.toString()).toList(),
        [
          'application/pdf',
          'image/jpeg',
          'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        ],
      );
    });

    test('blank title and no section are not sent', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'POST',
        '/parent/children/7/requests/leave',
        FakeReply(201, requestDetailJson()),
      );

      await RequestService(app.apiClient).submitLeave(7, form(title: '  '), []);

      final data = app.http.requests.single.data as FormData;
      expect(data.fields.map((field) => field.key), [
        'fromDate',
        'toDate',
        'reason',
      ]);
      expect(data.files, isEmpty);
    });

    test('an expired token is refreshed and the form is sent again', () async {
      final app = TestApp(accessToken: 'old', refreshToken: 'r');
      var calls = 0;
      app.http.on('POST', '/student/requests/leave', (_) async {
        calls++;
        return calls == 1
            ? FakeReply.error(401, 'UNAUTHORIZED', 'Hết hạn')
            : FakeReply(201, requestDetailJson(id: 42));
      });
      app.http.reply(
        'POST',
        '/auth/refresh',
        FakeReply(200, tokenJson(access: 'new', refresh: 'r2')),
      );

      final created = await RequestService(app.apiClient)
          .submitLeave(null, form(), [pickedFile('a.pdf')]);

      final sent = app.http.requests
          .where((request) => request.path == '/student/requests/leave')
          .toList();
      expect(created.id, 42);
      expect(sent, hasLength(2));
      expect(sent.last.headers['Authorization'], 'Bearer new');
      expect((sent.last.data as FormData).files.single.value.filename, 'a.pdf');
    });
  });

  group('pickers', () {
    test('picker failures become a friendly error', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.pickError = PlatformException(code: 'camera_access_denied');

      await expectLater(
        app.attachmentService.takePhoto(),
        throwsA(
          isA<ApiErrorModel>().having(
            (error) => error.code,
            'code',
            AttachmentService.pickFailedCode,
          ),
        ),
      );

      app.pickError = const FileSystemException('unreadable');
      await expectLater(
        app.attachmentService.pickFiles(),
        throwsA(isA<ApiErrorModel>()),
      );
    });
  });
}
