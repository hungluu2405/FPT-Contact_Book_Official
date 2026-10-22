import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/auth_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/auth_widget.dart';

/// Bắt buộc đổi mật khẩu trước khi vào app (`mustChangePassword`).
/// Đây là màn hình gốc nên không có nút quay lại; chỉ đổi mật khẩu hoặc đăng xuất.
/// Quy tắc mật khẩu do backend kiểm tra, lỗi hiện cạnh từng ô.
class ChangePasswordScreen extends StatefulWidget {
  final AuthController controller;

  const ChangePasswordScreen({super.key, required this.controller});

  @override
  State<ChangePasswordScreen> createState() => _ChangePasswordScreenState();
}

class _ChangePasswordScreenState extends State<ChangePasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _currentCtrl = TextEditingController();
  final _newCtrl = TextEditingController();
  final _confirmCtrl = TextEditingController();
  bool _hideCurrent = true;
  bool _hideNew = true;
  bool _hideConfirm = true;

  AuthController get _controller => widget.controller;

  @override
  void dispose() {
    _currentCtrl.dispose();
    _newCtrl.dispose();
    _confirmCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) {
      return;
    }
    await _controller.changePassword(
      currentPassword: _currentCtrl.text,
      newPassword: _newCtrl.text,
      confirmPassword: _confirmCtrl.text,
    );
  }

  String? Function(String?) _required(String message) =>
      (value) => (value ?? '').isEmpty ? message : null;

  @override
  Widget build(BuildContext context) {
    return AuthScaffold(
      title: 'Đổi mật khẩu',
      subtitle: 'Bạn cần đổi mật khẩu trước khi sử dụng ứng dụng',
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
                  AuthErrorBanner(message: errorMessage),
                AuthTextField(
                  label: 'Mật khẩu hiện tại',
                  hint: '••••••••••',
                  controller: _currentCtrl,
                  obscure: _hideCurrent,
                  textInputAction: TextInputAction.next,
                  errorText: fieldErrors['currentPassword'],
                  onChanged: (_) => _controller.clearErrors(),
                  validator: _required('Vui lòng nhập mật khẩu hiện tại'),
                  suffix: PasswordVisibilityButton(
                    hidden: _hideCurrent,
                    onPressed: () =>
                        setState(() => _hideCurrent = !_hideCurrent),
                  ),
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
                  label: 'Đổi mật khẩu',
                  loading: _controller.isLoading,
                  onPressed: _submit,
                ),
                const SizedBox(height: 8),
                TextButton(
                  onPressed: _controller.isLoading ? null : _controller.logout,
                  child: const Text('Đăng xuất'),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}
