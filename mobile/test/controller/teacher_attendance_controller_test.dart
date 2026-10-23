import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attendance_model.dart';

import '../support/fakes.dart';

const String kSheetPath = '/teacher/sections/1/sessions/2/attendance';

void main() {
  group('sections', () {
    test('open loads semesters and sections of the current semester', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply('GET', '/semesters', FakeReply(200, semestersJson()));
      app.http.reply(
        'GET',
        '/teacher/sections',
        FakeReply(200, pagedJson([teacherSectionJson()], totalPages: 2)),
      );
      final controller = app.teacherSectionController;

      await controller.open();

      expect(controller.semesters, hasLength(3));
      expect(controller.semester?.id, 1);
      expect(controller.sections.single.code, 'TOAN10-HK1-2627-01');
      expect(controller.sectionsHasMore, isTrue);
      expect(app.http.requests.last.queryParameters, {'page': 0});
    });

    test('choosing a semester reloads its sections', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply('GET', '/semesters', FakeReply(200, semestersJson()));
      app.http.on('GET', '/teacher/sections', (request) async {
        final id = request.queryParameters['semesterId'] as int?;
        return FakeReply(
          200,
          pagedJson(
            id == 38 ? [] : [teacherSectionJson()],
            semesterId: id ?? 1,
          ),
        );
      });
      final controller = app.teacherSectionController;
      await controller.open();

      await controller.selectSemester(38);

      expect(app.http.requests.last.queryParameters, {
        'semesterId': 38,
        'page': 0,
      });
      expect(controller.sections, isEmpty);
      expect(controller.semester?.id, 38);
    });

    test('the section detail pages sessions and students separately', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.on('GET', '/teacher/sections/1/attendance', (request) async {
        final sessionPage = request.queryParameters['sessionPage'] as int;
        final studentPage = request.queryParameters['studentPage'] as int;
        return FakeReply(
          200,
          sectionAttendanceJson(
            sessions: [sectionSessionJson(id: sessionPage + 1)],
            students: [sectionStudentJson(studentId: studentPage + 1)],
            sessionPage: sessionPage,
            studentPage: studentPage,
            sessionPages: 2,
            studentPages: 2,
          ),
        );
      });
      final controller = app.teacherSectionController;

      await controller.openSection(1);
      await controller.loadMoreSessions();
      await controller.loadMoreStudents();

      expect(controller.sessions.map((s) => s.id), [1, 2]);
      expect(controller.students.map((s) => s.studentId), [1, 2]);
      expect(controller.sessionsHasMore, isFalse);
      expect(controller.studentsHasMore, isFalse);
      expect(controller.thresholdPercent, 20);
      expect(controller.section?.subjectName, 'Toán 10');
    });

    test('a late detail of another section is dropped', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final slow = Completer<FakeReply>();
      app.http.on('GET', '/teacher/sections/1/attendance', (_) => slow.future);
      app.http.reply(
        'GET',
        '/teacher/sections/2/attendance',
        FakeReply(200, sectionAttendanceJson(sessions: [])),
      );
      final controller = app.teacherSectionController;

      final old = controller.openSection(1);
      await controller.openSection(2);
      slow.complete(FakeReply(200, sectionAttendanceJson()));
      await old;

      expect(controller.sessions, isEmpty);
      expect(controller.isLoadingSection, isFalse);
    });
  });

  group('sheet', () {
    test(
      'an unmarked session starts as present, approved leave as excused',
      () async {
        final app = TestApp(accessToken: 'a', refreshToken: 'r');
        app.http.reply('GET', kSheetPath, FakeReply(200, sheetJson()));
        final controller = app.attendanceSheetController;

        await controller.open(1, 2);

        expect(controller.statusOf(1), AttendanceStatus.present);
        expect(controller.statusOf(2), AttendanceStatus.present);
        expect(controller.statusOf(3), AttendanceStatus.absentExcused);
        expect(controller.counts, {
          AttendanceStatus.present: 2,
          AttendanceStatus.absentExcused: 1,
          AttendanceStatus.absentUnexcused: 0,
        });
        expect(controller.needsReason, isFalse);
        expect(controller.canEdit, isTrue);
      },
    );

    test('a first save sends every status and only changed notes', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply('GET', kSheetPath, FakeReply(200, sheetJson()));
      app.http.reply(
        'POST',
        kSheetPath,
        FakeReply(200, {
          'firstTime': true,
          'changed': 3,
          'sheet': sheetJson(marked: true),
        }),
      );
      final controller = app.attendanceSheetController;
      await controller.open(1, 2);

      controller.setStatus(2, AttendanceStatus.absentUnexcused);
      controller.setNote(2, '  Không báo trước  ');
      final result = await controller.save();

      expect(result?.changed, 3);
      expect(app.http.requests.last.data, {
        'statuses': {
          '1': 'PRESENT',
          '2': 'ABSENT_UNEXCUSED',
          '3': 'ABSENT_EXCUSED',
        },
        'notes': {'2': 'Không báo trước'},
      });
      expect(controller.sheet?.marked, isTrue);
      expect(controller.statusOf(2), AttendanceStatus.absentUnexcused);
      expect(controller.isSaving, isFalse);
    });

    test('changing a status of a marked session needs a reason', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        kSheetPath,
        FakeReply(200, sheetJson(marked: true)),
      );
      app.http.reply(
        'POST',
        kSheetPath,
        FakeReply(200, {
          'firstTime': false,
          'changed': 1,
          'sheet': sheetJson(marked: true),
        }),
      );
      final controller = app.attendanceSheetController;
      await controller.open(1, 2);

      expect(controller.statusOf(2), AttendanceStatus.absentUnexcused);
      controller.setNote(1, 'Phát biểu tốt');
      expect(controller.needsReason, isFalse);

      controller.setStatus(2, AttendanceStatus.absentExcused);
      expect(controller.needsReason, isTrue);
      expect(await controller.save(), isNull);
      expect(
        controller.reasonError,
        'Vui lòng nhập lý do điều chỉnh điểm danh',
      );
      expect(app.http.count('POST', kSheetPath), 0);

      await controller.save(reason: '  Phụ huynh gửi giấy xin phép  ');

      expect(app.http.requests.last.data, {
        'statuses': {
          '1': 'PRESENT',
          '2': 'ABSENT_EXCUSED',
          '3': 'ABSENT_EXCUSED',
        },
        'notes': {'1': 'Phát biểu tốt'},
        'reason': 'Phụ huynh gửi giấy xin phép',
      });
      expect(controller.reasonError, isNull);
    });

    test('a cleared note is sent as empty to remove it', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        kSheetPath,
        FakeReply(200, sheetJson(marked: true)),
      );
      app.http.reply(
        'POST',
        kSheetPath,
        FakeReply(200, {
          'firstTime': false,
          'changed': 1,
          'sheet': sheetJson(marked: true),
        }),
      );
      final controller = app.attendanceSheetController;
      await controller.open(1, 2);

      controller.setNote(2, '');
      await controller.save();

      expect((app.http.requests.last.data as Map)['notes'], {'2': ''});
    });

    test('hasChanges compares the choices with the loaded sheet', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        kSheetPath,
        FakeReply(200, sheetJson(marked: true)),
      );
      app.http.reply(
        'POST',
        kSheetPath,
        FakeReply(200, {
          'firstTime': false,
          'changed': 0,
          'sheet': sheetJson(marked: true),
        }),
      );
      final controller = app.attendanceSheetController;
      await controller.open(1, 2);
      expect(controller.hasChanges, isFalse);

      controller.setStatus(2, AttendanceStatus.absentExcused);
      expect(controller.hasChanges, isTrue);
      controller.setStatus(2, AttendanceStatus.absentUnexcused);
      expect(controller.hasChanges, isFalse);

      controller.setNote(1, '  ');
      expect(controller.hasChanges, isFalse);
      controller.setNote(1, 'Phát biểu tốt');
      expect(controller.hasChanges, isTrue);

      await controller.save();
      expect(controller.hasChanges, isFalse);
    });

    test('an unmarked sheet with the preset choices has no changes', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply('GET', kSheetPath, FakeReply(200, sheetJson()));
      final controller = app.attendanceSheetController;
      await controller.open(1, 2);

      expect(controller.hasChanges, isFalse);
      controller.setStatus(1, AttendanceStatus.absentUnexcused);
      expect(controller.hasChanges, isTrue);
    });

    test('a locked sheet never has changes', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        kSheetPath,
        FakeReply(200, sheetJson(marked: true, locked: true)),
      );
      final controller = app.attendanceSheetController;
      await controller.open(1, 2);

      controller.setStatus(1, AttendanceStatus.absentUnexcused);

      expect(controller.hasChanges, isFalse);
    });

    test('mark all present keeps approved leave as excused', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply('GET', kSheetPath, FakeReply(200, sheetJson()));
      final controller = app.attendanceSheetController;
      await controller.open(1, 2);
      controller.setStatus(1, AttendanceStatus.absentUnexcused);

      controller.markAllPresent();

      expect(controller.statusOf(1), AttendanceStatus.present);
      expect(controller.statusOf(3), AttendanceStatus.absentExcused);
    });

    test('a locked sheet cannot be changed or saved', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        kSheetPath,
        FakeReply(200, sheetJson(marked: true, locked: true)),
      );
      final controller = app.attendanceSheetController;
      await controller.open(1, 2);

      controller.setStatus(1, AttendanceStatus.absentUnexcused);

      expect(controller.canEdit, isFalse);
      expect(controller.statusOf(1), AttendanceStatus.present);
      expect(await controller.save(), isNull);
      expect(app.http.count('POST', kSheetPath), 0);
    });

    test('a backend error keeps the choices and shows the message', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply('GET', kSheetPath, FakeReply(200, sheetJson()));
      app.http.reply(
        'POST',
        kSheetPath,
        FakeReply.error(
          400,
          'BUSINESS_ERROR',
          'Điểm danh vừa được người khác lưu, vui lòng tải lại trang và thử lại',
        ),
      );
      final controller = app.attendanceSheetController;
      await controller.open(1, 2);
      controller.setStatus(2, AttendanceStatus.absentUnexcused);

      expect(await controller.save(), isNull);

      expect(
        controller.saveError,
        'Điểm danh vừa được người khác lưu, vui lòng tải lại trang và thử lại',
      );
      expect(controller.statusOf(2), AttendanceStatus.absentUnexcused);
      expect(controller.isSaving, isFalse);
    });

    test('a field error on reason is shown on the reason field', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        kSheetPath,
        FakeReply(200, sheetJson(marked: true)),
      );
      app.http.reply(
        'POST',
        kSheetPath,
        FakeReply.error(
          400,
          'VALIDATION_FAILED',
          'Dữ liệu gửi lên không hợp lệ',
          fieldErrors: [
            {'field': 'reason', 'message': 'Lý do tối đa 255 ký tự'},
          ],
        ),
      );
      final controller = app.attendanceSheetController;
      await controller.open(1, 2);
      controller.setStatus(1, AttendanceStatus.absentUnexcused);

      await controller.save(reason: 'x');

      expect(controller.reasonError, 'Lý do tối đa 255 ký tự');
      expect(controller.saveError, isNull);
    });
  });
}
