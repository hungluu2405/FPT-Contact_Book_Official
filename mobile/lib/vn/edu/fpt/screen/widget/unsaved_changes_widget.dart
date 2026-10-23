import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';

/// Hỏi trước khi bỏ nội dung chưa gửi hoặc chưa lưu; true là người dùng
/// đồng ý bỏ. Nút "Ở lại" giữ nguyên nội dung.
Future<bool> confirmDiscardChanges(
  BuildContext context, {
  String title = 'Bỏ nội dung đang soạn?',
  String message = 'Nội dung bạn đã nhập chưa được gửi và sẽ mất.',
  String confirmLabel = 'Bỏ nội dung',
}) async {
  final confirmed = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(title),
      content: Text(message),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          style: TextButton.styleFrom(foregroundColor: AppColors.textMuted),
          child: const Text('Ở lại'),
        ),
        FilledButton(
          onPressed: () => Navigator.of(context).pop(true),
          style: FilledButton.styleFrom(
            backgroundColor: AppColors.error,
            minimumSize: const Size(96, 48),
          ),
          child: Text(confirmLabel),
        ),
      ],
    ),
  );
  return confirmed == true;
}

/// Bọc màn hình có nội dung đang soạn: khi [hasChanges], nút back của hệ
/// thống, nút quay lại ở phần đầu và bấm lại tab đang mở đều hỏi trước khi
/// đóng màn hình (app shell dùng `maybePop`, tôn trọng [PopScope]).
class UnsavedChangesScopeWidget extends StatelessWidget {
  final bool hasChanges;
  final String message;
  final Widget child;

  const UnsavedChangesScopeWidget({
    super.key,
    required this.hasChanges,
    required this.child,
    this.message = 'Nội dung bạn đã nhập chưa được gửi và sẽ mất.',
  });

  @override
  Widget build(BuildContext context) {
    return PopScope<Object?>(
      canPop: !hasChanges,
      onPopInvokedWithResult: (didPop, result) async {
        if (didPop) {
          return;
        }
        final navigator = Navigator.of(context);
        if (await confirmDiscardChanges(context, message: message)) {
          navigator.pop(result);
        }
      },
      child: child,
    );
  }
}
