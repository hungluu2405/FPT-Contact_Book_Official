import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/club_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/club_service.dart';

/// Câu lạc bộ (chỉ đọc): "CLB của tôi / của con", danh sách mọi CLB đang hoạt
/// động (tìm kiếm, tải theo trang) và chi tiết một CLB. Dùng chung cho màn hình
/// danh sách và chi tiết.
class ClubController extends ChangeNotifier {
  final ClubService _clubService;

  int? _studentId;

  List<MyClubModel> _myClubs = const [];
  bool _loadingMyClubs = false;
  ApiErrorModel? _myClubsError;

  String _query = '';
  final List<ClubModel> _clubs = [];
  int _clubPage = -1;
  bool _clubsHasMore = true;
  bool _loadingClubs = false;
  ApiErrorModel? _clubsError;

  ClubDetailModel? _detail;
  List<ClubActivityModel> _activities = const [];
  bool _loadingDetail = false;
  ApiErrorModel? _detailError;
  int? _clubId;
  final List<ClubMemberModel> _members = [];
  int _memberPage = -1;
  bool _membersHasMore = true;
  bool _loadingMembers = false;

  /// Tăng mỗi lần mở lại, tìm kiếm hoặc mở CLB khác để bỏ phản hồi về muộn.
  int _myGeneration = 0;
  int _clubGeneration = 0;
  int _detailGeneration = 0;

  ClubController(this._clubService);

  List<MyClubModel> get myClubs => List<MyClubModel>.unmodifiable(_myClubs);

  bool get isLoadingMyClubs => _loadingMyClubs;

  ApiErrorModel? get myClubsError => _myClubsError;

  String get query => _query;

  List<ClubModel> get clubs => List<ClubModel>.unmodifiable(_clubs);

  bool get clubsHasMore => _clubsHasMore;

  bool get isLoadingClubs => _loadingClubs;

  ApiErrorModel? get clubsError => _clubsError;

  ClubDetailModel? get detail => _detail;

  List<ClubActivityModel> get activities =>
      List<ClubActivityModel>.unmodifiable(_activities);

  bool get isLoadingDetail => _loadingDetail;

  ApiErrorModel? get detailError => _detailError;

  List<ClubMemberModel> get members =>
      List<ClubMemberModel>.unmodifiable(_members);

  bool get membersHasMore => _membersHasMore;

  bool get isLoadingMembers => _loadingMembers;

  /// Mở danh sách cho [studentId] (null: HS xem của mình): CLB của người đó
  /// và trang đầu của mọi CLB.
  Future<void> open(int? studentId) {
    _studentId = studentId;
    _query = '';
    return Future.wait([reloadMyClubs(), _restartClubs()]);
  }

  Future<void> reloadMyClubs() async {
    final generation = ++_myGeneration;
    _loadingMyClubs = true;
    _myClubsError = null;
    notifyListeners();
    try {
      final clubs = await _clubService.fetchMyClubs(_studentId);
      if (generation == _myGeneration) {
        _myClubs = clubs;
      }
    } on ApiErrorModel catch (error) {
      if (generation == _myGeneration) {
        _myClubs = const [];
        _myClubsError = error;
      }
    } finally {
      if (generation == _myGeneration) {
        _loadingMyClubs = false;
        notifyListeners();
      }
    }
  }

  /// Tìm theo mã hoặc tên (bỏ khoảng trắng hai đầu), tải lại từ trang đầu.
  Future<void> search(String query) {
    _query = query.trim();
    return _restartClubs();
  }

  /// Tải lại cả hai phần (kéo xuống để tải lại).
  Future<void> reload() => Future.wait([reloadMyClubs(), _restartClubs()]);

  Future<void> _restartClubs() {
    _clubGeneration++;
    _clubs.clear();
    _clubPage = -1;
    _clubsHasMore = true;
    _loadingClubs = false;
    _clubsError = null;
    return loadMoreClubs();
  }

  /// Tải trang kế tiếp; bỏ qua nếu đang tải hoặc đã hết.
  Future<void> loadMoreClubs() async {
    if (_loadingClubs || !_clubsHasMore) {
      return;
    }
    final generation = _clubGeneration;
    _loadingClubs = true;
    _clubsError = null;
    notifyListeners();
    try {
      final page = await _clubService.fetchClubs(
        query: _query,
        page: _clubPage + 1,
      );
      if (generation != _clubGeneration) {
        return;
      }
      _clubs.addAll(page.items);
      _clubPage = page.page;
      _clubsHasMore = page.hasMore;
    } on ApiErrorModel catch (error) {
      if (generation == _clubGeneration) {
        _clubsError = error;
      }
    } finally {
      if (generation == _clubGeneration) {
        _loadingClubs = false;
        notifyListeners();
      }
    }
  }

  /// Mở chi tiết CLB: thông tin, hoạt động sắp tới và trang đầu thành viên.
  Future<void> openClub(int clubId) async {
    final generation = ++_detailGeneration;
    _clubId = clubId;
    _detail = null;
    _activities = const [];
    _members.clear();
    _memberPage = -1;
    _membersHasMore = true;
    _loadingMembers = false;
    _loadingDetail = true;
    _detailError = null;
    notifyListeners();
    try {
      final results = await Future.wait([
        _clubService.fetchClub(clubId),
        _clubService.fetchActivities(clubId),
      ]);
      if (generation != _detailGeneration) {
        return;
      }
      _detail = results[0] as ClubDetailModel;
      _activities = results[1] as List<ClubActivityModel>;
    } on ApiErrorModel catch (error) {
      if (generation == _detailGeneration) {
        _detailError = error;
      }
    } finally {
      if (generation == _detailGeneration) {
        _loadingDetail = false;
        notifyListeners();
      }
    }
    if (generation == _detailGeneration && _detail != null) {
      await loadMoreMembers();
    }
  }

  /// Tải trang thành viên kế tiếp; lỗi thì giữ danh sách đã có.
  Future<void> loadMoreMembers() async {
    final clubId = _clubId;
    if (clubId == null || _loadingMembers || !_membersHasMore) {
      return;
    }
    final generation = _detailGeneration;
    _loadingMembers = true;
    notifyListeners();
    try {
      final page = await _clubService.fetchMembers(
        clubId,
        page: _memberPage + 1,
      );
      if (generation != _detailGeneration) {
        return;
      }
      _members.addAll(page.items);
      _memberPage = page.page;
      _membersHasMore = page.hasMore;
    } on ApiErrorModel {
      // Danh sách thành viên là phần phụ; kéo xuống để tải lại.
    } finally {
      if (generation == _detailGeneration) {
        _loadingMembers = false;
        notifyListeners();
      }
    }
  }
}
