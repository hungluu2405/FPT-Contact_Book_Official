import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/child_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';

/// API dữ liệu học tập của SV và PH (docs/API.md mục "Sinh viên ... và phụ
/// huynh"). Lỗi ném [ApiErrorModel].
class StudentService {
  final ApiClient _apiClient;

  StudentService(this._apiClient);

  /// Gốc đường dẫn: SV xem của mình ([studentId] null) dùng `/student`,
  /// PH xem con dùng `/parent/children/{studentId}`.
  static String rootPath(int? studentId) =>
      studentId == null ? '/student' : '/parent/children/$studentId';

  /// Các con đã liên kết của phụ huynh đang đăng nhập.
  Future<List<ChildModel>> fetchChildren() async {
    final data = await _apiClient.get('/parent/children');
    return (data as List<dynamic>)
        .map((child) => ChildModel.fromJson(child as Map<String, dynamic>))
        .toList();
  }
}
