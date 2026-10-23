import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attendance_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/grade_row_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/semester_option_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/number_format.dart';

import '../support/fakes.dart';

void main() {
  group('grade models', () {
    test('row reads cells; unpublished score and average stay null', () {
      final row = GradeRowModel.fromJson(
        gradeRowJson(scores: [8, null, 9.25], average: null),
      );

      expect(row.subjectCode, 'TOAN10');
      expect(row.cells, hasLength(3));
      expect(row.cells[0].weight, 20);
      expect(row.cells[1].score, isNull);
      expect(row.cells[2].score, 9.25);
      expect(row.average, isNull);
    });

    test('a subject without components has no cells', () {
      final row = GradeRowModel.fromJson(gradeRowJson(scores: []));

      expect(row.cells, isEmpty);
    });

    test('semester option reads id, code and name', () {
      final option = SemesterOptionModel.fromJson(semestersJson().first);

      expect(option.id, 38);
      expect(option.name, 'Học kỳ II năm học 2026–2027');
    });
  });

  group('attendance models', () {
    test('summary reads counts and rates; marked is the sum', () {
      final summary = AttendanceSummaryModel.fromJson(
        summaryJson(present: 8, excused: 1, unexcused: 2, rate: 72.7),
      );

      expect(summary.marked, 11);
      expect(summary.attendanceRate, 72.7);
      expect(summary.warning, isFalse);
    });

    test('rate is null before the first marked session', () {
      final summary = AttendanceSummaryModel.fromJson(
        summaryJson(present: 0, excused: 0, unexcused: 0, rate: null),
      );

      expect(summary.attendanceRate, isNull);
      expect(summary.marked, 0);
    });

    test('history reads the section, threshold and a page of sessions', () {
      final history = AttendanceHistoryModel.fromJson(
        attendanceHistoryJson(
          [
            attendanceSessionJson(note: 'Muộn 10 phút'),
            attendanceSessionJson(no: 2, status: null),
          ],
          totalPages: 3,
          row: attendanceRowJson(sectionId: 27),
        ),
      );

      expect(history.section.sectionId, 27);
      expect(history.thresholdPercent, 20);
      expect(history.sessions.items.first.note, 'Muộn 10 phút');
      expect(history.sessions.items.last.status, isNull);
      expect(history.sessions.hasMore, isTrue);
    });
  });

  group('number format', () {
    test('decimals use a comma and drop trailing zeros', () {
      expect(formatDecimal(8.5), '8,5');
      expect(formatDecimal(10), '10');
      expect(formatDecimal(9.25), '9,25');
      expect(formatDecimal(8.3, maxDecimals: 1), '8,3');
    });

    test('percent keeps one decimal like the web', () {
      expect(formatPercent(80), '80%');
      expect(formatPercent(72.7), '72,7%');
      expect(formatPercent(20), '20%');
    });
  });
}
