import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import '../support/fakes.dart';

void main() {
  group('list', () {
    test('loads my clubs and the first page of all clubs', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/student/clubs',
        FakeReply(200, [myClubJson(status: 'PENDING')]),
      );
      app.http.reply(
        'GET',
        '/clubs',
        FakeReply(200, pageJson([clubJson()], totalPages: 2)),
      );

      await app.clubController.open(null);

      expect(app.clubController.myClubs.single.status, 'PENDING');
      expect(app.clubController.clubs.single.code, 'F-CODE');
      expect(app.clubController.clubsHasMore, isTrue);
      expect(
        app.http.requests.firstWhere((r) => r.path == '/clubs').queryParameters,
        {'page': 0},
      );
    });

    test('parent loads the clubs of the chosen child', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/parent/children/7/clubs',
        const FakeReply(200, []),
      );
      app.http.reply('GET', '/clubs', FakeReply(200, pageJson([])));

      await app.clubController.open(7);

      expect(app.http.count('GET', '/parent/children/7/clubs'), 1);
      expect(app.clubController.myClubs, isEmpty);
    });

    test('switching child hides the clubs of the previous one', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final slow = Completer<FakeReply>();
      app.http.reply(
        'GET',
        '/parent/children/7/clubs',
        FakeReply(200, [myClubJson()]),
      );
      app.http.on('GET', '/parent/children/8/clubs', (_) => slow.future);
      app.http.reply('GET', '/clubs', FakeReply(200, pageJson([])));
      await app.clubController.open(7);
      expect(app.clubController.myClubs, hasLength(1));

      final opening = app.clubController.open(8);

      expect(app.clubController.myClubs, isEmpty);
      expect(app.clubController.isLoadingMyClubs, isTrue);
      slow.complete(const FakeReply(200, []));
      await opening;
    });

    test('search restarts paging and drops the older reply', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final slow = Completer<FakeReply>();
      app.http.reply('GET', '/student/clubs', const FakeReply(200, []));
      app.http.on('GET', '/clubs', (request) {
        final q = request.queryParameters['q'];
        if (q == 'co') {
          return slow.future;
        }
        if (q == 'guitar') {
          return Future.value(
            FakeReply(200, pageJson([clubJson(id: 2, code: 'F-GUITAR')])),
          );
        }
        return Future.value(FakeReply(200, pageJson([clubJson()])));
      });
      await app.clubController.open(null);

      final old = app.clubController.search('co');
      await app.clubController.search(' guitar ');
      slow.complete(FakeReply(200, pageJson([clubJson()])));
      await old;

      expect(app.clubController.query, 'guitar');
      expect(app.clubController.clubs.single.code, 'F-GUITAR');
      expect(app.clubController.isLoadingClubs, isFalse);
    });

    test('load more appends the next page until the last', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply('GET', '/student/clubs', const FakeReply(200, []));
      app.http.on('GET', '/clubs', (request) async {
        final page = request.queryParameters['page'] as int;
        return FakeReply(
          200,
          pageJson([clubJson(id: page + 1)], page: page, totalPages: 2),
        );
      });
      await app.clubController.open(null);

      await app.clubController.loadMoreClubs();
      await app.clubController.loadMoreClubs();

      expect(app.clubController.clubs.map((c) => c.id), [1, 2]);
      expect(app.clubController.clubsHasMore, isFalse);
      expect(app.http.count('GET', '/clubs'), 2);
    });

    test('an error of my clubs does not hide all clubs', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.offline('GET', '/student/clubs');
      app.http.reply('GET', '/clubs', FakeReply(200, pageJson([clubJson()])));

      await app.clubController.open(null);

      expect(app.clubController.myClubsError!.isNetworkError, isTrue);
      expect(app.clubController.clubs, hasLength(1));
      expect(app.clubController.clubsError, isNull);
    });
  });

  group('detail', () {
    void stubDetail(TestApp app, {int clubId = 1, int totalPages = 1}) {
      app.http.reply(
        'GET',
        '/clubs/$clubId',
        FakeReply(
          200,
          clubDetailJson(
            club: clubJson(id: clubId),
            myStatus: 'ACTIVE',
            myRole: 'MEMBER',
          ),
        ),
      );
      app.http.reply(
        'GET',
        '/clubs/$clubId/activities',
        FakeReply(200, [clubActivityJson()]),
      );
      app.http.on('GET', '/clubs/$clubId/members', (request) async {
        final page = request.queryParameters['page'] as int;
        return FakeReply(
          200,
          pageJson(
            [clubMemberJson(name: 'Thành viên $page')],
            page: page,
            totalPages: totalPages,
          ),
        );
      });
    }

    test('loads detail, upcoming activities and members', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      stubDetail(app, totalPages: 2);

      await app.clubController.openClub(1);

      expect(app.clubController.detail!.myRole, 'MEMBER');
      expect(app.clubController.activities.single.taken, 12);
      expect(app.clubController.members.single.fullName, 'Thành viên 0');
      expect(app.clubController.membersHasMore, isTrue);

      await app.clubController.loadMoreMembers();

      expect(app.clubController.members, hasLength(2));
      expect(app.clubController.membersHasMore, isFalse);
    });

    test('opening another club drops the slow reply of the first', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final slow = Completer<FakeReply>();
      stubDetail(app, clubId: 2);
      app.http.on('GET', '/clubs/1', (_) => slow.future);
      app.http.reply('GET', '/clubs/1/activities', const FakeReply(200, []));
      app.http.reply('GET', '/clubs/1/members', FakeReply(200, pageJson([])));

      final first = app.clubController.openClub(1);
      await app.clubController.openClub(2);
      slow.complete(FakeReply(200, clubDetailJson()));
      await first;

      expect(app.clubController.detail!.club.id, 2);
      expect(app.clubController.isLoadingDetail, isFalse);
    });

    test('unknown club shows the server message', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/clubs/9',
        FakeReply.error(404, 'NOT_FOUND', 'Không tìm thấy câu lạc bộ'),
      );
      app.http.reply('GET', '/clubs/9/activities', const FakeReply(200, []));
      app.http.reply('GET', '/clubs/9/members', FakeReply(200, pageJson([])));

      await app.clubController.openClub(9);

      expect(app.clubController.detail, isNull);
      expect(
        app.clubController.detailError!.message,
        'Không tìm thấy câu lạc bộ',
      );
    });
  });
}
