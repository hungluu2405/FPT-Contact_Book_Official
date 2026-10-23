import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/change_password_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/schedule/list_exam_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/schedule/view_schedule_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/feature_tile_widget.dart';

import 'support/app_driver.dart';
import 'support/fakes.dart';

/// SV đã đăng nhập, có sẵn trả lời cho lịch thi và hồ sơ.
Future<TestApp> pumpStudent(WidgetTester tester) async {
  final app = TestApp(accessToken: 'a', refreshToken: 'r');
  app.http.reply('GET', '/me', FakeReply(200, userJson()));
  app.http.reply('GET', '/me/profile', FakeReply(200, profileJson()));
  app.http.reply('GET', '/student/exams', FakeReply(200, pagedJson([])));
  app.stubHome();
  await pumpApp(tester, app: app);
  return app;
}

Future<void> openTile(WidgetTester tester, String label) async {
  final tile = find.widgetWithText(FeatureTileWidget, label);
  await tester.ensureVisible(tile);
  await tester.tap(tile);
  await tester.pumpAndSettle();
}

Future<void> tapTab(WidgetTester tester, String label) async {
  await tester.tap(
    find.descendant(of: find.byType(NavigationBar), matching: find.text(label)),
  );
  await tester.pumpAndSettle();
}

/// Nút back của hệ thống Android.
Future<void> systemBack(WidgetTester tester) async {
  await tester.binding.handlePopRoute();
  await tester.pumpAndSettle();
}

bool get onHomeRoot =>
    find.byType(FeatureTileWidget).evaluate().isNotEmpty &&
    find.byType(ViewScheduleScreen).evaluate().isEmpty &&
    find.byType(ListExamScreen).evaluate().isEmpty;

void main() {
  testWidgets('the bottom bar stays on feature screens', (tester) async {
    await pumpStudent(tester);

    await openTile(tester, 'Lịch thi');

    expect(find.byType(ListExamScreen), findsOneWidget);
    expect(find.byType(NavigationBar), findsOneWidget);
  });

  testWidgets('the back arrow in the header returns home', (tester) async {
    await pumpStudent(tester);
    await openTile(tester, 'Lịch học');

    await tester.tap(find.byTooltip('Quay lại'));
    await tester.pumpAndSettle();

    expect(onHomeRoot, isTrue);
  });

  testWidgets('switching tabs keeps the open screen of a tab', (tester) async {
    await pumpStudent(tester);
    await openTile(tester, 'Lịch học');

    await tapTab(tester, 'Tài khoản');
    expect(find.text('an.nv@fpt.edu.vn'), findsOneWidget);

    await tapTab(tester, 'Trang chủ');
    expect(find.byType(ViewScheduleScreen), findsOneWidget);
  });

  testWidgets('tapping the current tab again goes to its root', (
    tester,
  ) async {
    await pumpStudent(tester);
    await openTile(tester, 'Lịch học');

    await tapTab(tester, 'Trang chủ');

    expect(onHomeRoot, isTrue);
  });

  testWidgets('password change from the account tab keeps the bar', (
    tester,
  ) async {
    await pumpStudent(tester);
    await tapTab(tester, 'Tài khoản');

    final button = find.widgetWithText(OutlinedButton, 'Đổi mật khẩu');
    await tester.ensureVisible(button);
    await tester.tap(button);
    await tester.pumpAndSettle();

    expect(find.byType(ChangePasswordScreen), findsOneWidget);
    expect(find.byType(NavigationBar), findsOneWidget);
  });

  group('system back', () {
    testWidgets('pops the screen opened in the tab first', (tester) async {
      await pumpStudent(tester);
      await openTile(tester, 'Lịch thi');

      await systemBack(tester);

      expect(onHomeRoot, isTrue);
    });

    testWidgets('goes from the root of another tab to home', (tester) async {
      await pumpStudent(tester);
      await tapTab(tester, 'Tài khoản');

      await systemBack(tester);

      expect(onHomeRoot, isTrue);
      expect(find.text('an.nv@fpt.edu.vn'), findsNothing);
    });

    testWidgets('leaves the app from the root of home', (tester) async {
      var exited = false;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'SystemNavigator.pop') {
            exited = true;
          }
          return null;
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      await pumpStudent(tester);

      await systemBack(tester);

      expect(exited, isTrue);
    });
  });
}
