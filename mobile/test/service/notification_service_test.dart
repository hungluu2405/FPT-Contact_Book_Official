import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/attachment_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/notification_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/number_format.dart';
import 'package:open_filex/open_filex.dart';

import '../support/fakes.dart';

void main() {
  group('models', () {
    test('notification models read the API fields', () {
      final item = NotificationModel.fromJson(
        notificationJson(priority: 'URGENT', read: true),
      );
      final detail = NotificationDetailModel.fromJson(
        notificationDetailJson(files: [attachmentJson()]),
      );

      expect(item.priority, NotificationPriority.urgent);
      expect(item.read, isTrue);
      expect(item.senderName, 'Phòng Công tác học sinh');
      expect(detail.targetLabel, 'Toàn trường');
      expect(detail.files.single.sizeBytes, 245760);
      expect(detail.files.single.extension, 'pdf');
      expect(detail.toJson()['files'], [attachmentJson()]);
    });

    test('missing optional fields get safe defaults', () {
      final item = NotificationModel.fromJson({'id': 3});
      final detail = NotificationDetailModel.fromJson({'id': 3});
      final file = AttachmentModel.fromJson({'id': 1, 'fileName': 'README'});

      expect(item.priority, NotificationPriority.normal);
      expect(item.read, isFalse);
      expect(item.senderName, isNull);
      expect(detail.content, '');
      expect(detail.files, isEmpty);
      expect(file.sizeBytes, 0);
      expect(file.extension, '');
    });
  });

  group('formatting', () {
    test('date-time of the API becomes dd/MM/yyyy HH:mm', () {
      expect(formatApiDateTime('2026-10-20T08:30:00'), '20/10/2026 08:30');
      expect(formatApiDateTime('2026-10-20T08:30'), '20/10/2026 08:30');
      expect(formatApiDateTime('2026-10-20'), '20/10/2026');
      expect(formatApiDateTime(null), '');
      expect(formatApiDateTime(''), '');
    });

    test('file sizes round up to KB below 1 MB like the web', () {
      expect(formatFileSize(0), '0 KB');
      expect(formatFileSize(1), '1 KB');
      expect(formatFileSize(1024), '1 KB');
      expect(formatFileSize(1025), '2 KB');
      expect(formatFileSize(1024 * 1024 - 1), '1024 KB');
      expect(formatFileSize(1024 * 1024), '1 MB');
      expect(formatFileSize(1572864), '1,5 MB');
    });
  });

  group('service', () {
    test('list, detail, read-all and unread count use their paths', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/notifications',
        FakeReply(200, pageJson([notificationJson()], page: 1, totalPages: 3)),
      );
      app.http.reply(
        'GET',
        '/notifications/1',
        FakeReply(200, notificationDetailJson()),
      );
      app.http.reply(
        'POST',
        '/notifications/read-all',
        const FakeReply(200, {'count': 0}),
      );
      app.http.reply(
        'GET',
        '/notifications/unread-count',
        const FakeReply(200, {'count': 4}),
      );
      final service = NotificationService(app.apiClient);

      final page = await service.fetchNotifications(page: 1);
      final detail = await service.fetchNotification(1);
      final left = await service.markAllRead();
      final unread = await service.fetchUnreadCount();

      expect(app.http.requests.first.queryParameters, {'page': 1});
      expect(page.hasMore, isTrue);
      expect(detail.title, 'Lịch nghỉ lễ 20/11');
      expect(left, 0);
      expect(unread, 4);
      expect(NotificationService.filePath(1, 11), '/notifications/1/files/11');
    });

    test('a binary download keeps the ApiError message of a failure', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/notifications/1/files/11',
        FakeReply(200, Uint8List.fromList([37, 80, 68, 70])),
      );
      app.http.reply(
        'GET',
        '/notifications/1/files/12',
        FakeReply.error(404, 'NOT_FOUND', 'Không tìm thấy tệp đính kèm'),
      );

      final bytes = await app.apiClient.getBytes('/notifications/1/files/11');

      expect(bytes, [37, 80, 68, 70]);
      await expectLater(
        app.apiClient.getBytes('/notifications/1/files/12'),
        throwsA(
          isA<ApiErrorModel>()
              .having((error) => error.code, 'code', 'NOT_FOUND')
              .having(
                (error) => error.message,
                'message',
                'Không tìm thấy tệp đính kèm',
              ),
        ),
      );
    });
  });

  group('attachment service', () {
    test(
      'same file names of different paths do not overwrite each other',
      () async {
        final app = TestApp(accessToken: 'a', refreshToken: 'r');
        addTearDown(app.deleteAttachmentDir);
        app.http.reply(
          'GET',
          '/notifications/1/files/11',
          FakeReply(200, Uint8List.fromList([1])),
        );
        app.http.reply(
          'GET',
          '/notifications/2/files/12',
          FakeReply(200, Uint8List.fromList([2])),
        );

        await app.attachmentService.downloadAndOpen(
          '/notifications/1/files/11',
          'a.pdf',
        );
        await app.attachmentService.downloadAndOpen(
          '/notifications/2/files/12',
          'a.pdf',
        );

        expect(app.openedFiles[0], isNot(app.openedFiles[1]));
        expect(File(app.openedFiles[0]).readAsBytesSync(), [1]);
        expect(File(app.openedFiles[1]).readAsBytesSync(), [2]);
      },
    );

    test('a file name cannot leave the attachment folder', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      addTearDown(app.deleteAttachmentDir);
      app.http.reply(
        'GET',
        '/notifications/1/files/11',
        FakeReply(200, Uint8List.fromList([1])),
      );

      await app.attachmentService.downloadAndOpen(
        '/notifications/1/files/11',
        '../../evil.sh',
      );

      final opened = File(app.openedFiles.single);
      expect(opened.parent.parent.path, endsWith('attachments'));
      expect(opened.uri.pathSegments.last, '.._.._evil.sh');
      expect(AttachmentService.safeFileName('  '), 'tep-dinh-kem');
      expect(AttachmentService.safeFileName('..'), 'tep-dinh-kem');
      expect(
        AttachmentService.safeFileName('Đơn: nghỉ?.pdf'),
        'Đơn_ nghỉ_.pdf',
      );
    });

    test('other open failures give a retry message', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      addTearDown(app.deleteAttachmentDir);
      app.openResult = OpenResult(type: ResultType.error, message: 'boom');
      app.http.reply(
        'GET',
        '/notifications/1/files/11',
        FakeReply(200, Uint8List.fromList([1])),
      );

      await expectLater(
        app.attachmentService.downloadAndOpen(
          '/notifications/1/files/11',
          'a.pdf',
        ),
        throwsA(
          isA<ApiErrorModel>().having(
            (error) => error.message,
            'message',
            AttachmentService.openFailedMessage,
          ),
        ),
      );
    });

    test('a file that cannot be saved gives a storage message', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      addTearDown(app.deleteAttachmentDir);
      app.attachmentDir.createSync(recursive: true);
      // Thư mục gốc thực ra là một tệp nên không tạo được thư mục con.
      final blocker = File('${app.attachmentDir.path}/blocker')
        ..writeAsStringSync('x');
      app.http.reply(
        'GET',
        '/notifications/1/files/11',
        FakeReply(200, Uint8List.fromList([1])),
      );
      final service = AttachmentService(
        app.apiClient,
        directory: () async => Directory(blocker.path),
        opener: (_) async => OpenResult(),
      );

      await expectLater(
        service.downloadAndOpen('/notifications/1/files/11', 'a.pdf'),
        throwsA(
          isA<ApiErrorModel>().having(
            (error) => error.message,
            'message',
            AttachmentService.saveFailedMessage,
          ),
        ),
      );
    });

    test('a platform error of the opener gives a retry message', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      addTearDown(app.deleteAttachmentDir);
      app.http.reply(
        'GET',
        '/notifications/1/files/11',
        FakeReply(200, Uint8List.fromList([1])),
      );
      final service = AttachmentService(
        app.apiClient,
        directory: () async => app.attachmentDir,
        opener: (_) async => throw PlatformException(code: 'error'),
      );

      await expectLater(
        service.downloadAndOpen('/notifications/1/files/11', 'a.pdf'),
        throwsA(
          isA<ApiErrorModel>().having(
            (error) => error.message,
            'message',
            AttachmentService.openFailedMessage,
          ),
        ),
      );
    });

    test('clear cache removes downloaded files of the last session', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      addTearDown(app.deleteAttachmentDir);
      app.http.reply(
        'GET',
        '/notifications/1/files/11',
        FakeReply(200, Uint8List.fromList([1])),
      );
      await app.attachmentService.downloadAndOpen(
        '/notifications/1/files/11',
        'a.pdf',
      );

      await app.attachmentService.clearCache();
      await app.attachmentService.clearCache();

      expect(File(app.openedFiles.single).existsSync(), isFalse);
      expect(app.attachmentDir.existsSync(), isTrue);
    });
  });
}
