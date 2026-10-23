import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/number_format.dart';

/// Một tệp đính kèm (thông báo, đơn): icon theo loại, tên, dung lượng; đang
/// tải thì hiện vòng xoay thay cho icon tải về. [onTap] null thì không bấm
/// được (đang tải tệp khác).
class AttachmentTileWidget extends StatelessWidget {
  final AttachmentModel file;
  final bool opening;
  final VoidCallback? onTap;

  const AttachmentTileWidget({
    super.key,
    required this.file,
    required this.opening,
    required this.onTap,
  });

  IconData get _icon => switch (file.extension) {
    'pdf' => Icons.picture_as_pdf,
    'jpg' || 'jpeg' || 'png' => Icons.image,
    'doc' || 'docx' => Icons.description,
    'xls' || 'xlsx' => Icons.table_chart,
    _ => Icons.insert_drive_file,
  };

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: AppColors.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              CircleAvatar(
                radius: 22,
                backgroundColor: AppColors.orangeLight,
                child: Icon(_icon, color: AppColors.orange),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      file.fileName,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: AppColors.text,
                      ),
                    ),
                    Text(
                      formatFileSize(file.sizeBytes),
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppColors.textMuted,
                      ),
                    ),
                  ],
                ),
              ),
              SizedBox(
                width: 40,
                child: opening
                    ? const Center(
                        child: SizedBox(
                          width: 22,
                          height: 22,
                          child: CircularProgressIndicator(
                            strokeWidth: 2.5,
                            color: AppColors.orange,
                          ),
                        ),
                      )
                    : const Icon(Icons.download, color: AppColors.orangeDark),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
