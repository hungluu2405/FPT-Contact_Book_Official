import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/club_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/club_service.dart';

import '../support/fakes.dart';

void main() {
  test('club models read the API fields', () {
    final detail = ClubDetailModel.fromJson(
      clubDetailJson(myStatus: 'PENDING'),
    );
    final activity = ClubActivityModel.fromJson(clubActivityJson(max: null));
    final mine = MyClubModel.fromJson(myClubJson(role: 'PRESIDENT'));

    expect(detail.club.code, 'F-CODE');
    expect(detail.club.isActive, isTrue);
    expect(detail.myStatus, ClubMemberStatus.pending);
    expect(detail.myRole, isNull);
    expect(activity.maxParticipants, isNull);
    expect(activity.taken, 12);
    expect(mine.role, ClubMemberRole.president);
  });

  test('clubs send q only when the keyword is not blank', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/clubs', FakeReply(200, pageJson([clubJson()])));
    final service = ClubService(app.apiClient);

    await service.fetchClubs(query: '   ');
    await service.fetchClubs(query: ' code ', page: 2);

    expect(app.http.requests.first.queryParameters, {'page': 0});
    expect(app.http.requests.last.queryParameters, {'q': 'code', 'page': 2});
  });

  test('detail, members, activities and my clubs use their paths', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/clubs/1', FakeReply(200, clubDetailJson()));
    app.http.reply(
      'GET',
      '/clubs/1/members',
      FakeReply(200, pageJson([clubMemberJson()])),
    );
    app.http.reply(
      'GET',
      '/clubs/1/activities',
      FakeReply(200, [clubActivityJson()]),
    );
    app.http.reply('GET', '/student/clubs', FakeReply(200, [myClubJson()]));
    app.http.reply('GET', '/parent/children/7/clubs', const FakeReply(200, []));
    final service = ClubService(app.apiClient);

    final detail = await service.fetchClub(1);
    final members = await service.fetchMembers(1, page: 1);
    final activities = await service.fetchActivities(1);
    final mine = await service.fetchMyClubs(null);
    final child = await service.fetchMyClubs(7);

    expect(detail.club.presidentName, 'Nguyễn Văn Hùng');
    expect(members.items.single.role, ClubMemberRole.president);
    expect(activities.single.title, 'Luyện thuật toán');
    expect(mine.single.clubId, 1);
    expect(child, isEmpty);
    expect(
      app.http.requests
          .firstWhere((r) => r.path == '/clubs/1/members')
          .queryParameters,
      {'page': 1},
    );
  });
}
