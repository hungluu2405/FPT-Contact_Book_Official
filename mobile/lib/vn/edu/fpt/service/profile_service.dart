import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/profile_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';

/// Hồ sơ của người đang đăng nhập. Lỗi ném [ApiErrorModel].
class ProfileService {
  final ApiClient _apiClient;

  ProfileService(this._apiClient);

  Future<ProfileModel> fetchProfile() async {
    final data = await _apiClient.get('/me/profile');
    return ProfileModel.fromJson(data as Map<String, dynamic>);
  }
}
