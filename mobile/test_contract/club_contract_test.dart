import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/club_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';

import 'auth_contract_test.dart' show ContractClient, apiError, kSeedPassword;

/// Hợp đồng câu lạc bộ (giai đoạn 10b-4), chỉ đọc dữ liệu mẫu: CLB F-CODE
/// (chủ nhiệm HS250101) và F-GUITAR.
///
/// Chạy: `flutter test test_contract` khi backend đang chạy ở cổng 8080.
void main() {
  Future<ContractClient> signIn(String phone) async {
    final client = ContractClient();
    await client.auth.login(phone, kSeedPassword);
    return client;
  }

  test('every role lists active clubs and searches by name', () async {
    for (final phone in ['0900000004', '0900000005', '0900000003']) {
      final service = ClubService((await signIn(phone)).api);

      final all = await service.fetchClubs();
      final found = await service.fetchClubs(query: 'guitar');

      expect(all.items.map((c) => c.code), containsAll(['F-CODE', 'F-GUITAR']));
      expect(all.items.every((c) => c.isActive), isTrue);
      expect(found.items.map((c) => c.code), ['F-GUITAR']);
    }
  });

  test('detail, members and activities of F-CODE', () async {
    final service = ClubService((await signIn('0900000004')).api);
    final club = (await service.fetchClubs(query: 'F-Code')).items.single;

    final detail = await service.fetchClub(club.id);
    final members = await service.fetchMembers(club.id);
    final activities = await service.fetchActivities(club.id);

    expect(detail.club.code, 'F-CODE');
    expect(detail.club.presidentName, isNotEmpty);
    expect(members.totalElements, detail.club.memberCount);
    expect(members.items.first.role, 'PRESIDENT');
    for (final activity in activities) {
      expect(activity.title, isNotEmpty);
      expect(activity.state, isNot('CANCELLED'));
    }
  });

  test('president sees the club as an active member', () async {
    final client = await signIn('0933112233');
    final service = ClubService(client.api);

    final mine = await service.fetchMyClubs(null);

    final fcode = mine.firstWhere((c) => c.code == 'F-CODE');
    expect(fcode.role, 'PRESIDENT');
    expect(fcode.status, 'ACTIVE');
  });

  test('parent reads the clubs of HS260001 only', () async {
    final client = await signIn('0900000005');
    final children = await StudentService(client.api).fetchChildren();
    final childId = children.firstWhere((c) => c.code == 'HS260001').id;
    final service = ClubService(client.api);

    final clubs = await service.fetchMyClubs(childId);

    expect(clubs, isA<List<Object>>());
    expect(service.fetchMyClubs(999999), throwsA(apiError('FORBIDDEN')));
  });

  test('unknown club is 404', () async {
    final service = ClubService((await signIn('0900000004')).api);

    expect(service.fetchClub(999999), throwsA(apiError('NOT_FOUND')));
  });
}
