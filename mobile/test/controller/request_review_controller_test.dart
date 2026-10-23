import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/request_review_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/request_model.dart';

import '../support/fakes.dart';

/// Hàng đợi 2 đơn chờ duyệt của GV.
void stubQueue(TestApp app) {
  app.http.on('GET', '/teacher/requests', (request) async {
    final status = request.queryParameters['status'];
    if (status == 'APPROVED') {
      return FakeReply(200, pageJson([requestJson(id: 5, status: 'APPROVED')]));
    }
    return FakeReply(
      200,
      pageJson([requestJson(), requestJson(id: 2)], totalElements: 2),
    );
  });
}

void main() {
  group('queue', () {
    test('open loads pending requests and counts them', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      stubQueue(app);
      final controller = app.requestReviewController;

      await controller.open();

      expect(controller.filter, RequestReviewController.pending);
      expect(controller.items.map((item) => item.id), [1, 2]);
      expect(controller.pendingCount, 2);
      expect(app.http.requests.single.queryParameters, {
        'status': 'PENDING',
        'page': 0,
      });
    });

    test('a filter reloads from the first page', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      stubQueue(app);
      final controller = app.requestReviewController;
      await controller.open();

      await controller.setFilter('APPROVED');

      expect(controller.items.single.status, RequestStatus.approved);
      expect(controller.pendingCount, 2);
      expect(app.http.requests.last.queryParameters, {
        'status': 'APPROVED',
        'page': 0,
      });
    });

    test('a late page of an older filter is dropped', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final slow = Completer<FakeReply>();
      app.http.on('GET', '/teacher/requests', (request) {
        if (request.queryParameters['status'] == 'REJECTED') {
          return slow.future;
        }
        return Future.value(FakeReply(200, pageJson([requestJson(id: 9)])));
      });
      final controller = app.requestReviewController;

      final old = controller.setFilter('REJECTED');
      await controller.setFilter('ALL');
      slow.complete(FakeReply(200, pageJson([requestJson(id: 1)])));
      await old;

      expect(controller.items.single.id, 9);
      expect(controller.isLoading, isFalse);
    });

    test(
      'the pending count alone can be refreshed for the home tile',
      () async {
        final app = TestApp(accessToken: 'a', refreshToken: 'r');
        app.http.reply(
          'GET',
          '/teacher/requests',
          FakeReply(200, pageJson([requestJson()], totalElements: 7)),
        );

        await app.requestReviewController.loadPendingCount();

        expect(app.requestReviewController.pendingCount, 7);
        expect(app.requestReviewController.items, isEmpty);
      },
    );
  });

  group('review', () {
    Future<TestApp> openDetail() async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      stubQueue(app);
      app.http.reply(
        'GET',
        '/teacher/requests/1',
        FakeReply(200, requestDetailJson(canCancel: false, canReview: true)),
      );
      await app.requestReviewController.open();
      await app.requestReviewController.openRequest(1);
      return app;
    }

    test('loading more after an approval skips no pending request', () async {
      // Backend phân trang theo vị trí: đơn vừa duyệt rời hàng đợi thì các
      // đơn sau dồn lên một chỗ.
      final pending = List<int>.generate(21, (index) => index + 1);
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.on('GET', '/teacher/requests', (request) async {
        final page = request.queryParameters['page'] as int;
        final ids = pending.skip(page * 20).take(20);
        return FakeReply(
          200,
          pageJson(
            [for (final id in ids) requestJson(id: id)],
            page: page,
            totalPages: (pending.length + 19) ~/ 20,
            totalElements: pending.length,
          ),
        );
      });
      app.http.reply(
        'GET',
        '/teacher/requests/1',
        FakeReply(200, requestDetailJson(canCancel: false, canReview: true)),
      );
      app.http.on('POST', '/teacher/requests/1/review', (request) async {
        pending.remove(1);
        return FakeReply(200, {
          'decision': 'APPROVED',
          'excusedSessions': 0,
          'request': requestDetailJson(status: 'APPROVED', canCancel: false),
        });
      });
      final controller = app.requestReviewController;
      await controller.open();
      expect(controller.items, hasLength(20));
      await controller.openRequest(1);
      await controller.review(approve: true, comment: '');

      await controller.loadMore();

      expect(
        controller.items.map((item) => item.id),
        List<int>.generate(20, (index) => index + 2),
      );
      expect(controller.hasMore, isFalse);
    });

    test('approving updates the detail, the queue and the count', () async {
      final app = await openDetail();
      app.http.reply(
        'POST',
        '/teacher/requests/1/review',
        FakeReply(200, {
          'decision': 'APPROVED',
          'excusedSessions': 2,
          'request': requestDetailJson(status: 'APPROVED', canCancel: false),
        }),
      );
      final controller = app.requestReviewController;
      expect(controller.detail?.canReview, isTrue);

      final result = await controller.review(approve: true, comment: '  ');

      expect(app.http.requests.last.data, {'decision': 'APPROVED'});
      expect(result?.excusedSessions, 2);
      expect(controller.detail?.status, RequestStatus.approved);
      expect(controller.detail?.canReview, isFalse);
      expect(controller.items.map((item) => item.id), [2]);
      expect(controller.pendingCount, 1);
      expect(controller.isReviewing, isFalse);
    });

    test('rejecting needs a comment of at most 500 characters', () async {
      final app = await openDetail();
      final controller = app.requestReviewController;

      expect(await controller.review(approve: false, comment: ' '), isNull);
      expect(controller.commentError, 'Vui lòng nhập ghi chú khi từ chối đơn');
      expect(
        await controller.review(approve: false, comment: 'a' * 501),
        isNull,
      );
      expect(controller.commentError, 'Ghi chú tối đa 500 ký tự');
      expect(app.http.count('POST', '/teacher/requests/1/review'), 0);

      controller.clearCommentError();
      expect(controller.commentError, isNull);
    });

    test(
      'a reject keeps the item with the new status outside pending',
      () async {
        final app = await openDetail();
        app.http.reply(
          'POST',
          '/teacher/requests/1/review',
          FakeReply(200, {
            'decision': 'REJECTED',
            'excusedSessions': 0,
            'request': requestDetailJson(status: 'REJECTED', canCancel: false),
          }),
        );
        final controller = app.requestReviewController;
        app.http.on(
          'GET',
          '/teacher/requests',
          (_) async =>
              FakeReply(200, pageJson([requestJson(), requestJson(id: 2)])),
        );
        await controller.setFilter('ALL');

        await controller.review(approve: false, comment: ' Thiếu giấy khám ');

        expect(app.http.requests.last.data, {
          'decision': 'REJECTED',
          'comment': 'Thiếu giấy khám',
        });
        expect(controller.items.first.status, RequestStatus.rejected);
        expect(controller.items, hasLength(2));
      },
    );

    test('a request reviewed meanwhile shows the error and reloads', () async {
      final app = await openDetail();
      app.http.reply(
        'POST',
        '/teacher/requests/1/review',
        FakeReply.error(
          400,
          'BUSINESS_ERROR',
          'Đơn không còn ở trạng thái chờ duyệt',
        ),
      );
      app.http.reply(
        'GET',
        '/teacher/requests/1',
        FakeReply(
          200,
          requestDetailJson(status: 'CANCELLED', canCancel: false),
        ),
      );
      final controller = app.requestReviewController;

      expect(await controller.review(approve: true), isNull);

      expect(controller.reviewError, 'Đơn không còn ở trạng thái chờ duyệt');
      expect(controller.detail?.status, RequestStatus.cancelled);
      expect(controller.detail?.canReview, isFalse);
    });

    test('a comment error from the backend stays on the comment', () async {
      final app = await openDetail();
      app.http.reply(
        'POST',
        '/teacher/requests/1/review',
        FakeReply.error(
          400,
          'BUSINESS_ERROR',
          'Ghi chú tối đa 500 ký tự',
          fieldErrors: [
            {'field': 'comment', 'message': 'Ghi chú tối đa 500 ký tự'},
          ],
        ),
      );

      await app.requestReviewController.review(approve: false, comment: 'x');

      expect(
        app.requestReviewController.commentError,
        'Ghi chú tối đa 500 ký tự',
      );
      expect(app.requestReviewController.reviewError, isNull);
    });

    test('an evidence file opens from the request path', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      addTearDown(app.deleteAttachmentDir);
      app.http.reply(
        'GET',
        '/requests/1/files/11',
        FakeReply(200, Uint8List.fromList([1])),
      );

      final error = await app.requestReviewController.openFile(
        1,
        AttachmentModel.fromJson(attachmentJson(fileName: 'giay-kham.pdf')),
      );

      expect(error, isNull);
      expect(app.openedFiles.single, endsWith('giay-kham.pdf'));
    });
  });
}
