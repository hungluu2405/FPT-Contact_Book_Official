import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/club_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/club_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/club/view_club_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/empty_state_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/page_header_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/status_badge_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/user_labels.dart';

/// Câu lạc bộ: CLB của HS (hoặc của con PH) và mọi CLB đang hoạt động (tìm
/// kiếm, cuộn để tải thêm). [studentId] null là HS xem của mình.
class ListClubScreen extends StatefulWidget {
  final ClubController controller;
  final int? studentId;
  final String viewerName;

  const ListClubScreen({
    super.key,
    required this.controller,
    required this.studentId,
    required this.viewerName,
  });

  @override
  State<ListClubScreen> createState() => _ListClubScreenState();
}

class _ListClubScreenState extends State<ListClubScreen> {
  final _searchCtrl = TextEditingController();

  ClubController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.open(widget.studentId);
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  void _openClub(int clubId) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ViewClubScreen(
          controller: _controller,
          clubId: clubId,
          viewerName: widget.viewerName,
        ),
      ),
    );
  }

  bool _onScroll(ScrollNotification notification) {
    if (notification.metrics.extentAfter < 300) {
      _controller.loadMoreClubs();
    }
    return false;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) {
          final clubs = _controller.clubs;
          final before = _beforeAllClubs();
          return NotificationListener<ScrollNotification>(
            onNotification: _onScroll,
            child: RefreshIndicator(
              color: AppColors.orange,
              onRefresh: _controller.reload,
              child: ListView.builder(
                padding: EdgeInsets.zero,
                itemCount: before.length + clubs.length + 1,
                itemBuilder: (context, index) {
                  if (index < before.length) {
                    return before[index];
                  }
                  if (index == before.length + clubs.length) {
                    return _footer();
                  }
                  final club = clubs[index - before.length];
                  return Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                    child: _ClubCard(
                      club: club,
                      onTap: () => _openClub(club.id),
                    ),
                  );
                },
              ),
            ),
          );
        },
      ),
    );
  }

  /// Phần đầu, mục "Đang tham gia", tiêu đề và ô tìm kiếm của mọi CLB.
  List<Widget> _beforeAllClubs() {
    final mine = _controller.myClubs;
    final ofChild = widget.studentId != null;
    final active = mine
        .where((club) => club.status == ClubMemberStatus.active)
        .length;
    return [
      PageHeaderWidget(
        icon: Icons.groups,
        title: 'Câu lạc bộ',
        headline: ofChild ? 'CLB của con' : 'CLB của tôi',
        subtitle: active == 0 ? null : 'Đang tham gia $active câu lạc bộ',
        viewerName: widget.viewerName,
      ),
      const _SectionTitle('Đang tham gia'),
      ..._myClubs(ofChild),
      const _SectionTitle('Tất cả câu lạc bộ'),
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
        child: TextField(
          controller: _searchCtrl,
          textInputAction: TextInputAction.search,
          onSubmitted: _controller.search,
          style: const TextStyle(fontSize: 16, color: AppColors.text),
          decoration: InputDecoration(
            hintText: 'Tìm theo mã hoặc tên câu lạc bộ',
            prefixIcon: const Icon(Icons.search),
            filled: true,
            fillColor: Colors.white,
            contentPadding: const EdgeInsets.symmetric(vertical: 14),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: const BorderSide(color: AppColors.border),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: const BorderSide(color: AppColors.border),
            ),
          ),
        ),
      ),
    ];
  }

  List<Widget> _myClubs(bool ofChild) {
    final error = _controller.myClubsError;
    if (error != null) {
      return [
        ErrorRetryWidget(
          message: error.message,
          onRetry: _controller.reloadMyClubs,
        ),
      ];
    }
    final mine = _controller.myClubs;
    if (mine.isEmpty) {
      if (_controller.isLoadingMyClubs) {
        return const [_Spinner()];
      }
      return [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
          child: Text(
            ofChild
                ? 'Con chưa tham gia câu lạc bộ nào.'
                : 'Bạn chưa tham gia câu lạc bộ nào.',
            style: const TextStyle(fontSize: 15, color: AppColors.textMuted),
          ),
        ),
      ];
    }
    return [
      for (final club in mine)
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
          child: _MyClubCard(club: club, onTap: () => _openClub(club.clubId)),
        ),
    ];
  }

  Widget _footer() {
    final error = _controller.clubsError;
    if (error != null) {
      return ErrorRetryWidget(
        message: error.message,
        onRetry: _controller.loadMoreClubs,
      );
    }
    if (_controller.isLoadingClubs ||
        (_controller.clubs.isEmpty && _controller.clubsHasMore)) {
      return const _Spinner();
    }
    if (_controller.clubs.isEmpty) {
      return EmptyStateWidget(
        icon: Icons.groups,
        message: _controller.query.isEmpty
            ? 'Chưa có câu lạc bộ nào đang hoạt động.'
            : 'Không tìm thấy câu lạc bộ phù hợp.',
      );
    }
    if (_controller.clubsHasMore) {
      // Dự phòng khi trang đầu chưa đủ dài để cuộn.
      return Center(
        child: TextButton(
          onPressed: _controller.loadMoreClubs,
          child: const Text('Tải thêm'),
        ),
      );
    }
    return const SizedBox(height: 24);
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

class _Spinner extends StatelessWidget {
  const _Spinner();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.all(24),
      child: Center(child: CircularProgressIndicator(color: AppColors.orange)),
    );
  }
}

/// Chữ cái đầu của tên CLB sau khi bỏ chữ "Câu lạc bộ" ("Lập trình F-Code"
/// → "L"), để các CLB có avatar khác nhau.
String _clubInitial(String name) {
  final short = name.replaceFirst(RegExp(r'^(Câu lạc bộ|CLB)\s+'), '').trim();
  return short.isEmpty ? '?' : short.characters.first.toUpperCase();
}

/// Thẻ trắng có avatar chữ cái đầu của CLB, dùng chung cho hai mục.
class _ClubTile extends StatelessWidget {
  final String name;
  final String subtitle;
  final Widget? badge;
  final VoidCallback onTap;

  const _ClubTile({
    required this.name,
    required this.subtitle,
    required this.onTap,
    this.badge,
  });

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
          padding: const EdgeInsets.all(14),
          child: Row(
            children: [
              CircleAvatar(
                radius: 24,
                backgroundColor: AppColors.orangeLight,
                child: Text(
                  _clubInitial(name),
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w700,
                    color: AppColors.orangeDark,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (badge != null) ...[badge!, const SizedBox(height: 4)],
                    Text(
                      name,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: AppColors.navy,
                      ),
                    ),
                    Text(
                      subtitle,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppColors.textMuted,
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: AppColors.textMuted),
            ],
          ),
        ),
      ),
    );
  }
}

class _MyClubCard extends StatelessWidget {
  final MyClubModel club;
  final VoidCallback onTap;

  const _MyClubCard({required this.club, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final active = club.status == ClubMemberStatus.active;
    final label = active
        ? (kClubRoleLabels[club.role] ?? club.role)
        : (kClubStatusLabels[club.status] ?? club.status);
    return _ClubTile(
      name: club.name,
      subtitle: club.code,
      onTap: onTap,
      badge: StatusBadgeWidget(
        label: label,
        tone: active
            ? BadgeTone.success
            : (club.status == ClubMemberStatus.rejected
                  ? BadgeTone.danger
                  : BadgeTone.orange),
      ),
    );
  }
}

class _ClubCard extends StatelessWidget {
  final ClubModel club;
  final VoidCallback onTap;

  const _ClubCard({required this.club, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final description = club.description ?? '';
    return _ClubTile(
      name: club.name,
      subtitle: [
        '${club.memberCount} thành viên',
        if (description.isNotEmpty) description,
      ].join(' · '),
      onTap: onTap,
    );
  }
}
