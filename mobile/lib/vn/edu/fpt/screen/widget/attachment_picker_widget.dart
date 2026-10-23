import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/picked_file_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_theme.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/leave_rule.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/number_format.dart';

/// Tệp đã chọn để gửi kèm (bấm dấu x để bỏ) và hai nút chụp ảnh, chọn tệp;
/// dùng cho đơn nghỉ và thông báo của tổ trưởng.
class AttachmentPickerWidget extends StatelessWidget {
  /// Tên phần, ví dụ "Tệp minh chứng"; sau tên hiện số tệp đã chọn.
  final String label;
  final List<PickedFileModel> files;
  final ValueChanged<int> onRemove;
  final VoidCallback onTakePhoto;
  final VoidCallback onPickFiles;

  const AttachmentPickerWidget({
    super.key,
    required this.label,
    required this.files,
    required this.onRemove,
    required this.onTakePhoto,
    required this.onPickFiles,
  });

  @override
  Widget build(BuildContext context) {
    final full = files.length >= LeaveRule.maxFiles;
    final buttonStyle = OutlinedButton.styleFrom(
      foregroundColor: AppColors.orangeDark,
      side: const BorderSide(color: AppColors.orange),
      minimumSize: const Size(0, 48),
      textStyle: const TextStyle(
        fontFamily: kAppFontFamily,
        fontSize: 15,
        fontWeight: FontWeight.w600,
      ),
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Text(
            '$label (${files.length}/${LeaveRule.maxFiles})',
            style: const TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w500,
              color: AppColors.text,
            ),
          ),
        ),
        for (var i = 0; i < files.length; i++)
          Container(
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.fromLTRB(12, 4, 4, 4),
            decoration: BoxDecoration(
              color: AppColors.orangeLight,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              children: [
                Icon(
                  files[i].extension == 'pdf'
                      ? Icons.picture_as_pdf
                      : Icons.insert_drive_file,
                  color: AppColors.orange,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    '${files[i].fileName} · '
                    '${formatFileSize(files[i].sizeBytes)}',
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 15, color: AppColors.text),
                  ),
                ),
                IconButton(
                  tooltip: 'Bỏ tệp ${files[i].fileName}',
                  icon: const Icon(Icons.close, color: AppColors.textMuted),
                  onPressed: () => onRemove(i),
                ),
              ],
            ),
          ),
        Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: full ? null : onTakePhoto,
                icon: const Icon(Icons.photo_camera_outlined),
                label: const Text('Chụp ảnh'),
                style: buttonStyle,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: OutlinedButton.icon(
                onPressed: full ? null : onPickFiles,
                icon: const Icon(Icons.attach_file),
                label: const Text('Chọn tệp'),
                style: buttonStyle,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        const Text(
          'Tối đa 5 tệp, mỗi tệp tối đa 5 MB; nhận PDF, JPG, PNG, DOCX, XLSX.',
          style: TextStyle(fontSize: 13, color: AppColors.textMuted),
        ),
      ],
    );
  }
}
