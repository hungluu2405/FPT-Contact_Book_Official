import 'package:flutter/material.dart';

/// Bảng màu dùng chung, khớp với CSS variables của web (static/css/app.css).
class AppColors {
  AppColors._();

  /// Màu của mọi nút chính.
  static const Color orange = Color(0xFFF97316);

  /// Link, chữ nhỏ màu cam.
  static const Color orangeDark = Color(0xFFC2410C);

  /// Mảng nền, mục đang chọn.
  static const Color orangeLight = Color(0xFFFFF7ED);

  /// Chỉ dùng cho chữ tiêu đề lớn.
  static const Color navy = Color(0xFF0A1930);

  static const Color text = Color(0xFF1F2937);
  static const Color textMuted = Color(0xFF6B7280);
  static const Color hint = Color(0xFF9CA3AF);
  static const Color border = Color(0xFFE5E7EB);
  static const Color background = Colors.white;
  static const Color error = Color(0xFFDC2626);
  static const Color errorLight = Color(0xFFFEF2F2);

  /// Xanh FPT và xanh lá chỉ dùng ở logo và dải ba màu.
  static const Color fptBlue = Color(0xFF0066B3);
  static const Color fptGreen = Color(0xFF00A651);
}
