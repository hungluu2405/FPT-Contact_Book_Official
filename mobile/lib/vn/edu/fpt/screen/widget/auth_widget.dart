import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';

const double kCardOverlap = 68;

/// Phần đầu của màn hình đăng nhập, đổi mật khẩu: mảng cam nhạt bo cong,
/// logo, tiêu đề navy (giống trang đăng nhập của web).
class AuthHeader extends StatelessWidget {
  final String title;
  final String subtitle;

  const AuthHeader({super.key, required this.title, required this.subtitle});

  @override
  Widget build(BuildContext context) {
    final topInset = MediaQuery.of(context).padding.top;

    return Container(
      width: double.infinity,
      padding: EdgeInsets.fromLTRB(24, topInset + 24, 24, 36 + kCardOverlap),
      decoration: const BoxDecoration(
        color: AppColors.orangeLight,
        borderRadius: BorderRadius.vertical(bottom: Radius.circular(40)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Image.asset(
            'assets/images/fpt_schools_logo.png',
            height: 56,
            fit: BoxFit.contain,
            alignment: Alignment.centerLeft,
            errorBuilder: (_, _, _) => const Text(
              'FPT Schools',
              style: TextStyle(color: AppColors.navy, fontSize: 20),
            ),
          ),
          const SizedBox(height: 12),
          const ThreeColorStripe(),
          const SizedBox(height: 24),
          Text(
            title,
            style: const TextStyle(
              color: AppColors.navy,
              fontSize: 34,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            subtitle,
            style: const TextStyle(color: AppColors.textMuted, fontSize: 16),
          ),
        ],
      ),
    );
  }
}

/// Dải ba màu cam, xanh, xanh lá của FPT.
class ThreeColorStripe extends StatelessWidget {
  const ThreeColorStripe({super.key});

  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      width: 96,
      height: 4,
      child: Row(
        // ColoredBox không có con co về cao 0 nếu không kéo giãn theo chiều dọc.
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(child: ColoredBox(color: AppColors.orange)),
          Expanded(child: ColoredBox(color: AppColors.fptBlue)),
          Expanded(child: ColoredBox(color: AppColors.fptGreen)),
        ],
      ),
    );
  }
}

/// Thẻ trắng chứa form, kéo lên chồng lên phần đáy của [AuthHeader].
class AuthCard extends StatelessWidget {
  final Widget child;

  const AuthCard({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    return Transform.translate(
      offset: const Offset(0, -kCardOverlap),
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 24),
        padding: const EdgeInsets.fromLTRB(20, 24, 20, 24),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(8),
          boxShadow: const [
            BoxShadow(
              color: Color(0x1F000000),
              blurRadius: 24,
              offset: Offset(0, 10),
            ),
          ],
        ),
        child: child,
      ),
    );
  }
}

/// Khung chung của màn hình xác thực: nền trắng, chữ thanh trạng thái tối.
class AuthScaffold extends StatelessWidget {
  final String title;
  final String subtitle;
  final Widget child;

  const AuthScaffold({
    super.key,
    required this.title,
    required this.subtitle,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.dark.copyWith(
        statusBarColor: Colors.transparent,
      ),
      child: Scaffold(
        body: SingleChildScrollView(
          child: Column(
            children: [
              AuthHeader(title: title, subtitle: subtitle),
              AuthCard(child: child),
            ],
          ),
        ),
      ),
    );
  }
}

/// Hộp báo lỗi chung phía trên nút gửi.
class AuthErrorBanner extends StatelessWidget {
  final String message;

  const AuthErrorBanner({super.key, required this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.errorLight,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.error.withValues(alpha: 0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.error_outline, color: AppColors.error),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              style: const TextStyle(color: AppColors.error, fontSize: 15),
            ),
          ),
        ],
      ),
    );
  }
}

/// Hộp thông tin (nền cam nhạt) phía trên form, ví dụ đã gửi mã xác nhận.
class AuthInfoBanner extends StatelessWidget {
  final String message;

  const AuthInfoBanner({super.key, required this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.orangeLight,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.orange.withValues(alpha: 0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(
            Icons.mark_email_read_outlined,
            color: AppColors.orangeDark,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              style: const TextStyle(color: AppColors.text, fontSize: 15),
            ),
          ),
        ],
      ),
    );
  }
}

/// Nút chính, hiện vòng xoay và khóa nút khi đang gửi.
class AuthSubmitButton extends StatelessWidget {
  final String label;
  final bool loading;
  final VoidCallback onPressed;

  const AuthSubmitButton({
    super.key,
    required this.label,
    required this.loading,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: FilledButton(
        onPressed: loading ? null : onPressed,
        child: loading
            ? const SizedBox(
                width: 24,
                height: 24,
                child: CircularProgressIndicator(
                  strokeWidth: 2.5,
                  color: Colors.white,
                ),
              )
            : Text(label),
      ),
    );
  }
}

/// Ô nhập liệu: nhãn phía trên, nền trắng, viền xám nhạt, bo góc 8.
class AuthTextField extends StatelessWidget {
  final String label;
  final String hint;
  final TextEditingController controller;
  final bool obscure;
  final Widget? suffix;
  final TextInputType? keyboardType;
  final String? Function(String?)? validator;
  final int? maxLength;
  final List<TextInputFormatter>? inputFormatters;
  final String? errorText;
  final ValueChanged<String>? onChanged;
  final TextInputAction? textInputAction;

  const AuthTextField({
    super.key,
    required this.label,
    required this.hint,
    required this.controller,
    this.obscure = false,
    this.suffix,
    this.keyboardType,
    this.validator,
    this.maxLength,
    this.inputFormatters,
    this.errorText,
    this.onChanged,
    this.textInputAction,
  });

  OutlineInputBorder _border(Color color, [double width = 1]) =>
      OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: BorderSide(color: color, width: width),
      );

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.w500,
            color: AppColors.text,
          ),
        ),
        const SizedBox(height: 8),
        TextFormField(
          controller: controller,
          obscureText: obscure,
          keyboardType: keyboardType,
          validator: validator,
          maxLength: maxLength,
          inputFormatters: inputFormatters,
          onChanged: onChanged,
          textInputAction: textInputAction,
          style: const TextStyle(fontSize: 16, color: AppColors.text),
          decoration: InputDecoration(
            counterText: '',
            hintText: hint,
            hintStyle: const TextStyle(color: AppColors.hint),
            errorText: errorText,
            errorMaxLines: 3,
            filled: true,
            fillColor: Colors.white,
            suffixIcon: suffix,
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 16,
              vertical: 16,
            ),
            enabledBorder: _border(AppColors.border),
            focusedBorder: _border(AppColors.orange, 1.5),
            errorBorder: _border(AppColors.error),
            focusedErrorBorder: _border(AppColors.error, 1.5),
          ),
        ),
      ],
    );
  }
}

/// Nút ẩn/hiện mật khẩu đặt cuối ô nhập.
class PasswordVisibilityButton extends StatelessWidget {
  final bool hidden;
  final VoidCallback onPressed;

  const PasswordVisibilityButton({
    super.key,
    required this.hidden,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: hidden ? 'Hiện mật khẩu' : 'Ẩn mật khẩu',
      icon: Icon(
        hidden ? Icons.visibility_off_outlined : Icons.visibility_outlined,
        color: AppColors.text,
      ),
      onPressed: onPressed,
    );
  }
}
