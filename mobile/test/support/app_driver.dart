import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/main.dart';

import 'fakes.dart';

/// Khởi động app với tầng HTTP giả, giống main(): khôi phục phiên rồi
/// hiện màn hình theo trạng thái. Request chạy trong lúc pump nên các
/// timer của Dio chạy theo đồng hồ giả của test.
Future<TestApp> pumpApp(WidgetTester tester, {TestApp? app}) async {
  final testApp = app ?? TestApp();
  unawaited(testApp.controller.restoreSession());
  await tester.pumpWidget(
    MyApp(
      authController: testApp.controller,
      passwordResetController: testApp.resetController,
      homeController: testApp.homeController,
      profileController: testApp.profileController,
      scheduleController: testApp.scheduleController,
      examController: testApp.examController,
      gradeController: testApp.gradeController,
      attendanceController: testApp.attendanceController,
      clubController: testApp.clubController,
      notificationController: testApp.notificationController,
      requestController: testApp.requestController,
      departmentController: testApp.departmentController,
      departmentNotificationController:
          testApp.departmentNotificationController,
      teacherSectionController: testApp.teacherSectionController,
      attendanceSheetController: testApp.attendanceSheetController,
      requestReviewController: testApp.requestReviewController,
    ),
  );
  await tester.pumpAndSettle();
  return testApp;
}

Finder field(String label) =>
    find.ancestor(of: find.text(label), matching: find.byType(Column)).first;

Future<void> enter(WidgetTester tester, String label, String text) async {
  final input = find.descendant(
    of: field(label),
    matching: find.byType(TextFormField),
  );
  await tester.ensureVisible(input);
  await tester.enterText(input, text);
}

Future<void> tapButton(WidgetTester tester, String label) async {
  final button = find.widgetWithText(FilledButton, label);
  await tester.ensureVisible(button);
  await tester.tap(button);
  await tester.pumpAndSettle();
}
