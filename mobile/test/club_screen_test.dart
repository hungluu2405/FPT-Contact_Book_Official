import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/club_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/club/list_club_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/club/view_club_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/feature_tile_widget.dart';

import 'support/app_driver.dart';
import 'support/fakes.dart';

/// Đã đăng nhập với [roles]; trả lời sẵn các API câu lạc bộ.
Future<TestApp> pumpWithClubs(
  WidgetTester tester, {
  List<String> roles = const ['STUDENT'],
  List<Map<String, dynamic>> children = const [],
  List<Map<String, dynamic>> mine = const [],
}) async {
  final app = TestApp(accessToken: 'a', refreshToken: 'r');
  app.http.reply('GET', '/me', FakeReply(200, userJson(roles: roles)));
  app.stubHome(children: children);
  for (final root in ['/student', '/parent/children/7']) {
    app.http.reply('GET', '$root/clubs', FakeReply(200, mine));
  }
  app.http.on('GET', '/clubs', (request) async {
    final q = request.queryParameters['q'];
    final all = [
      clubJson(),
      clubJson(id: 2, code: 'F-GUITAR', name: 'Câu lạc bộ Âm nhạc F-Guitar'),
    ];
    return FakeReply(
      200,
      pageJson(
        q == null
            ? all
            : all
                  .where(
                    (c) => (c['name'] as String).toLowerCase().contains(
                      (q as String).toLowerCase(),
                    ),
                  )
                  .toList(),
      ),
    );
  });
  app.http.reply(
    'GET',
    '/clubs/1',
    FakeReply(200, clubDetailJson(myStatus: 'ACTIVE', myRole: 'MEMBER')),
  );
  app.http.reply(
    'GET',
    '/clubs/1/activities',
    FakeReply(200, [clubActivityJson(myStatus: 'REGISTERED')]),
  );
  app.http.reply(
    'GET',
    '/clubs/1/members',
    FakeReply(
      200,
      pageJson([
        clubMemberJson(),
        clubMemberJson(name: 'Vũ Thị Mai', role: ClubMemberRole.member),
      ]),
    ),
  );
  await pumpApp(tester, app: app);
  return app;
}

Future<void> openClubs(WidgetTester tester) async {
  final tile = find.widgetWithText(FeatureTileWidget, 'Câu lạc bộ');
  await tester.ensureVisible(tile);
  await tester.tap(tile);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('student sees own clubs and all clubs', (tester) async {
    await pumpWithClubs(
      tester,
      mine: [
        myClubJson(),
        myClubJson(
          clubId: 2,
          code: 'F-GUITAR',
          name: 'Câu lạc bộ Âm nhạc F-Guitar',
          status: 'PENDING',
        ),
      ],
    );

    await openClubs(tester);

    expect(find.byType(ListClubScreen), findsOneWidget);
    expect(find.text('CLB của tôi'), findsOneWidget);
    expect(find.text('Đang tham gia 1 câu lạc bộ'), findsOneWidget);
    expect(find.text('Thành viên'), findsOneWidget);
    expect(find.text('Chờ duyệt'), findsOneWidget);
    expect(find.text('Tất cả câu lạc bộ'), findsOneWidget);
  });

  testWidgets('no club yet shows a short message', (tester) async {
    await pumpWithClubs(tester);

    await openClubs(tester);

    expect(find.text('Bạn chưa tham gia câu lạc bộ nào.'), findsOneWidget);
  });

  testWidgets('search sends the keyword and shows matches', (tester) async {
    final app = await pumpWithClubs(tester);
    await openClubs(tester);

    await tester.enterText(find.byType(TextField), ' guitar ');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    expect(app.http.requests.last.queryParameters, {'q': 'guitar', 'page': 0});
    expect(find.text('Câu lạc bộ Âm nhạc F-Guitar'), findsOneWidget);
    expect(find.text('Câu lạc bộ Lập trình F-Code'), findsNothing);
  });

  testWidgets('club detail shows info, activities and members', (tester) async {
    tester.view.physicalSize = const Size(800, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await pumpWithClubs(tester);
    await openClubs(tester);

    await tester.tap(find.text('Câu lạc bộ Lập trình F-Code'));
    await tester.pumpAndSettle();

    expect(find.byType(ViewClubScreen), findsOneWidget);
    expect(find.text('Đang tham gia · Thành viên'), findsOneWidget);
    expect(find.text('ThS. Hoàng Văn Code'), findsOneWidget);
    expect(find.text('15/09/2020'), findsOneWidget);
    expect(find.text('Luyện thuật toán'), findsOneWidget);
    expect(find.text('Thứ Bảy, 24/10 · 14:00–16:00'), findsOneWidget);
    expect(find.text('LAB-401 · 12/30 chỗ'), findsOneWidget);
    expect(find.text('Đã đăng ký'), findsOneWidget);
    expect(find.text('Chủ nhiệm'), findsOneWidget);
    expect(find.text('Vũ Thị Mai'), findsOneWidget);
    expect(find.textContaining('trên trang web của trường'), findsOneWidget);
  });

  testWidgets('parent sees the clubs of the selected child', (tester) async {
    final app = await pumpWithClubs(
      tester,
      roles: ['PARENT'],
      children: [childJson(id: 7)],
    );

    await openClubs(tester);

    expect(find.text('CLB của con'), findsOneWidget);
    expect(find.text('Con chưa tham gia câu lạc bộ nào.'), findsOneWidget);
    expect(app.http.count('GET', '/parent/children/7/clubs'), 1);
  });
}
