import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/auth_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/auth_widget.dart';

/// Đổi mật khẩu. [forced] = true: bắt buộc đổi trước khi vào app
/// (`mustChangePassword`), là màn hình gốc nên chỉ có đổi mật khẩu hoặc đăng
/// xuất. [forced] = false: mở từ tab Tài khoản, đổi xong thì đóng và trả `true`.
/// Quy tắc mật khẩu do backend kiểm tra, lỗi hiện cạnh từng ô.
class ChangePasswordScreen extends StatefulWidget {
  final AuthController controller;
  final bool forced;

  const ChangePasswordScreen({
    super.key,
    required this.controller,
    this.forced = true,
  });

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
  void initState() {
    super.initState();
    if (!widget.forced) {
      // Bỏ lỗi còn lại của lần đổi trước; gọi sau khung hình đầu vì
      // clearErrors báo cho các màn hình đang nghe controller.
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => _controller.clearErrors(),
      );
    }
  }

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
    final changed = await _controller.changePassword(
      currentPassword: _currentCtrl.text,
      newPassword: _newCtrl.text,
      confirmPassword: _confirmCtrl.text,
    );
    if (changed && !widget.forced && mounted) {
      Navigator.of(context).pop(true);
    }
  }

  String? Function(String?) _required(String message) =>
      (value) => (value ?? '').isEmpty ? message : null;

  @override
  Widget build(BuildContext context) {
    return AuthScaffold(
      title: 'Đổi mật khẩu',
      subtitle: widget.forced
          ? 'Bạn cần đổi mật khẩu trước khi sử dụng ứng dụng'
          : 'Nhập mật khẩu hiện tại và mật khẩu mới',
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
                if (widget.forced)
                  TextButton(
                    onPressed: _controller.isLoading
                        ? null
                        : _controller.logout,
                    child: const Text('Đăng xuất'),
                  )
                else
                  TextButton(
                    onPressed: _controller.isLoading
                        ? null
                        : () => Navigator.of(context).pop(false),
                    child: const Text('Quay lại'),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }
}
