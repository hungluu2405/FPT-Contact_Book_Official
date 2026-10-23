/// Số thập phân kiểu Việt Nam: dấu phẩy, bỏ số 0 thừa ở cuối, tối đa
/// [maxDecimals] chữ số sau dấu phẩy. Ví dụ 8.5 → "8,5", 10.0 → "10".
String formatDecimal(double value, {int maxDecimals = 2}) {
  var text = value.toStringAsFixed(maxDecimals);
  if (text.contains('.')) {
    text = text.replaceFirst(RegExp(r'\.?0+$'), '');
  }
  return text.replaceAll('.', ',');
}

/// Phần trăm một chữ số thập phân như web: 92.25 → "92,3%", 100 → "100%".
String formatPercent(double value) =>
    '${formatDecimal(value, maxDecimals: 1)}%';

/// Dung lượng tệp: dưới 1 MB làm tròn lên KB như web ("240 KB"), từ 1 MB
/// trở lên một chữ số thập phân ("1,5 MB").
String formatFileSize(int bytes) {
  const mb = 1024 * 1024;
  if (bytes < mb) {
    return '${(bytes + 1023) ~/ 1024} KB';
  }
  return '${formatDecimal(bytes / mb, maxDecimals: 1)} MB';
}
