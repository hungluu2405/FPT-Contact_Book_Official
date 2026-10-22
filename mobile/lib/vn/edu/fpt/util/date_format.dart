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
