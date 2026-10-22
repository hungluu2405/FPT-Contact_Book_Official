import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';

/// API thông báo. Giai đoạn 10b chỉ dùng số chưa đọc; danh sách và chi tiết
/// thêm ở 10c. Lỗi ném [ApiErrorModel].
class NotificationService {
  final ApiClient _apiClient;

  NotificationService(this._apiClient);

  Future<int> fetchUnreadCount() async {
    final data = await _apiClient.get('/notifications/unread-count');
    return ((data as Map<String, dynamic>)['count'] as num).toInt();
  }
}
