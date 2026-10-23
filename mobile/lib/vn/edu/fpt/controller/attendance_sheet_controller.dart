import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attendance_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attendance_sheet_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/teacher_service.dart';

/// Bảng điểm danh một buổi của giáo viên. Buổi chưa điểm danh chọn sẵn "Có
/// mặt" (học sinh có đơn nghỉ đã duyệt chọn "Vắng có phép"); giáo viên chỉ đổi
/// người vắng. Đổi trạng thái của buổi đã điểm danh phải có lý do; bảng quá
/// hạn (locked) chỉ xem.
class AttendanceSheetController extends ChangeNotifier {
  static const int noteMax = 255;
  static const int reasonMax = 255;
  static const String reasonRequired =
      'Vui lòng nhập lý do điều chỉnh điểm danh';
  static const String reasonSize = 'Lý do tối đa $reasonMax ký tự';
  static const String noteSize = 'Ghi chú tối đa $noteMax ký tự';

  final TeacherService _teacherService;

  int? _sectionId;
  int? _sessionId;
  AttendanceSheetModel? _sheet;
  bool _loading = false;
  ApiErrorModel? _error;

  final Map<int, String> _statuses = {};
  final Map<int, String> _notes = {};
  bool _saving = false;
  String? _reasonError;
  String? _saveError;

  /// Tăng mỗi lần mở buổi khác để bỏ phản hồi về muộn.
  int _generation = 0;

  AttendanceSheetController(this._teacherService);

  AttendanceSheetModel? get sheet => _sheet;

  bool get isLoading => _loading;

  ApiErrorModel? get error => _error;

  bool get isSaving => _saving;

  String? get reasonError => _reasonError;

  /// Lỗi khi lưu không gắn ô lý do (ví dụ người khác vừa lưu, quá hạn).
  String? get saveError => _saveError;

  /// Còn sửa được: đã tải bảng và chưa quá hạn.
  bool get canEdit => _sheet != null && !_sheet!.locked;

  String statusOf(int studentId) =>
      _statuses[studentId] ?? AttendanceStatus.present;

  String noteOf(int studentId) => _notes[studentId] ?? '';

  /// Số học sinh theo từng trạng thái đang chọn (đủ ba trạng thái).
  Map<String, int> get counts {
    final result = {
      AttendanceStatus.present: 0,
      AttendanceStatus.absentExcused: 0,
      AttendanceStatus.absentUnexcused: 0,
    };
    for (final row in _sheet?.rows ?? const <AttendanceSheetRowModel>[]) {
      final status = statusOf(row.studentId);
      result[status] = (result[status] ?? 0) + 1;
    }
    return result;
  }

  /// Phải có lý do: buổi đã điểm danh và có học sinh đổi trạng thái so với
  /// lần lưu trước (chỉ đổi ghi chú thì không cần).
  bool get needsReason {
    final sheet = _sheet;
    if (sheet == null || !sheet.reasonRequired) {
      return false;
    }
    return sheet.rows.any(
      (row) => row.status != null && row.status != statusOf(row.studentId),
    );
  }

  /// Mở bảng điểm danh của buổi [sessionId] thuộc lớp [sectionId].
  Future<void> open(int sectionId, int sessionId) async {
    final generation = ++_generation;
    _sectionId = sectionId;
    _sessionId = sessionId;
    _sheet = null;
    _statuses.clear();
    _notes.clear();
    _saving = false;
    _reasonError = null;
    _saveError = null;
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final sheet = await _teacherService.fetchSheet(sectionId, sessionId);
      if (generation == _generation) {
        _showSheet(sheet);
      }
    } on ApiErrorModel catch (error) {
      if (generation == _generation) {
        _error = error;
      }
    } finally {
      if (generation == _generation) {
        _loading = false;
        notifyListeners();
      }
    }
  }

  /// Tải lại bảng (sau lỗi người khác vừa lưu, kéo xuống để tải lại).
  Future<void> reload() async {
    final sectionId = _sectionId;
    final sessionId = _sessionId;
    if (sectionId != null && sessionId != null) {
      await open(sectionId, sessionId);
    }
  }

  void _showSheet(AttendanceSheetModel sheet) {
    _sheet = sheet;
    _statuses.clear();
    _notes.clear();
    for (final row in sheet.rows) {
      _statuses[row.studentId] = row.status ?? _defaultStatus(row);
      _notes[row.studentId] = row.note ?? '';
    }
  }

  static String _defaultStatus(AttendanceSheetRowModel row) => row.approvedLeave
      ? AttendanceStatus.absentExcused
      : AttendanceStatus.present;

  void setStatus(int studentId, String status) {
    if (!canEdit || _statuses[studentId] == status) {
      return;
    }
    _statuses[studentId] = status;
    _saveError = null;
    if (!needsReason) {
      _reasonError = null;
    }
    notifyListeners();
  }

  void setNote(int studentId, String note) {
    if (!canEdit) {
      return;
    }
    _notes[studentId] = note;
    notifyListeners();
  }

  /// Mọi học sinh "Có mặt", trừ học sinh có đơn nghỉ đã duyệt.
  void markAllPresent() {
    final sheet = _sheet;
    if (sheet == null || !canEdit) {
      return;
    }
    for (final row in sheet.rows) {
      _statuses[row.studentId] = _defaultStatus(row);
    }
    _saveError = null;
    notifyListeners();
  }

  /// Bỏ lỗi lý do khi người dùng sửa ô lý do.
  void clearReasonError() {
    if (_reasonError != null) {
      _reasonError = null;
      notifyListeners();
    }
  }

  /// Kiểm tra rồi lưu; thành công thì bảng đổi theo kết quả và trả kết quả,
  /// lỗi thì trả null và giữ các lựa chọn.
  Future<AttendanceSaveResultModel?> save({String? reason}) async {
    final sheet = _sheet;
    if (sheet == null || !canEdit || _saving) {
      return null;
    }
    final trimmed = reason?.trim() ?? '';
    _saveError = null;
    _reasonError = null;
    if (needsReason && trimmed.isEmpty) {
      _reasonError = reasonRequired;
    } else if (trimmed.length > reasonMax) {
      _reasonError = reasonSize;
    }
    final notes = <int, String>{};
    for (final row in sheet.rows) {
      final note = noteOf(row.studentId).trim();
      if (note.length > noteMax) {
        _saveError = '$noteSize (${row.fullName})';
      }
      if (note != (row.note ?? '')) {
        notes[row.studentId] = note;
      }
    }
    if (_reasonError != null || _saveError != null) {
      notifyListeners();
      return null;
    }
    _saving = true;
    notifyListeners();
    try {
      final result = await _teacherService.saveSheet(
        sheet.sectionId,
        sheet.sessionId,
        statuses: {
          for (final row in sheet.rows) row.studentId: statusOf(row.studentId),
        },
        notes: notes,
        reason: trimmed.isEmpty ? null : trimmed,
      );
      _showSheet(result.sheet);
      return result;
    } on ApiErrorModel catch (error) {
      final reasonField = error.fieldErrors['reason'];
      final others = error.fieldErrors.entries
          .where((entry) => entry.key != 'reason')
          .map((entry) => entry.value)
          .toList();
      _reasonError = reasonField;
      _saveError = others.isNotEmpty
          ? others.join('\n')
          : (reasonField == null ? error.message : null);
      return null;
    } finally {
      _saving = false;
      notifyListeners();
    }
  }
}
