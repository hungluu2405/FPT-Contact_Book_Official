/// Đổi ngày `yyyy-MM-dd` của API sang `dd/MM/yyyy`. Chuỗi rỗng hoặc sai
/// dạng thì trả nguyên chuỗi để không làm mất dữ liệu trên màn hình.
String formatApiDate(String? value) {
  if (value == null || value.isEmpty) {
    return '';
  }
  final parts = value.split('-');
  if (parts.length != 3) {
    return value;
  }
  return '${parts[2]}/${parts[1]}/${parts[0]}';
}

/// Đọc ngày `yyyy-MM-dd` của API (không có giờ, không có múi giờ).
/// Sai dạng thì trả null.
DateTime? parseApiDate(String? value) {
  final parts = (value ?? '').split('-');
  if (parts.length != 3) {
    return null;
  }
  final year = int.tryParse(parts[0]);
  final month = int.tryParse(parts[1]);
  final day = int.tryParse(parts[2]);
  if (year == null || month == null || day == null) {
    return null;
  }
  return DateTime(year, month, day);
}

/// Ngày theo dạng `yyyy-MM-dd` để gửi lên API.
String toApiDate(DateTime date) =>
    '${date.year.toString().padLeft(4, '0')}-${_two(date.month)}-'
    '${_two(date.day)}';

/// Giờ `HH:mm:ss` hoặc `HH:mm` của API thành `HH:mm`.
String formatApiTime(String? value) {
  if (value == null || value.length < 5) {
    return value ?? '';
  }
  return value.substring(0, 5);
}

/// Số phút từ 00:00 của giờ `HH:mm[:ss]`; sai dạng thì trả null.
int? minutesOfDay(String? value) {
  final parts = (value ?? '').split(':');
  if (parts.length < 2) {
    return null;
  }
  final hour = int.tryParse(parts[0]);
  final minute = int.tryParse(parts[1]);
  return hour == null || minute == null ? null : hour * 60 + minute;
}

const List<String> _weekdayShort = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];

const List<String> _weekdayLong = [
  'Thứ Hai',
  'Thứ Ba',
  'Thứ Tư',
  'Thứ Năm',
  'Thứ Sáu',
  'Thứ Bảy',
  'Chủ Nhật',
];

/// "T2" ... "CN" cho dải tuần.
String weekdayShort(DateTime date) => _weekdayShort[date.weekday - 1];

/// "Thứ Hai" ... "Chủ Nhật".
String weekdayLong(DateTime date) => _weekdayLong[date.weekday - 1];

/// "23 tháng 10".
String dayMonthText(DateTime date) => '${date.day} tháng ${date.month}';

/// "23/10".
String dayMonthShort(DateTime date) => '${_two(date.day)}/${_two(date.month)}';

/// "Th10" dưới số ngày của thẻ lịch thi.
String monthShort(DateTime date) => 'Th${date.month}';

String _two(int value) => value.toString().padLeft(2, '0');
