import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/home_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/child_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';

/// Dòng "Đang xem: ..." dưới tên người dùng. Có từ 2 lựa chọn trở lên thì
/// bấm vào mở bảng chọn từ dưới lên; "Của tôi" chỉ có khi vừa là SV.
class ChildSelectorWidget extends StatelessWidget {
  final HomeController controller;

  const ChildSelectorWidget({super.key, required this.controller});

  String _label(ChildModel? child) =>
      child == null ? 'Của tôi' : '${child.name} · ${child.code}';

  Future<void> _pick(BuildContext context) async {
    final isStudent = controller.user?.isStudent ?? false;
    final options = <ChildModel?>[if (isStudent) null, ...controller.children];
    final selectedId = controller.selectedStudentId;

    final picked = await showModalBottomSheet<_Pick>(
      context: context,
      backgroundColor: Colors.white,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(24, 0, 24, 8),
              child: Text(
                'Chọn người cần xem',
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                  color: AppColors.navy,
                ),
              ),
            ),
            for (final option in options)
              ListTile(
                minTileHeight: 56,
                contentPadding: const EdgeInsets.symmetric(horizontal: 24),
                title: Text(
                  _label(option),
                  style: const TextStyle(fontSize: 16, color: AppColors.text),
                ),
                trailing: option?.id == selectedId
                    ? const Icon(Icons.check, color: AppColors.orange)
                    : null,
                onTap: () => Navigator.of(context).pop(_Pick(option)),
              ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
    if (picked != null) {
      controller.selectChild(picked.child);
    }
  }

  @override
  Widget build(BuildContext context) {
    final canSwitch = controller.canSwitchStudent;
    final text = Text.rich(
      TextSpan(
        children: [
          const TextSpan(
            text: 'Đang xem: ',
            style: TextStyle(color: AppColors.textMuted),
          ),
          TextSpan(
            text: _label(controller.selectedChild),
            style: const TextStyle(
              color: AppColors.orangeDark,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
      style: const TextStyle(fontSize: 15),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
    );

    if (!canSwitch) {
      return text;
    }
    return InkWell(
      borderRadius: BorderRadius.circular(8),
      onTap: () => _pick(context),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 40),
        child: Row(
          children: [
            Flexible(child: text),
            const Icon(Icons.arrow_drop_down, color: AppColors.orangeDark),
          ],
        ),
      ),
    );
  }
}

/// Kết quả của bảng chọn; bọc lại để phân biệt "Của tôi" (null) với đóng
/// bảng mà không chọn.
class _Pick {
  final ChildModel? child;

  const _Pick(this.child);
}
