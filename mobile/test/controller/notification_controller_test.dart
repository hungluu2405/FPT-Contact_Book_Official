import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:open_filex/open_filex.dart';

import '../support/fakes.dart';

void main() {
  group('list', () {
    test('open loads the first page', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/notifications',
        FakeReply(200, pageJson([notificationJson()], totalPages: 2)),
      );

      await app.notificationController.open();

      expect(
        app.notificationController.items.single.title,
        'Lịch nghỉ lễ 20/11',
      );
      expect(app.notificationController.hasMore, isTrue);
      expect(app.notificationController.isLoading, isFalse);
      expect(app.http.requests.single.queryParameters, {'page': 0});
    });

    test('load more appends pages until the last one', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.on('GET', '/notifications', (request) async {
        final page = request.queryParameters['page'] as int;
        return FakeReply(
          200,
          pageJson([notificationJson(id: page + 1)], page: page, totalPages: 2),
        );
      });
      await app.notificationController.open();

      await app.notificationController.loadMore();
      await app.notificationController.loadMore();

      expect(app.notificationController.items.map((item) => item.id), [1, 2]);
      expect(app.notificationController.hasMore, isFalse);
      expect(app.http.count('GET', '/notifications'), 2);
    });

    test('a reload drops the reply of the older request', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final slow = Completer<FakeReply>();
      var calls = 0;
      app.http.on('GET', '/notifications', (_) {
        calls++;
        if (calls == 1) {
          return slow.future;
        }
        return Future.value(
          FakeReply(200, pageJson([notificationJson(id: 2)])),
        );
      });

      final old = app.notificationController.open();
      await app.notificationController.open();
      slow.complete(FakeReply(200, pageJson([notificationJson(id: 1)])));
      await old;

      expect(app.notificationController.items.single.id, 2);
      expect(app.notificationController.isLoading, isFalse);
    });

    test('an error keeps loaded items and can be retried', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.offline('GET', '/notifications');

      await app.notificationController.open();

      expect(app.notificationController.items, isEmpty);
      expect(app.notificationController.error?.isNetworkError, isTrue);

      app.http.reply(
        'GET',
        '/notifications',
        FakeReply(200, pageJson([notificationJson()])),
      );
      await app.notificationController.loadMore();

      expect(app.notificationController.error, isNull);
      expect(app.notificationController.items, hasLength(1));
    });
  });

  group('read state', () {
    test('opening an unread notification marks it read and asks to '
        'reload the unread count', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      var unreadChanged = 0;
      app.notificationController.onUnreadChanged = () => unreadChanged++;
      app.http.reply(
        'GET',
        '/notifications',
        FakeReply(200, pageJson([notificationJson(read: false)])),
      );
      app.http.reply(
        'GET',
        '/notifications/1',
        FakeReply(200, notificationDetailJson(files: [attachmentJson()])),
      );
      await app.notificationController.open();

      await app.notificationController.openNotification(1);

      final detail = app.notificationController.detail!;
      expect(detail.targetLabel, 'Toàn trường');
      expect(detail.files.single.fileName, 'lich-nghi-le.pdf');
      expect(app.notificationController.items.single.read, isTrue);
      expect(unreadChanged, 1);
    });

    test(
      'opening a notification that was already read changes no count',
      () async {
        final app = TestApp(accessToken: 'a', refreshToken: 'r');
        var unreadChanged = 0;
        app.notificationController.onUnreadChanged = () => unreadChanged++;
        app.http.reply(
          'GET',
          '/notifications',
          FakeReply(200, pageJson([notificationJson(read: true)])),
        );
        app.http.reply(
          'GET',
          '/notifications/1',
          FakeReply(200, notificationDetailJson()),
        );
        await app.notificationController.open();

        await app.notificationController.openNotification(1);

        expect(unreadChanged, 0);
      },
    );

    test('a failed detail shows the error and keeps the item unread', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      var unreadChanged = 0;
      app.notificationController.onUnreadChanged = () => unreadChanged++;
      app.http.reply(
        'GET',
        '/notifications',
        FakeReply(200, pageJson([notificationJson()])),
      );
      app.http.reply(
        'GET',
        '/notifications/1',
        FakeReply.error(404, 'NOT_FOUND', 'Không tìm thấy thông báo'),
      );
      await app.notificationController.open();

      await app.notificationController.openNotification(1);

      expect(app.notificationController.detail, isNull);
      expect(
        app.notificationController.detailError?.message,
        'Không tìm thấy thông báo',
      );
      expect(app.notificationController.items.single.read, isFalse);
      expect(unreadChanged, 0);
    });

    test('a late detail of another notification is ignored', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final slow = Completer<FakeReply>();
      app.http.on('GET', '/notifications/1', (_) => slow.future);
      app.http.reply(
        'GET',
        '/notifications/2',
        FakeReply(200, notificationDetailJson(id: 2, title: 'Họp phụ huynh')),
      );

      final old = app.notificationController.openNotification(1);
      await app.notificationController.openNotification(2);
      slow.complete(FakeReply(200, notificationDetailJson()));
      await old;

      expect(app.notificationController.detail?.title, 'Họp phụ huynh');
      expect(app.notificationController.isLoadingDetail, isFalse);
    });

    test(
      'mark all read marks every loaded item and reloads the count',
      () async {
        final app = TestApp(accessToken: 'a', refreshToken: 'r');
        var unreadChanged = 0;
        app.notificationController.onUnreadChanged = () => unreadChanged++;
        app.http.reply(
          'GET',
          '/notifications',
          FakeReply(
            200,
            pageJson([notificationJson(), notificationJson(id: 2)]),
          ),
        );
        app.http.reply(
          'POST',
          '/notifications/read-all',
          const FakeReply(200, {'count': 0}),
        );
        await app.notificationController.open();

        final error = await app.notificationController.markAllRead();

        expect(error, isNull);
        expect(
          app.notificationController.items.every((item) => item.read),
          isTrue,
        );
        expect(app.notificationController.isMarkingAll, isFalse);
        expect(unreadChanged, 1);
      },
    );

    test(
      'mark all read drops a page that was loading with old states',
      () async {
        final app = TestApp(accessToken: 'a', refreshToken: 'r');
        final slow = Completer<FakeReply>();
        app.http.on('GET', '/notifications', (request) {
          if (request.queryParameters['page'] == 1) {
            return slow.future;
          }
          return Future.value(
            FakeReply(200, pageJson([notificationJson()], totalPages: 2)),
          );
        });
        app.http.reply(
          'POST',
          '/notifications/read-all',
          const FakeReply(200, {'count': 0}),
        );
        await app.notificationController.open();

        final loading = app.notificationController.loadMore();
        await app.notificationController.markAllRead();
        slow.complete(
          FakeReply(
            200,
            pageJson([notificationJson(id: 2)], page: 1, totalPages: 2),
          ),
        );
        await loading;

        expect(app.notificationController.items.map((item) => item.id), [1]);
        expect(app.notificationController.hasUnreadLoaded, isFalse);
        expect(app.notificationController.hasMore, isTrue);
        expect(app.notificationController.isLoading, isFalse);
      },
    );

    test('mark all read returns the error and keeps items unread', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      var unreadChanged = 0;
      app.notificationController.onUnreadChanged = () => unreadChanged++;
      app.http.reply(
        'GET',
        '/notifications',
        FakeReply(200, pageJson([notificationJson()])),
      );
      app.http.offline('POST', '/notifications/read-all');
      await app.notificationController.open();

      final error = await app.notificationController.markAllRead();

      expect(error?.isNetworkError, isTrue);
      expect(app.notificationController.items.single.read, isFalse);
      expect(unreadChanged, 0);
    });
  });

  group('attachments', () {
    test('a file is downloaded and handed to another app', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      addTearDown(app.deleteAttachmentDir);
      app.http.reply(
        'GET',
        '/notifications/1/files/11',
        FakeReply(200, Uint8List.fromList([1, 2, 3])),
      );
      final file = AttachmentModel.fromJson(attachmentJson());

      final error = await app.notificationController.openFile(1, file);

      expect(error, isNull);
      expect(File(app.openedFiles.single).readAsBytesSync(), [1, 2, 3]);
      expect(app.openedFiles.single, endsWith('lich-nghi-le.pdf'));
      expect(app.notificationController.openingFileId, isNull);
    });

    test('no app to open the file gives a friendly error', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      addTearDown(app.deleteAttachmentDir);
      app.openResult = OpenResult(type: ResultType.noAppToOpen);
      app.http.reply(
        'GET',
        '/notifications/1/files/11',
        FakeReply(200, Uint8List.fromList([1])),
      );

      final error = await app.notificationController.openFile(
        1,
        AttachmentModel.fromJson(attachmentJson()),
      );

      expect(error?.message, 'Máy chưa có ứng dụng mở được tệp này.');
    });

    test('a download error is returned and nothing is opened', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      addTearDown(app.deleteAttachmentDir);
      app.http.reply(
        'GET',
        '/notifications/1/files/11',
        FakeReply.error(403, 'FORBIDDEN', 'Bạn không có quyền tải tệp này'),
      );

      final error = await app.notificationController.openFile(
        1,
        AttachmentModel.fromJson(attachmentJson()),
      );

      expect(error?.message, 'Bạn không có quyền tải tệp này');
      expect(app.openedFiles, isEmpty);
      expect(app.notificationController.openingFileId, isNull);
    });
  });
}
