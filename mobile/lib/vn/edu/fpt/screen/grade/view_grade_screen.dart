import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/grade_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/grade_row_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/empty_state_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/semester_picker_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/number_format.dart';

/// Điểm theo học kỳ: mỗi môn một thẻ có điểm trung bình, bấm để xem từng
/// đầu điểm. [studentId] null là HS xem của mình.
class ViewGradeScreen extends StatefulWidget {
  final GradeController controller;
  final int? studentId;
  final String viewerName;

  const ViewGradeScreen({
    super.key,
    required this.controller,
    required this.studentId,
    required this.viewerName,
  });

  @override
  State<ViewGradeScreen> createState() => _ViewGradeScreenState();
}

class _ViewGradeScreenState extends State<ViewGradeScreen> {
  GradeController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.open(widget.studentId);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) => RefreshIndicator(
          color: AppColors.orange,
          onRefresh: _controller.reload,
          child: ListView(
            padding: EdgeInsets.zero,
            children: [_header(), ..._content()],
          ),
        ),
      ),
    );
  }

  Widget _header() {
    final rows = _controller.rows;
    final loaded = _controller.semester != null && _controller.error == null;
    return PageHeaderWidget(
      icon: Icons.workspace_premium,
      title: 'Điểm',
      headline: loaded ? '${rows.length} môn học' : 'Điểm học kỳ',
      subtitle: _controller.semester?.name,
      viewerName: widget.viewerName,
      bottom: _controller.semesters.isEmpty
          ? null
          : SemesterPickerWidget(
              semesters: _controller.semesters,
              selectedId: _controller.selectedSemesterId,
              onSelect: _controller.selectSemester,
            ),
    );
  }

  List<Widget> _content() {
    final error = _controller.error;
    if (_controller.hasNoSemester) {
      return const [
        EmptyStateWidget(
          icon: Icons.event_busy,
          message: 'Chưa có học kỳ nào để xem điểm.',
        ),
      ];
    }
    if (error != null) {
      return [
        ErrorRetryWidget(message: error.message, onRetry: _controller.reload),
      ];
    }
    final rows = _controller.rows;
    if (_controller.isLoading && _controller.semester == null) {
      return const [
        Padding(
          padding: EdgeInsets.all(32),
          child: Center(
            child: CircularProgressIndicator(color: AppColors.orange),
          ),
        ),
      ];
    }
    return [
      if (_controller.isLoading)
        const LinearProgressIndicator(
          color: AppColors.orange,
          backgroundColor: AppColors.orangeLight,
        ),
      if (rows.isEmpty)
        const EmptyStateWidget(
          icon: Icons.workspace_premium,
          message: 'Học kỳ này chưa có môn học nào.',
        )
      else
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
          child: Column(
            children: [
              for (final row in rows)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: _GradeCard(row: row),
                ),
            ],
          ),
        ),
    ];
  }
}

/// Thẻ một môn: mã, tên, điểm trung bình; bấm để mở các đầu điểm.
class _GradeCard extends StatefulWidget {
  final GradeRowModel row;

  const _GradeCard({required this.row});

  @override
  State<_GradeCard> createState() => _GradeCardState();
}

class _GradeCardState extends State<_GradeCard> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final row = widget.row;
    final average = row.average;
    final hasCells = row.cells.isNotEmpty;
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: AppColors.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: hasCells ? () => setState(() => _expanded = !_expanded) : null,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          row.subjectCode,
                          style: const TextStyle(
                            fontSize: 17,
                            fontWeight: FontWeight.w800,
                            color: AppColors.navy,
                          ),
                        ),
                        Text(
                          row.subjectName,
                          style: const TextStyle(
                            fontSize: 14,
                            color: AppColors.text,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          hasCells
                              ? (average == null
                                    ? 'Chưa đủ điểm'
                                    : 'Điểm trung bình môn')
                              : 'Chưa có đầu điểm',
                          style: const TextStyle(
                            fontSize: 13,
                            color: AppColors.textMuted,
                          ),
                        ),
                      ],
                    ),
                  ),
                  _AverageCircle(average: average),
                  if (hasCells)
                    Icon(
                      _expanded ? Icons.expand_less : Icons.expand_more,
                      color: AppColors.textMuted,
                    ),
                ],
              ),
              if (_expanded) ...[
                const SizedBox(height: 10),
                const Divider(height: 1, color: AppColors.border),
                for (final cell in row.cells) _CellRow(cell: cell),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _AverageCircle extends StatelessWidget {
  final double? average;

  const _AverageCircle({required this.average});

  @override
  Widget build(BuildContext context) {
    final value = average;
    return Container(
      width: 56,
      height: 56,
      margin: const EdgeInsets.only(left: 8),
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: value == null ? const Color(0xFFF3F4F6) : AppColors.orangeLight,
      ),
      alignment: Alignment.center,
      child: Text(
        value == null ? '–' : formatDecimal(value, maxDecimals: 1),
        style: TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.w800,
          color: value == null ? AppColors.textMuted : AppColors.orangeDark,
        ),
      ),
    );
  }
}

/// Một đầu điểm: tên, trọng số, điểm trên thang; "–" là chưa công bố.
class _CellRow extends StatelessWidget {
  final GradeCellModel cell;

  const _CellRow({required this.cell});

  @override
  Widget build(BuildContext context) {
    final score = cell.score;
    return Padding(
      padding: const EdgeInsets.only(top: 10),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  cell.componentName,
                  style: const TextStyle(fontSize: 15, color: AppColors.text),
                ),
                Text(
                  'Trọng số ${formatPercent(cell.weight)}',
                  style: const TextStyle(
                    fontSize: 13,
                    color: AppColors.textMuted,
                  ),
                ),
              ],
            ),
          ),
          Text.rich(
            TextSpan(
              children: [
                TextSpan(
                  text: score == null ? '–' : formatDecimal(score),
                  style: const TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w700,
                    color: AppColors.navy,
                  ),
                ),
                TextSpan(
                  text: ' / ${formatDecimal(cell.maxScore)}',
                  style: const TextStyle(
                    fontSize: 13,
                    color: AppColors.textMuted,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
