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
