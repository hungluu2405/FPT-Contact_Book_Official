import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/semester_option_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';

/// Hàng thẻ chọn học kỳ cuộn ngang, đặt trong phần đầu trang (giống dải tuần
/// của Lịch học). Học kỳ đang xem là thẻ cam đặc chữ trắng và luôn được cuộn
/// vào trong khung nhìn.
class SemesterPickerWidget extends StatefulWidget {
  final List<SemesterOptionModel> semesters;
  final int? selectedId;
  final ValueChanged<int> onSelect;

  const SemesterPickerWidget({
    super.key,
    required this.semesters,
    required this.selectedId,
    required this.onSelect,
  });

  @override
  State<SemesterPickerWidget> createState() => _SemesterPickerWidgetState();
}

class _SemesterPickerWidgetState extends State<SemesterPickerWidget> {
  final GlobalKey _selectedKey = GlobalKey();

  @override
  void initState() {
    super.initState();
    _revealSelected();
  }

  @override
  void didUpdateWidget(SemesterPickerWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.selectedId != widget.selectedId ||
        oldWidget.semesters.length != widget.semesters.length) {
      _revealSelected();
    }
  }

  void _revealSelected() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final selected = _selectedKey.currentContext;
      if (mounted && selected != null) {
        Scrollable.ensureVisible(selected, alignment: 0.5);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    if (widget.semesters.isEmpty) {
      return const SizedBox.shrink();
    }
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [
          for (final semester in widget.semesters)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: _chip(semester, semester.id == widget.selectedId),
            ),
        ],
      ),
    );
  }

  Widget _chip(SemesterOptionModel semester, bool selected) {
    return Semantics(
      key: selected ? _selectedKey : null,
      selected: selected,
      button: true,
      child: Material(
        color: selected ? AppColors.orange : Colors.white,
        shape: StadiumBorder(
          side: BorderSide(
            color: selected ? AppColors.orange : AppColors.border,
          ),
        ),
        child: InkWell(
          customBorder: const StadiumBorder(),
          onTap: selected ? null : () => widget.onSelect(semester.id),
          child: Container(
            height: 44,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            alignment: Alignment.center,
            child: Text(
              semester.name,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: selected ? Colors.white : AppColors.text,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
