import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/password_reset_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/reset_password_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/auth_widget.dart';

/// Quên mật khẩu, bước 1: nhập số điện thoại để nhận mã qua email.
class ForgotPasswordScreen extends StatefulWidget {
  final PasswordResetController controller;

  const ForgotPasswordScreen({super.key, required this.controller});

  @override
  State<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends State<ForgotPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _phoneCtrl = TextEditingController();

  PasswordResetController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.clearErrors();
  }

  @override
  void dispose() {
    _phoneCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final sent = await _controller.requestCode(_phoneCtrl.text);
    if (sent && mounted) {
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => ResetPasswordScreen(controller: _controller),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return AuthScaffold(
      title: 'Quên mật khẩu',
      subtitle:
          'Nhập số điện thoại đăng nhập, mã xác nhận sẽ được gửi tới email '
          'của tài khoản',
      child: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          final errorMessage = _controller.errorMessage;
          final phoneError = _controller.fieldErrors['phone'];
          return Form(
            key: _formKey,
            child: Column(
              children: [
                if (errorMessage != null && phoneError == null)
                  AuthErrorBanner(message: errorMessage),
                AuthTextField(
                  label: 'Số điện thoại',
                  hint: '0901234567',
                  controller: _phoneCtrl,
                  keyboardType: TextInputType.phone,
                  textInputAction: TextInputAction.done,
                  maxLength: 10,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  errorText: phoneError,
                  onChanged: (_) => _controller.clearErrors(),
                  validator: (value) => (value ?? '').trim().isEmpty
                      ? 'Vui lòng nhập số điện thoại'
                      : null,
                ),
                const SizedBox(height: 24),
                AuthSubmitButton(
                  label: 'Gửi mã xác nhận',
                  loading: _controller.isLoading,
                  onPressed: _submit,
                ),
                const SizedBox(height: 8),
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('Quay lại đăng nhập'),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}
