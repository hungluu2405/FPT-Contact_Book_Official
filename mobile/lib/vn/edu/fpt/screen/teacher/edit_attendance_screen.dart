import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/attendance_sheet_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attendance_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attendance_sheet_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/auth_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_theme.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

/// Nhãn ngắn của ba nút trạng thái trên mỗi hàng.
const Map<String, String> _shortLabels = {
  AttendanceStatus.present: 'Có mặt',
  AttendanceStatus.absentExcused: 'Có phép',
  AttendanceStatus.absentUnexcused: 'Không phép',
};

/// Bảng điểm danh một buổi: mỗi học sinh một hàng với ba nút Có mặt / Có
/// phép / Không phép và ô ghi chú; lý do khi sửa buổi đã điểm danh; hộp xác
/// nhận khi lưu. Quá hạn thì chỉ xem. Nơi mở màn hình đã gọi
/// [AttendanceSheetController.open].
class EditAttendanceScreen extends StatefulWidget {
  final AttendanceSheetController controller;
  final String viewerName;

  const EditAttendanceScreen({
    super.key,
    required this.controller,
    required this.viewerName,
  });

  @override
  State<EditAttendanceScreen> createState() => _EditAttendanceScreenState();
}

class _EditAttendanceScreenState extends State<EditAttendanceScreen> {
  final _reasonCtrl = TextEditingController();

  AttendanceSheetController get _controller => widget.controller;

  @override
  void dispose() {
    _reasonCtrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    FocusScope.of(context).unfocus();
    final counts = _controller.counts;
    final total = _controller.sheet?.rows.length ?? 0;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Lưu điểm danh'),
        content: Text(
          'Lưu điểm danh cho $total học sinh?\n'
          'Có mặt ${counts[AttendanceStatus.present]} · '
          'Có phép ${counts[AttendanceStatus.absentExcused]} · '
          'Không phép ${counts[AttendanceStatus.absentUnexcused]}',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            style: TextButton.styleFrom(foregroundColor: AppColors.textMuted),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: FilledButton.styleFrom(minimumSize: const Size(96, 48)),
            child: const Text('Lưu'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) {
      return;
    }
    final messenger = ScaffoldMessenger.of(context);
    final result = await _controller.save(reason: _reasonCtrl.text);
    if (result == null) {
      return;
    }
    _reasonCtrl.clear();
    messenger.showSnackBar(
      SnackBar(
        content: Text('Đã lưu điểm danh (${result.changed} học sinh).'),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          final sheet = _controller.sheet;
          return RefreshIndicator(
            color: AppColors.orange,
            onRefresh: _controller.reload,
            child: ListView(
              padding: EdgeInsets.zero,
              children: [
                PageHeaderWidget(
                  icon: Icons.fact_check_outlined,
                  title: sheet == null
                      ? 'Điểm danh'
                      : 'Điểm danh · ${sheet.subjectCode}',
                  headline: sheet == null
                      ? 'Điểm danh'
                      : 'Buổi ${sheet.sessionNo} · ${formatApiDate(sheet.date)}',
                  subtitle: sheet == null
                      ? null
                      : [
                          '${formatApiTime(sheet.startTime)}–'
                              '${formatApiTime(sheet.endTime)}',
                          if ((sheet.roomCode ?? '').isNotEmpty)
                            'Phòng ${sheet.roomCode}',
                          sheet.sectionCode,
                        ].join(' · '),
                  viewerName: widget.viewerName,
                ),
                ..._content(sheet),
              ],
            ),
          );
        },
      ),
    );
  }

  List<Widget> _content(AttendanceSheetModel? sheet) {
    final error = _controller.error;
    if (error != null) {
      return [
        ErrorRetryWidget(message: error.message, onRetry: _controller.reload),
      ];
    }
    if (sheet == null) {
      return const [
        Padding(
          padding: EdgeInsets.all(24),
          child: Center(
            child: CircularProgressIndicator(color: AppColors.orange),
          ),
        ),
      ];
    }
    final canEdit = _controller.canEdit;
    final counts = _controller.counts;
    final saveError = _controller.saveError;
    return [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _DeadlineBanner(sheet: sheet),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 6,
              children: [
                StatusBadgeWidget(
                  label: 'Có mặt ${counts[AttendanceStatus.present]}',
                  tone: BadgeTone.success,
                ),
                StatusBadgeWidget(
                  label: 'Có phép ${counts[AttendanceStatus.absentExcused]}',
                ),
                StatusBadgeWidget(
                  label:
                      'Không phép ${counts[AttendanceStatus.absentUnexcused]}',
                  tone: BadgeTone.danger,
                ),
              ],
            ),
            if (canEdit && sheet.rows.isNotEmpty)
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  onPressed: _controller.markAllPresent,
                  icon: const Icon(Icons.done_all),
                  label: const Text('Tất cả có mặt'),
                ),
              ),
            if (sheet.rows.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 16),
                child: Text(
                  'Lớp chưa có học sinh đang học để điểm danh.',
                  style: TextStyle(fontSize: 15, color: AppColors.textMuted),
                ),
              ),
            for (final row in sheet.rows)
              _StudentRow(
                key: ValueKey('${sheet.sessionId}-${row.studentId}-${row.note}'),
                row: row,
                status: _controller.statusOf(row.studentId),
                note: _controller.noteOf(row.studentId),
                enabled: canEdit,
                onStatus: (status) =>
                    _controller.setStatus(row.studentId, status),
                onNote: (note) => _controller.setNote(row.studentId, note),
              ),
            if (canEdit && _controller.needsReason) ...[
              const SizedBox(height: 8),
              AuthTextField(
                label: 'Lý do điều chỉnh',
                hint: 'Ví dụ: Phụ huynh gửi giấy xin phép sau buổi học',
                controller: _reasonCtrl,
                maxLength: AttendanceSheetController.reasonMax,
                errorText: _controller.reasonError,
                onChanged: (_) => _controller.clearReasonError(),
              ),
            ],
            if (saveError != null) ...[
              const SizedBox(height: 16),
              AuthErrorBanner(message: saveError),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  onPressed: _controller.reload,
                  icon: const Icon(Icons.refresh),
                  label: const Text('Tải lại bảng điểm danh'),
                ),
              ),
            ],
            if (canEdit && sheet.rows.isNotEmpty) ...[
              const SizedBox(height: 20),
              AuthSubmitButton(
                label: 'Lưu điểm danh',
                loading: _controller.isSaving,
                onPressed: _save,
              ),
            ],
          ],
        ),
      ),
    ];
  }
}

/// Hạn điểm danh, sửa; bảng quá hạn thì báo chỉ xem.
class _DeadlineBanner extends StatelessWidget {
  final AttendanceSheetModel sheet;

  const _DeadlineBanner({required this.sheet});

  @override
  Widget build(BuildContext context) {
    final deadline = formatApiDateTime(sheet.editableUntil);
    final String text;
    if (sheet.locked) {
      text =
          'Đã hết hạn điểm danh, chỉnh sửa (đến $deadline). Chỉ xem; cần sửa '
          'vui lòng liên hệ quản trị viên.';
    } else if (sheet.marked) {
      text =
          'Buổi này đã điểm danh. Sửa được đến $deadline; đổi trạng thái '
          'cần ghi lý do.';
    } else {
      text =
          'Điểm danh trước $deadline. Mọi học sinh được chọn sẵn "Có mặt", '
          'chỉ cần đổi người vắng.';
    }
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: sheet.locked ? const Color(0xFFF3F4F6) : AppColors.orangeLight,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            sheet.locked ? Icons.lock_outline : Icons.schedule,
            color: sheet.locked ? AppColors.textMuted : AppColors.orange,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(fontSize: 14, color: AppColors.text),
            ),
          ),
        ],
      ),
    );
  }
}

/// Một học sinh: tên, mã, nhãn có đơn nghỉ, ba nút trạng thái, ô ghi chú.
class _StudentRow extends StatelessWidget {
  final AttendanceSheetRowModel row;
  final String status;
  final String note;
  final bool enabled;
  final ValueChanged<String> onStatus;
  final ValueChanged<String> onNote;

  const _StudentRow({
    super.key,
    required this.row,
    required this.status,
    required this.note,
    required this.enabled,
    required this.onStatus,
    required this.onNote,
  });

  static Color _colorOf(String status) => switch (status) {
    AttendanceStatus.present => AppColors.success,
    AttendanceStatus.absentExcused => AppColors.orange,
    _ => AppColors.error,
  };

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(top: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      row.fullName,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: AppColors.navy,
                      ),
                    ),
                    Text(
                      row.studentCode,
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppColors.textMuted,
                      ),
                    ),
                  ],
                ),
              ),
              if (row.approvedLeave)
                const StatusBadgeWidget(
                  label: 'Có đơn nghỉ',
                  tone: BadgeTone.info,
                ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              for (final option in _shortLabels.entries) ...[
                if (option.key != AttendanceStatus.present)
                  const SizedBox(width: 8),
                Expanded(
                  child: _StatusButton(
                    label: option.value,
                    selected: status == option.key,
                    color: _colorOf(option.key),
                    onTap: enabled ? () => onStatus(option.key) : null,
                  ),
                ),
              ],
            ],
          ),
          if (enabled) ...[
            const SizedBox(height: 8),
            TextFormField(
              initialValue: note,
              maxLength: AttendanceSheetController.noteMax,
              onChanged: onNote,
              style: const TextStyle(fontSize: 15, color: AppColors.text),
              decoration: InputDecoration(
                isDense: true,
                counterText: '',
                hintText: 'Ghi chú (không bắt buộc)',
                hintStyle: const TextStyle(color: AppColors.hint),
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 12,
                ),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: const BorderSide(color: AppColors.border),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: const BorderSide(color: AppColors.border),
                ),
              ),
            ),
          ] else if (note.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(
              'Ghi chú: $note',
              style: const TextStyle(fontSize: 14, color: AppColors.text),
            ),
          ],
        ],
      ),
    );
  }
}

/// Nút trạng thái: chọn thì tô màu của trạng thái, chữ trắng.
class _StatusButton extends StatelessWidget {
  final String label;
  final bool selected;
  final Color color;
  final VoidCallback? onTap;

  const _StatusButton({
    required this.label,
    required this.selected,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      selected: selected,
      child: Material(
        color: selected ? color : Colors.white,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
          side: BorderSide(color: selected ? color : AppColors.border),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: SizedBox(
            height: 44,
            child: Center(
              child: Text(
                label,
                maxLines: 1,
                style: TextStyle(
                  fontFamily: kAppFontFamily,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: selected
                      ? Colors.white
                      : (onTap == null ? AppColors.hint : AppColors.text),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
