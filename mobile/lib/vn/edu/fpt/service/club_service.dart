import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/club_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/page_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';

/// API câu lạc bộ chỉ đọc (docs/API.md mục "Câu lạc bộ"). Lỗi ném
/// [ApiErrorModel].
class ClubService {
  final ApiClient _apiClient;

  ClubService(this._apiClient);

  /// CLB đang hoạt động, [query] tìm theo mã hoặc tên, [page] đếm từ 0.
  Future<PageModel<ClubModel>> fetchClubs({String? query, int page = 0}) async {
    final keyword = query?.trim() ?? '';
    final data = await _apiClient.get(
      '/clubs',
      query: {'q': ?(keyword.isEmpty ? null : keyword), 'page': page},
    );
    return PageModel.fromJson(data as Map<String, dynamic>, ClubModel.fromJson);
  }

  Future<ClubDetailModel> fetchClub(int clubId) async {
    final data = await _apiClient.get('/clubs/$clubId');
    return ClubDetailModel.fromJson(data as Map<String, dynamic>);
  }

  Future<PageModel<ClubMemberModel>> fetchMembers(
    int clubId, {
    int page = 0,
  }) async {
    final data = await _apiClient.get(
      '/clubs/$clubId/members',
      query: {'page': page},
    );
    return PageModel.fromJson(
      data as Map<String, dynamic>,
      ClubMemberModel.fromJson,
    );
  }

  Future<List<ClubActivityModel>> fetchActivities(int clubId) async {
    final data = await _apiClient.get('/clubs/$clubId/activities');
    return (data as List<dynamic>)
        .map((item) => ClubActivityModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  /// CLB của HS ([studentId] null) hoặc của con PH đang xem.
  Future<List<MyClubModel>> fetchMyClubs(int? studentId) async {
    final data = await _apiClient.get(
      '${StudentService.rootPath(studentId)}/clubs',
    );
    return (data as List<dynamic>)
        .map((item) => MyClubModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }
}
