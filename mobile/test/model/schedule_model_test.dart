import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/exam_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/page_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/schedule_item_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/schedule_week_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/semester_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

import '../support/fakes.dart';

void main() {
  group('schedule models', () {
    test('week reads semester, days and the today flag', () {
      final result = InSemesterModel.fromJson(
        weekJson(
          items: {
            '2026-10-21': [sessionJson(), sessionJson(start: '09:30')],
          },
        ),
        (data) => ScheduleWeekModel.fromJson(data as Map<String, dynamic>),
      );

      expect(result.semester.name, 'Học kỳ I năm học 2026–2027');
      expect(result.data.days, hasLength(7));
      expect(result.data.weekStart, '2026-10-19');
      expect(result.data.previous, '2026-10-12');
      expect(result.data.next, '2026-10-26');
      expect(result.data.todayDay!.date, '2026-10-21');
      expect(result.data.todayDay!.items, hasLength(2));
    });

    test('week outside today has no todayDay', () {
      final week = ScheduleWeekModel.fromJson(
        weekJson(today: null)['data'] as Map<String, dynamic>,
      );

      expect(week.todayDay, isNull);
    });

    test('session keeps nullable fields and knows when cancelled', () {
      final item = ScheduleItemModel.fromJson({
        ...sessionJson(status: SessionStatus.cancelled),
        'roomCode': null,
        'teacherName': null,
      });

      expect(item.isCancelled, isTrue);
      expect(item.roomCode, isNull);
      expect(item.teacherName, isNull);
      expect(item.toJson()['status'], 'CANCELLED');
    });

    test('page of exams reads paging fields', () {
      final page = PageModel.fromJson(
        pagedJson(
              [examJson(), examJson(id: 2, type: ExamType.finalExam)],
              totalPages: 3,
              totalElements: 45,
            )['data']
            as Map<String, dynamic>,
        ExamModel.fromJson,
      );

      expect(page.items.map((exam) => exam.id), [1, 2]);
      expect(page.items.last.examType, 'FINAL');
      expect(page.totalElements, 45);
      expect(page.hasMore, isTrue);
    });

    test('empty last page has nothing more', () {
      final page = PageModel.fromJson({
        'items': [],
        'page': 0,
        'size': 20,
        'totalElements': 0,
        'totalPages': 0,
      }, ExamModel.fromJson);

      expect(page.items, isEmpty);
      expect(page.hasMore, isFalse);
    });
  });

  group('date and time helpers', () {
    test('parse and print API dates', () {
      expect(parseApiDate('2026-10-21'), DateTime(2026, 10, 21));
      expect(parseApiDate('21/10/2026'), isNull);
      expect(parseApiDate(null), isNull);
      expect(toApiDate(DateTime(2026, 1, 5)), '2026-01-05');
    });

    test('time accepts HH:mm:ss and HH:mm', () {
      expect(formatApiTime('07:30:00'), '07:30');
      expect(formatApiTime('07:30'), '07:30');
      expect(formatApiTime(null), '');
      expect(minutesOfDay('09:20:00'), 9 * 60 + 20);
      expect(minutesOfDay('x'), isNull);
    });

    test('Vietnamese day names', () {
      final wednesday = DateTime(2026, 10, 21);
      final sunday = DateTime(2026, 10, 25);

      expect(weekdayShort(wednesday), 'T4');
      expect(weekdayShort(sunday), 'CN');
      expect(weekdayLong(wednesday), 'Thứ Tư');
      expect(dayMonthText(wednesday), '21 tháng 10');
      expect(dayMonthShort(DateTime(2026, 3, 4)), '04/03');
      expect(monthShort(wednesday), 'Th10');
    });
  });
}
