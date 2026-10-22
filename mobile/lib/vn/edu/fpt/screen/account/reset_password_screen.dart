import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/password_reset_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/auth_widget.dart';

/// Quên mật khẩu, bước 2: nhập mã nhận qua email và mật khẩu mới. Thành công
/// thì quay về màn hình đăng nhập kèm thông báo. Quy tắc mật khẩu do backend
/// kiểm tra, lỗi hiện cạnh từng ô.
class ResetPasswordScreen extends StatefulWidget {
  final PasswordResetController controller;

  const ResetPasswordScreen({super.key, required this.controller});

  @override
  State<ResetPasswordScreen> createState() => _ResetPasswordScreenState();
}

class _ResetPasswordScreenState extends State<ResetPasswordScreen> {
  static const String sentMessage =
      'Nếu tài khoản có email, mã xác nhận đã được gửi. Mã có hiệu lực '
      'trong khoảng 10 phút. Tài khoản chưa có email vui lòng liên hệ quản '
      'trị viên nhà trường.';
  static const String successMessage =
      'Đặt lại mật khẩu thành công, vui lòng đăng nhập bằng mật khẩu mới';

  final _formKey = GlobalKey<FormState>();
  final _codeCtrl = TextEditingController();
  final _newCtrl = TextEditingController();
  final _confirmCtrl = TextEditingController();
  bool _hideNew = true;
  bool _hideConfirm = true;

  PasswordResetController get _controller => widget.controller;

  @override
  void dispose() {
    _codeCtrl.dispose();
    _newCtrl.dispose();
    _confirmCtrl.dispose();
    super.dispose();
  }

  String? Function(String?) _required(String message) =>
      (value) => (value ?? '').trim().isEmpty ? message : null;

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final done = await _controller.resetPassword(
      code: _codeCtrl.text,
      newPassword: _newCtrl.text,
      confirmPassword: _confirmCtrl.text,
    );
    if (done && mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text(successMessage)));
      Navigator.of(context).popUntil((route) => route.isFirst);
    }
  }

  Future<void> _resend() async {
    final sent = await _controller.requestCode(_controller.phone);
    if (sent && mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Đã gửi lại mã xác nhận')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return AuthScaffold(
      title: 'Đặt lại mật khẩu',
      subtitle: 'Số điện thoại ${_controller.phone}',
      child: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          final errorMessage = _controller.errorMessage;
          final fieldErrors = _controller.fieldErrors;
          return Form(
            key: _formKey,
            child: Column(
              children: [
                if (errorMessage != null)
                  AuthErrorBanner(message: errorMessage)
                else
                  const AuthInfoBanner(message: sentMessage),
                AuthTextField(
                  label: 'Mã xác nhận (6 chữ số)',
                  hint: '123456',
                  controller: _codeCtrl,
                  keyboardType: TextInputType.number,
                  textInputAction: TextInputAction.next,
                  maxLength: 6,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  errorText: fieldErrors['code'],
                  onChanged: (_) => _controller.clearErrors(),
                  validator: _required('Vui lòng nhập mã xác nhận'),
                ),
                const SizedBox(height: 20),
                AuthTextField(
                  label: 'Mật khẩu mới',
                  hint: '••••••••••',
                  controller: _newCtrl,
                  obscure: _hideNew,
                  textInputAction: TextInputAction.next,
                  errorText: fieldErrors['newPassword'],
                  onChanged: (_) => _controller.clearErrors(),
                  validator: _required('Vui lòng nhập mật khẩu mới'),
                  suffix: PasswordVisibilityButton(
                    hidden: _hideNew,
                    onPressed: () => setState(() => _hideNew = !_hideNew),
                  ),
                ),
                const SizedBox(height: 20),
                AuthTextField(
                  label: 'Nhập lại mật khẩu mới',
                  hint: '••••••••••',
                  controller: _confirmCtrl,
                  obscure: _hideConfirm,
                  textInputAction: TextInputAction.done,
                  errorText: fieldErrors['confirmPassword'],
                  onChanged: (_) => _controller.clearErrors(),
                  validator: _required('Vui lòng nhập lại mật khẩu mới'),
                  suffix: PasswordVisibilityButton(
                    hidden: _hideConfirm,
                    onPressed: () =>
                        setState(() => _hideConfirm = !_hideConfirm),
                  ),
                ),
                const SizedBox(height: 24),
                AuthSubmitButton(
                  label: 'Đặt lại mật khẩu',
                  loading: _controller.isLoading,
                  onPressed: _submit,
                ),
                const SizedBox(height: 8),
                TextButton(
                  onPressed: _controller.isLoading ? null : _resend,
                  child: const Text('Gửi lại mã'),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}
