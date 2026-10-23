import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/auth_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/password_reset_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/forgot_password_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/auth_widget.dart';

class LoginScreen extends StatefulWidget {
  final AuthController controller;
  final PasswordResetController passwordResetController;

  const LoginScreen({
    super.key,
    required this.controller,
    required this.passwordResetController,
  });

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _phoneCtrl = TextEditingController();
  final _passCtrl = TextEditingController();
  bool _hidePass = true;

  AuthController get _controller => widget.controller;

  @override
  void dispose() {
    _phoneCtrl.dispose();
    _passCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) {
      return;
    }
    await _controller.login(_phoneCtrl.text, _passCtrl.text);
  }

  void _openForgotPassword() {
    _controller.clearErrors();
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) =>
            ForgotPasswordScreen(controller: widget.passwordResetController),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AuthScaffold(
      title: 'Đăng nhập',
      subtitle:
          'Sổ liên lạc điện tử dành cho học sinh, phụ huynh và giáo viên',
      child: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          final errorMessage = _controller.errorMessage;
          return Form(
            key: _formKey,
            child: Column(
              children: [
                if (errorMessage != null)
                  AuthErrorBanner(message: errorMessage),
                AuthTextField(
                  label: 'Số điện thoại',
                  hint: '0901234567',
                  controller: _phoneCtrl,
                  keyboardType: TextInputType.phone,
                  textInputAction: TextInputAction.next,
                  maxLength: 10,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  errorText: _controller.fieldErrors['phone'],
                  onChanged: (_) => _controller.clearErrors(),
                  validator: (value) => (value ?? '').trim().isEmpty
                      ? 'Vui lòng nhập số điện thoại'
                      : null,
                ),
                const SizedBox(height: 20),
                AuthTextField(
                  label: 'Mật khẩu',
                  hint: '••••••••••',
                  controller: _passCtrl,
                  obscure: _hidePass,
                  textInputAction: TextInputAction.done,
                  errorText: _controller.fieldErrors['password'],
                  onChanged: (_) => _controller.clearErrors(),
                  validator: (value) =>
                      (value ?? '').isEmpty ? 'Vui lòng nhập mật khẩu' : null,
                  suffix: PasswordVisibilityButton(
                    hidden: _hidePass,
                    onPressed: () => setState(() => _hidePass = !_hidePass),
                  ),
                ),
                const SizedBox(height: 24),
                AuthSubmitButton(
                  label: 'Đăng nhập',
                  loading: _controller.isLoading,
                  onPressed: _submit,
                ),
                const SizedBox(height: 8),
                TextButton(
                  onPressed: _controller.isLoading ? null : _openForgotPassword,
                  child: const Text('Quên mật khẩu?'),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}
