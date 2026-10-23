import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attendance_sheet_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/teacher_section_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/teacher_service.dart';

import '../support/fakes.dart';

void main() {
  test('section and sheet models read the API fields', () {
    final detail = SectionAttendanceModel.fromJson(sectionAttendanceJson());
    final sheet = AttendanceSheetModel.fromJson(
      sheetJson(marked: true, locked: true),
    );
    final bareRow = AttendanceSheetRowModel.fromJson({'studentId': 9});

    expect(detail.section.status, SectionStatus.open);
    expect(detail.sessions.items.first.hasAttendance, isTrue);
    expect(detail.sessions.items.last.isCancelled, isFalse);
    expect(detail.students.items.single.summary.present, 8);
    expect(detail.thresholdPercent, 20);
    expect(sheet.locked, isTrue);
    expect(sheet.reasonRequired, isTrue);
    expect(sheet.editableUntil, '2026-10-22T09:00:00');
    expect(sheet.rows[1].note, 'Không báo trước');
    expect(sheet.rows[2].approvedLeave, isTrue);
    expect(bareRow.status, isNull);
    expect(bareRow.approvedLeave, isFalse);
  });

  test('sections, detail and sheet use their paths', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/teacher/sections',
      FakeReply(200, pagedJson([teacherSectionJson()])),
    );
    app.http.reply(
      'GET',
      '/teacher/sections/1/attendance',
      FakeReply(200, sectionAttendanceJson()),
    );
    app.http.reply(
      'GET',
      '/teacher/sections/1/sessions/2/attendance',
      FakeReply(200, sheetJson()),
    );
    final service = TeacherService(app.apiClient);

    await service.fetchSections();
    await service.fetchSections(semesterId: 38, page: 1);
    await service.fetchSectionAttendance(1, sessionPage: 2, studentPage: 3);
    final sheet = await service.fetchSheet(1, 2);

    expect(app.http.requests.map((r) => r.queryParameters).toList(), [
      {'page': 0},
      {'semesterId': 38, 'page': 1},
      {'sessionPage': 2, 'studentPage': 3},
      <String, dynamic>{},
    ]);
    expect(sheet.rows, hasLength(3));
  });

  test(
    'save sends string keys, notes only when given, reason when set',
    () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'POST',
        '/teacher/sections/1/sessions/2/attendance',
        FakeReply(200, {
          'firstTime': false,
          'changed': 1,
          'sheet': sheetJson(),
        }),
      );
      final service = TeacherService(app.apiClient);

      await service.saveSheet(1, 2, statuses: {1: 'PRESENT'});
      final result = await service.saveSheet(
        1,
        2,
        statuses: {1: 'ABSENT_EXCUSED'},
        notes: {1: ''},
        reason: 'Có giấy phép',
      );

      expect(app.http.requests.first.data, {
        'statuses': {'1': 'PRESENT'},
      });
      expect(app.http.requests.last.data, {
        'statuses': {'1': 'ABSENT_EXCUSED'},
        'notes': {'1': ''},
        'reason': 'Có giấy phép',
      });
      expect(result.changed, 1);
      expect(result.firstTime, isFalse);
    },
  );

  test('review queue, request and review use their paths and body', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/teacher/requests',
      FakeReply(200, pageJson([requestJson()], totalElements: 4)),
    );
    app.http.reply(
      'GET',
      '/teacher/requests/1',
      FakeReply(200, requestDetailJson(canReview: true)),
    );
    app.http.reply(
      'POST',
      '/teacher/requests/1/review',
      FakeReply(200, {
        'decision': 'REJECTED',
        'excusedSessions': 0,
        'request': requestDetailJson(status: 'REJECTED'),
      }),
    );
    final service = TeacherService(app.apiClient);

    final queue = await service.fetchReviewRequests();
    await service.fetchReviewRequests(status: 'ALL', page: 2);
    final detail = await service.fetchReviewRequest(1);
    final result = await service.review(
      1,
      decision: 'REJECTED',
      comment: '  Thiếu giấy  ',
    );

    expect(queue.totalElements, 4);
    expect(app.http.requests[0].queryParameters, {'page': 0});
    expect(app.http.requests[1].queryParameters, {'status': 'ALL', 'page': 2});
    expect(detail.canReview, isTrue);
    expect(app.http.requests.last.data, {
      'decision': 'REJECTED',
      'comment': 'Thiếu giấy',
    });
    expect(result.decision, 'REJECTED');
    expect(result.request.status, 'REJECTED');
    expect(result.request.canReview, isFalse);
  });
}
