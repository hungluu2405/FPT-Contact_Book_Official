import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/request_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/leave_option_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/request/view_request_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/attachment_picker_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/auth_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_theme.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/leave_rule.dart';

/// Form gửi đơn xin nghỉ học cho người đang xem (HS hoặc con của PH): khoảng
/// ngày chọn trên lịch, lớp môn học (tùy chọn), tiêu đề (tùy chọn), lý do và
/// tối đa 5 tệp minh chứng (chụp ảnh hoặc chọn tệp). Lỗi hiện cạnh từng ô;
/// gửi thành công thì mở chi tiết đơn vừa gửi.
class AddLeaveScreen extends StatefulWidget {
  final RequestController controller;
  final String viewerName;

  const AddLeaveScreen({
    super.key,
    required this.controller,
    required this.viewerName,
  });

  @override
  State<AddLeaveScreen> createState() => _AddLeaveScreenState();
}

class _AddLeaveScreenState extends State<AddLeaveScreen> {
  final _titleCtrl = TextEditingController();
  final _reasonCtrl = TextEditingController();
  final _scrollCtrl = ScrollController();
  DateTime? _fromDate;
  DateTime? _toDate;
  int? _sectionId;

  RequestController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    // Màn hình danh sách bên dưới cũng nghe controller này.
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => _controller.startLeave(),
    );
  }

  @override
  void dispose() {
    _titleCtrl.dispose();
    _reasonCtrl.dispose();
    _scrollCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickDate({required bool from}) async {
    final today = _controller.today;
    final current = from ? _fromDate : (_toDate ?? _fromDate);
    final first = DateTime(
      today.year,
      today.month,
      today.day - LeaveRule.maxPastDays,
    );
    final last = DateTime(today.year + 1, today.month, today.day);
    var initial = current ?? DateTime(today.year, today.month, today.day);
    if (initial.isBefore(first)) {
      initial = first;
    }
    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: first,
      lastDate: last,
      currentDate: today,
      initialEntryMode: DatePickerEntryMode.calendarOnly,
      helpText: from ? 'Nghỉ từ ngày' : 'Nghỉ đến ngày',
    );
    if (picked == null) {
      return;
    }
    setState(() {
      if (from) {
        _fromDate = picked;
        // Thường nghỉ một ngày: điền sẵn đến ngày nếu còn trống hoặc trước.
        if (_toDate == null || _toDate!.isBefore(picked)) {
          _toDate = picked;
        }
      } else {
        _toDate = picked;
      }
    });
    _controller
      ..clearFieldError('fromDate')
      ..clearFieldError('toDate');
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
    final created = await _controller.submitLeave(
      LeaveFormModel(
        fromDate: _fromDate,
        toDate: _toDate,
        sectionId: _sectionId,
        title: _titleCtrl.text,
        reason: _reasonCtrl.text,
      ),
    );
    if (!mounted) {
      return;
    }
    if (created == null) {
      // Lỗi ngày và hộp lỗi chung nằm đầu form, nút gửi nằm cuối.
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
        .showSnackBar(const SnackBar(content: Text('Đã gửi đơn nghỉ.')));
    Navigator.of(context).pushReplacement(
      MaterialPageRoute<void>(
        builder: (_) => ViewRequestScreen(
          controller: _controller,
          requestId: created.id,
          viewerName: widget.viewerName,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          final options = _controller.options;
          return ListView(
            controller: _scrollCtrl,
            padding: EdgeInsets.zero,
            children: [
              PageHeaderWidget(
                icon: Icons.edit_note,
                title: 'Đơn từ',
                headline: 'Gửi đơn nghỉ',
                subtitle: options == null
                    ? null
                    : 'Học sinh: ${options.studentName} '
                          '(${options.studentCode})',
                viewerName: widget.viewerName,
              ),
              ..._body(options),
            ],
          );
        },
      ),
    );
  }

  List<Widget> _body(LeaveOptionsModel? options) {
    final error = _controller.optionsError;
    if (error != null) {
      return [
        ErrorRetryWidget(
          message: error.message,
          onRetry: _controller.startLeave,
        ),
      ];
    }
    if (options == null) {
      return const [
        Padding(
          padding: EdgeInsets.all(24),
          child: Center(
            child: CircularProgressIndicator(color: AppColors.orange),
          ),
        ),
      ];
    }
    final errors = _controller.fieldErrors;
    final formError = _controller.formError;
    return [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 20, 16, 32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (formError != null) AuthErrorBanner(message: formError),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: _DateField(
                    label: 'Từ ngày',
                    value: _fromDate,
                    errorText: errors['fromDate'],
                    onTap: () => _pickDate(from: true),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _DateField(
                    label: 'Đến ngày',
                    value: _toDate,
                    errorText: errors['toDate'],
                    onTap: () => _pickDate(from: false),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            _SectionField(
              sections: options.sections,
              value: _sectionId,
              errorText: errors['sectionId'],
              onChanged: (value) {
                setState(() => _sectionId = value);
                _controller.clearFieldError('sectionId');
              },
            ),
            const SizedBox(height: 20),
            AuthTextField(
              label: 'Tiêu đề (không bắt buộc)',
              hint: 'Để trống sẽ tự điền "Đơn xin nghỉ học từ ... đến ..."',
              controller: _titleCtrl,
              maxLength: LeaveRule.titleMax,
              errorText: errors['title'],
              textInputAction: TextInputAction.next,
              onChanged: (_) => _controller.clearFieldError('title'),
            ),
            const SizedBox(height: 20),
            AuthTextField(
              label: 'Lý do',
              hint: 'Ví dụ: Em bị sốt, có giấy khám bệnh kèm theo.',
              controller: _reasonCtrl,
              maxLength: LeaveRule.reasonMax,
              minLines: 4,
              maxLines: 8,
              keyboardType: TextInputType.multiline,
              errorText: errors['reason'],
              onChanged: (_) => _controller.clearFieldError('reason'),
            ),
            const SizedBox(height: 20),
            AttachmentPickerWidget(
              label: 'Tệp minh chứng',
              files: _controller.files,
              onRemove: _controller.removeFile,
              onTakePhoto: () => _addFiles(_controller.takePhoto),
              onPickFiles: () => _addFiles(_controller.pickFiles),
            ),
            const SizedBox(height: 28),
            AuthSubmitButton(
              label: 'Gửi đơn',
              loading: _controller.isSubmitting,
              onPressed: _submit,
            ),
          ],
        ),
      ),
    ];
  }
}

/// Nhãn phía trên ô, giống [AuthTextField].
class _FieldLabel extends StatelessWidget {
  final String text;

  const _FieldLabel(this.text);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(
        text,
        style: const TextStyle(
          fontSize: 15,
          fontWeight: FontWeight.w500,
          color: AppColors.text,
        ),
      ),
    );
  }
}

InputDecoration _decoration({String? errorText, String? helperText}) {
  OutlineInputBorder border(Color color) => OutlineInputBorder(
    borderRadius: BorderRadius.circular(8),
    borderSide: BorderSide(color: color),
  );
  return InputDecoration(
    errorText: errorText,
    errorMaxLines: 3,
    helperText: helperText,
    helperMaxLines: 3,
    filled: true,
    fillColor: Colors.white,
    contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 16),
    enabledBorder: border(AppColors.border),
    border: border(AppColors.border),
    focusedBorder: border(AppColors.orange),
    errorBorder: border(AppColors.error),
    focusedErrorBorder: border(AppColors.error),
  );
}

/// Ô ngày: bấm để mở lịch, hiện `dd/MM/yyyy`.
class _DateField extends StatelessWidget {
  final String label;
  final DateTime? value;
  final String? errorText;
  final VoidCallback onTap;

  const _DateField({
    required this.label,
    required this.value,
    required this.errorText,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final date = value;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _FieldLabel(label),
        Semantics(
          button: true,
          label: label,
          child: InkWell(
            onTap: onTap,
            borderRadius: BorderRadius.circular(8),
            child: InputDecorator(
              decoration: _decoration(errorText: errorText).copyWith(
                suffixIcon: const Icon(
                  Icons.calendar_month,
                  color: AppColors.orange,
                ),
              ),
              child: Text(
                date == null ? 'Chọn ngày' : formatApiDate(toApiDate(date)),
                style: TextStyle(
                  fontSize: 16,
                  color: date == null ? AppColors.hint : AppColors.text,
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// Ô chọn lớp môn học; "Tất cả các lớp" là nghỉ mọi lớp trong khoảng ngày.
class _SectionField extends StatelessWidget {
  final List<SectionOptionModel> sections;
  final int? value;
  final String? errorText;
  final ValueChanged<int?> onChanged;

  const _SectionField({
    required this.sections,
    required this.value,
    required this.errorText,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _FieldLabel('Lớp môn học'),
        DropdownButtonFormField<int?>(
          initialValue: value,
          isExpanded: true,
          onChanged: onChanged,
          style: const TextStyle(
            fontFamily: kAppFontFamily,
            fontSize: 16,
            color: AppColors.text,
          ),
          decoration: _decoration(
            errorText: errorText,
            helperText:
                'Chỉ chọn khi nghỉ riêng một lớp; để trống là nghỉ mọi lớp '
                'trong khoảng ngày.',
          ),
          items: [
            const DropdownMenuItem<int?>(child: Text('Tất cả các lớp')),
            for (final section in sections)
              DropdownMenuItem<int?>(
                value: section.id,
                child: Text(section.name, overflow: TextOverflow.ellipsis),
              ),
          ],
        ),
      ],
    );
  }
}
