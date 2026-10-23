import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/club_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/club_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/empty_state_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/user_labels.dart';

/// Chi tiết câu lạc bộ (chỉ đọc): thông tin, quan hệ của người xem, hoạt động
/// sắp tới và thành viên đang tham gia. Dùng chung [ClubController] với màn
/// hình danh sách.
class ViewClubScreen extends StatefulWidget {
  final ClubController controller;
  final int clubId;
  final String viewerName;

  const ViewClubScreen({
    super.key,
    required this.controller,
    required this.clubId,
    required this.viewerName,
  });

  @override
  State<ViewClubScreen> createState() => _ViewClubScreenState();
}

class _ViewClubScreenState extends State<ViewClubScreen> {
  ClubController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    // Màn hình danh sách bên dưới cũng nghe controller này, nên chỉ tải sau
    // khung hình đầu để không báo thay đổi giữa lúc đang dựng giao diện.
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => _controller.openClub(widget.clubId),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          final detail = _controller.detail?.club.id == widget.clubId
              ? _controller.detail
              : null;
          return RefreshIndicator(
            color: AppColors.orange,
            onRefresh: () => _controller.openClub(widget.clubId),
            child: ListView(
              padding: EdgeInsets.zero,
              children: [
                PageHeaderWidget(
                  icon: Icons.groups,
                  title: 'Câu lạc bộ',
                  headline: detail?.club.name ?? 'Câu lạc bộ',
                  subtitle: detail?.club.code,
                  viewerName: widget.viewerName,
                ),
                ..._content(detail),
              ],
            ),
          );
        },
      ),
    );
  }

  List<Widget> _content(ClubDetailModel? detail) {
    final error = _controller.detailError;
    if (error != null) {
      return [
        ErrorRetryWidget(
          message: error.message,
          onRetry: () => _controller.openClub(widget.clubId),
        ),
      ];
    }
    if (detail == null) {
      return const [
        Padding(
          padding: EdgeInsets.all(32),
          child: Center(
            child: CircularProgressIndicator(color: AppColors.orange),
          ),
        ),
      ];
    }
    final club = detail.club;
    final activities = _controller.activities;
    final members = _controller.members;
    return [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
        child: Wrap(
          spacing: 6,
          runSpacing: 4,
          children: [
            if (!club.isActive)
              const StatusBadgeWidget(
                label: 'Ngừng hoạt động',
                tone: BadgeTone.muted,
              ),
            ?_relationBadge(detail),
          ],
        ),
      ),
      _InfoCard(club: club),
      const Padding(
        padding: EdgeInsets.fromLTRB(16, 0, 16, 0),
        child: Text(
          'Gửi yêu cầu tham gia và đăng ký hoạt động trên trang web của trường.',
          style: TextStyle(
            fontSize: 13,
            fontStyle: FontStyle.italic,
            color: AppColors.textMuted,
          ),
        ),
      ),
      const _SectionTitle('Hoạt động sắp tới'),
      if (activities.isEmpty)
        const EmptyStateWidget(
          icon: Icons.event_available,
          message: 'Chưa có hoạt động nào sắp diễn ra.',
        )
      else
        for (final activity in activities)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            child: _ActivityCard(activity: activity),
          ),
      _SectionTitle('Thành viên (${club.memberCount})'),
      for (final member in members) _MemberRow(member: member),
      if (_controller.isLoadingMembers)
        const Padding(
          padding: EdgeInsets.all(16),
          child: Center(
            child: CircularProgressIndicator(color: AppColors.orange),
          ),
        )
      else if (_controller.membersHasMore)
        Center(
          child: TextButton(
            onPressed: _controller.loadMoreMembers,
            child: const Text('Xem thêm thành viên'),
          ),
        ),
      const SizedBox(height: 24),
    ];
  }

  /// Nhãn quan hệ của người xem: vai trò khi đang tham gia, hoặc trạng thái
  /// (chờ duyệt, bị từ chối, đã rời); null khi chưa từng gửi yêu cầu.
  Widget? _relationBadge(ClubDetailModel detail) {
    final status = detail.myStatus;
    if (status == null) {
      return null;
    }
    if (status == ClubMemberStatus.active) {
      return StatusBadgeWidget(
        label:
            'Đang tham gia · ${kClubRoleLabels[detail.myRole] ?? detail.myRole ?? ''}',
        tone: BadgeTone.success,
      );
    }
    return StatusBadgeWidget(
      label: kClubStatusLabels[status] ?? status,
      tone: status == ClubMemberStatus.rejected
          ? BadgeTone.danger
          : BadgeTone.orange,
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final String text;

  const _SectionTitle(this.text);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 10),
      child: Text(
        text,
        style: const TextStyle(
          fontSize: 17,
          fontWeight: FontWeight.w700,
          color: AppColors.navy,
        ),
      ),
    );
  }
}

/// Mô tả và các dòng thông tin của CLB; dòng không có dữ liệu thì bỏ qua.
class _InfoCard extends StatelessWidget {
  final ClubModel club;

  const _InfoCard({required this.club});

  @override
  Widget build(BuildContext context) {
    final description = club.description ?? '';
    final rows = <(IconData, String, String?)>[
      (Icons.event, 'Thành lập', formatApiDate(club.foundedDate)),
      (Icons.school_outlined, 'Giáo viên phụ trách', club.advisorName),
      (Icons.star_outline, 'Chủ nhiệm', club.presidentName),
      (Icons.people_outline, 'Thành viên', '${club.memberCount}'),
    ];
    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (description.isNotEmpty) ...[
            Text(
              description,
              style: const TextStyle(fontSize: 15, color: AppColors.text),
            ),
            const SizedBox(height: 8),
          ],
          for (final (icon, label, value) in rows)
            if ((value ?? '').isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Row(
                  children: [
                    Icon(icon, size: 18, color: AppColors.textMuted),
                    const SizedBox(width: 8),
                    Text(
                      '$label: ',
                      style: const TextStyle(
                        fontSize: 14,
                        color: AppColors.textMuted,
                      ),
                    ),
                    Expanded(
                      child: Text(
                        value!,
                        style: const TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          color: AppColors.text,
                        ),
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

/// Một hoạt động: tiêu đề, thời gian, địa điểm, số chỗ, đăng ký của người xem.
class _ActivityCard extends StatelessWidget {
  final ClubActivityModel activity;

  const _ActivityCard({required this.activity});

  @override
  Widget build(BuildContext context) {
    final start = activity.startTime.split('T');
    final end = activity.endTime.split('T');
    final date = parseApiDate(start.first);
    final when = [
      if (date != null) '${weekdayLong(date)}, ${dayMonthShort(date)}',
      '${formatApiTime(start.length > 1 ? start[1] : '')}–'
          '${formatApiTime(end.length > 1 ? end[1] : '')}',
    ].join(' · ');
    final max = activity.maxParticipants;
    final location = activity.location ?? '';
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (activity.myStatus != null) ...[
            const StatusBadgeWidget(
              label: 'Đã đăng ký',
              tone: BadgeTone.success,
              dot: true,
            ),
            const SizedBox(height: 6),
          ],
          Text(
            activity.title,
            style: const TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w700,
              color: AppColors.navy,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            when,
            style: const TextStyle(fontSize: 14, color: AppColors.text),
          ),
          Text(
            [
              if (location.isNotEmpty) location,
              max == null
                  ? '${activity.taken} người đăng ký'
                  : '${activity.taken}/$max chỗ',
            ].join(' · '),
            style: const TextStyle(fontSize: 13, color: AppColors.textMuted),
          ),
        ],
      ),
    );
  }
}

class _MemberRow extends StatelessWidget {
  final ClubMemberModel member;

  const _MemberRow({required this.member});

  @override
  Widget build(BuildContext context) {
    final leader = member.role != ClubMemberRole.member;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
      child: Row(
        children: [
          CircleAvatar(
            radius: 18,
            backgroundColor: AppColors.orangeLight,
            child: Text(
              initialOf(member.fullName),
              style: const TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w700,
                color: AppColors.orangeDark,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              member.fullName,
              style: const TextStyle(fontSize: 15, color: AppColors.text),
            ),
          ),
          StatusBadgeWidget(
            label: kClubRoleLabels[member.role] ?? member.role,
            tone: leader ? BadgeTone.orange : BadgeTone.muted,
          ),
        ],
      ),
    );
  }
}
