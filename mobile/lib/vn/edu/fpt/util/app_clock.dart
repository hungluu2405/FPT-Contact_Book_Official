/// Giờ hiện tại theo Asia/Ho_Chi_Minh (UTC+7, không có giờ mùa hè), trả về
/// dạng giờ địa phương không kèm múi giờ để so với ngày giờ của API.
/// Controller nhận hàm này qua constructor để test dùng đồng hồ giả.
typedef AppClock = DateTime Function();

DateTime vietnamNow() {
  final utc = DateTime.now().toUtc().add(const Duration(hours: 7));
  return DateTime(
    utc.year,
    utc.month,
    utc.day,
    utc.hour,
    utc.minute,
    utc.second,
  );
}
