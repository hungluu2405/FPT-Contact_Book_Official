import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/leave_option_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/picked_file_model.dart';

/// Quy tắc của đơn nghỉ, giống RequestRule, RequestService và
/// AttachmentStorage ở backend; thông báo trùng từng chữ để lỗi kiểm tra
/// trước và lỗi backend trả về hiện như nhau. Backend vẫn kiểm tra lại.
class LeaveRule {
  LeaveRule._();

  static const int maxPastDays = 14;
  static const int maxSpanDays = 30;
  static const int titleMax = 200;
  static const int reasonMax = 1000;
  static const int maxFiles = 5;
  static const int maxFileBytes = 5 * 1024 * 1024;

  /// Đuôi tệp được nhận và kiểu nội dung gửi kèm (backend so cả hai).
  static const Map<String, String> contentTypes = {
    'pdf': 'application/pdf',
    'jpg': 'image/jpeg',
    'jpeg': 'image/jpeg',
    'png': 'image/png',
    'docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  };

  static const String fromRequired = 'Vui lòng chọn từ ngày';
  static const String toRequired = 'Vui lòng chọn đến ngày';
  static const String toBeforeFrom = 'Đến ngày phải bằng hoặc sau từ ngày';
  static const String fromTooOld =
      'Từ ngày không được sớm hơn $maxPastDays ngày trước hôm nay';
  static const String spanTooLong = 'Mỗi đơn nghỉ tối đa $maxSpanDays ngày';
  static const String titleSize = 'Tiêu đề tối đa $titleMax ký tự';
  static const String reasonRequired = 'Vui lòng nhập lý do';
  static const String reasonSize = 'Lý do tối đa $reasonMax ký tự';
  static const String tooManyFiles = 'Tối đa $maxFiles tệp đính kèm';

  /// Lỗi theo ô (`fromDate`, `toDate`, `title`, `reason`) như tên trường
  /// của API; rỗng là hợp lệ. [today] là hôm nay giờ Việt Nam.
  static Map<String, String> validate(LeaveFormModel form, DateTime today) {
    final errors = <String, String>{};
    final from = _dateOnly(form.fromDate);
    final to = _dateOnly(form.toDate);
    final day = _dateOnly(today)!;
    if (from == null) {
      errors['fromDate'] = fromRequired;
    }
    if (to == null) {
      errors['toDate'] = toRequired;
    }
    if (from != null && to != null) {
      if (from.isBefore(day.subtract(const Duration(days: maxPastDays)))) {
        errors['fromDate'] = fromTooOld;
      }
      if (to.isBefore(from)) {
        errors['toDate'] = toBeforeFrom;
      } else if (_daysBetween(from, to) + 1 > maxSpanDays) {
        errors['toDate'] = spanTooLong;
      }
    }
    if (form.title.trim().length > titleMax) {
      errors['title'] = titleSize;
    }
    final reason = form.reason.trim();
    if (reason.isEmpty) {
      errors['reason'] = reasonRequired;
    } else if (reason.length > reasonMax) {
      errors['reason'] = reasonSize;
    }
    return errors;
  }

  /// Lỗi của một tệp vừa chọn; null là nhận được.
  static String? fileError(PickedFileModel file) {
    if (!contentTypes.containsKey(file.extension)) {
      return 'Tệp "${file.fileName}" không hợp lệ: '
          'chỉ nhận PDF, JPG, PNG, DOCX, XLSX';
    }
    if (file.sizeBytes == 0) {
      return 'Tệp "${file.fileName}" rỗng';
    }
    if (file.sizeBytes > maxFileBytes) {
      return 'Tệp "${file.fileName}" vượt quá 5 MB';
    }
    return null;
  }

  static DateTime? _dateOnly(DateTime? value) =>
      value == null ? null : DateTime(value.year, value.month, value.day);

  /// Số ngày lịch giữa hai ngày; dùng UTC để không lệch vì giờ mùa hè.
  static int _daysBetween(DateTime from, DateTime to) => DateTime.utc(
    to.year,
    to.month,
    to.day,
  ).difference(DateTime.utc(from.year, from.month, from.day)).inDays;
}
