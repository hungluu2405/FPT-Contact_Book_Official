import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/department_notification_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/sent_notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/department/view_sent_notification_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/attachment_picker_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/auth_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/unsaved_changes_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_theme.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/user_labels.dart';

/// Form thông báo nội bộ của tổ trưởng: tiêu đề, nội dung, mức độ, tệp đính
/// kèm. Người nhận (cả tổ hoặc một giáo viên) đã chọn ở nơi mở màn hình qua
/// [DepartmentNotificationController.startCompose]. Gửi có hộp xác nhận; gửi
/// xong mở thống kê đã đọc của thông báo vừa gửi.
class AddDepartmentNotificationScreen extends StatefulWidget {
  final DepartmentNotificationController controller;

  const AddDepartmentNotificationScreen({super.key, required this.controller});

  @override
  State<AddDepartmentNotificationScreen> createState() =>
      _AddDepartmentNotificationScreenState();
}

class _AddDepartmentNotificationScreenState
    extends State<AddDepartmentNotificationScreen> {
  final _titleCtrl = TextEditingController();
  final _contentCtrl = TextEditingController();
  final _scrollCtrl = ScrollController();
  String _priority = NotificationPriority.normal;

  DepartmentNotificationController get _controller => widget.controller;

  @override
  void dispose() {
    _titleCtrl.dispose();
    _contentCtrl.dispose();
    _scrollCtrl.dispose();
    super.dispose();
  }

  Future<void> _addFiles(Future<List<String>> Function() pick) async {
    final messenger = ScaffoldMessenger.of(context);
    final rejected = await pick();
    if (rejected.isNotEmpty) {
      messenger.showSnackBar(SnackBar(content: Text(rejected.join('\n'))));
    }
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    final form = DepartmentNotificationFormModel(
      title: _titleCtrl.text,
      content: _contentCtrl.text,
      priority: _priority,
    );
    // Thiếu tiêu đề, nội dung thì báo lỗi ngay, không cần hỏi xác nhận.
    if (form.title.trim().isNotEmpty && form.content.trim().isNotEmpty) {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Gửi thông báo'),
          content: Text('Gửi thông báo này tới ${_target()}?'),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              style: TextButton.styleFrom(foregroundColor: AppColors.textMuted),
              child: const Text('Hủy'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(context).pop(true),
              style: FilledButton.styleFrom(minimumSize: const Size(96, 48)),
              child: const Text('Gửi'),
            ),
          ],
        ),
      );
      if (confirmed != true || !mounted) {
        return;
      }
    }
    final sent = await _controller.send(form);
    if (!mounted) {
      return;
    }
    if (sent == null) {
      // Lỗi nằm ở đầu form, nút gửi nằm cuối.
      if (_scrollCtrl.hasClients) {
        await _scrollCtrl.animateTo(
          0,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
      return;
    }
    ScaffoldMessenger.of(context)
        .showSnackBar(const SnackBar(content: Text('Đã gửi thông báo.')));
    Navigator.of(context).pushReplacement(
      MaterialPageRoute<void>(
        builder: (_) => ViewSentNotificationScreen(
          controller: _controller,
          notificationId: sent.id,
        ),
      ),
    );
  }

  /// "cả Tổ Toán – Tin học" hoặc "riêng thầy/cô ..." trong câu xác nhận.
  String _target() {
    final label = _controller.targetLabel;
    return label.isEmpty ? '' : label[0].toLowerCase() + label.substring(1);
  }

  /// Đã gõ tiêu đề, nội dung hoặc đính tệp: rời trang phải hỏi. Chỉ đổi mức
  /// độ thì không tính.
  bool get _hasContent =>
      _titleCtrl.text.trim().isNotEmpty ||
      _contentCtrl.text.trim().isNotEmpty ||
      _controller.files.isNotEmpty;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([_titleCtrl, _contentCtrl, _controller]),
      builder: (context, child) => UnsavedChangesScopeWidget(
        hasChanges: _hasContent,
        message: 'Thông báo chưa được gửi. Nội dung đã nhập sẽ mất.',
        child: child!,
      ),
      child: _form(),
    );
  }

  Widget _form() {
    return Scaffold(
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          final errors = _controller.fieldErrors;
          final formError = _controller.formError;
          return ListView(
            controller: _scrollCtrl,
            padding: EdgeInsets.zero,
            children: [
              PageHeaderWidget(
                icon: Icons.campaign_outlined,
                title: 'Tổ chuyên môn',
                headline: _controller.isToTeacher
                    ? 'Gửi riêng giáo viên'
                    : 'Gửi thông báo tổ',
                subtitle: 'Gửi tới: ${_target()}',
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 20, 16, 32),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (formError != null) AuthErrorBanner(message: formError),
                    AuthTextField(
                      label: 'Tiêu đề',
                      hint: 'Ví dụ: Họp tổ chuyên môn tháng 11',
                      controller: _titleCtrl,
                      maxLength: DepartmentNotificationController.titleMax,
                      errorText: errors['title'],
                      textInputAction: TextInputAction.next,
                      onChanged: (_) => _controller.clearFieldError('title'),
                    ),
                    const SizedBox(height: 20),
                    AuthTextField(
                      label: 'Nội dung',
                      hint: 'Nội dung gửi các thầy cô',
                      controller: _contentCtrl,
                      minLines: 5,
                      maxLines: 12,
                      keyboardType: TextInputType.multiline,
                      errorText: errors['content'],
                      onChanged: (_) => _controller.clearFieldError('content'),
                    ),
                    const SizedBox(height: 20),
                    const Text(
                      'Mức độ',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w500,
                        color: AppColors.text,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      children: [
                        for (final priority in const [
                          NotificationPriority.normal,
                          NotificationPriority.important,
                          NotificationPriority.urgent,
                        ])
                          ChoiceChip(
                            label: Text(
                              kNotificationPriorityLabels[priority] ?? priority,
                            ),
                            selected: _priority == priority,
                            onSelected: (_) =>
                                setState(() => _priority = priority),
                            selectedColor:
                                priority == NotificationPriority.urgent
                                ? AppColors.error
                                : AppColors.orange,
                            backgroundColor: Colors.white,
                            showCheckmark: false,
                            labelStyle: TextStyle(
                              fontFamily: kAppFontFamily,
                              fontSize: 15,
                              fontWeight: FontWeight.w600,
                              color: _priority == priority
                                  ? Colors.white
                                  : AppColors.text,
                            ),
                          ),
                      ],
                    ),
                    if (errors['priority'] != null) ...[
                      const SizedBox(height: 6),
                      Text(
                        errors['priority']!,
                        style: const TextStyle(
                          fontSize: 13,
                          color: AppColors.error,
                        ),
                      ),
                    ],
                    const SizedBox(height: 20),
                    AttachmentPickerWidget(
                      label: 'Tệp đính kèm',
                      files: _controller.files,
                      onRemove: _controller.removeFile,
                      onTakePhoto: () => _addFiles(_controller.takePhoto),
                      onPickFiles: () => _addFiles(_controller.pickFiles),
                    ),
                    const SizedBox(height: 28),
                    AuthSubmitButton(
                      label: 'Gửi thông báo',
                      loading: _controller.isSending,
                      onPressed: _submit,
                    ),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
