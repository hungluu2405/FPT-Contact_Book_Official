import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/page_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/sent_notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';

/// API thông báo đã nhận (docs/API.md mục "Dùng chung"). Lỗi ném
/// [ApiErrorModel].
class NotificationService {
  final ApiClient _apiClient;

  NotificationService(this._apiClient);

  Future<int> fetchUnreadCount() async {
    final data = await _apiClient.get('/notifications/unread-count');
    return ((data as Map<String, dynamic>)['count'] as num).toInt();
  }

  /// Hộp thư, mới nhất trước; [page] đếm từ 0.
  Future<PageModel<NotificationModel>> fetchNotifications({
    int page = 0,
  }) async {
    final data = await _apiClient.get('/notifications', query: {'page': page});
    return PageModel.fromJson(
      data as Map<String, dynamic>,
      NotificationModel.fromJson,
    );
  }

  /// Chi tiết; lần đầu mở thì backend đánh dấu đã đọc.
  Future<NotificationDetailModel> fetchNotification(int id) async {
    final data = await _apiClient.get('/notifications/$id');
    return NotificationDetailModel.fromJson(data as Map<String, dynamic>);
  }

  /// Đánh dấu tất cả đã đọc, trả số chưa đọc còn lại.
  Future<int> markAllRead() async {
    final data = await _apiClient.post('/notifications/read-all');
    return ((data as Map<String, dynamic>)['count'] as num).toInt();
  }

  /// Thông báo mình đã gửi, mới nhất trước, kèm số người đã đọc.
  Future<PageModel<SentNotificationModel>> fetchSent({int page = 0}) async {
    final data = await _apiClient.get(
      '/notifications/sent',
      query: {'page': page},
    );
    return PageModel.fromJson(
      data as Map<String, dynamic>,
      SentNotificationModel.fromJson,
    );
  }

  /// Thống kê một thông báo mình đã gửi; [read] true chỉ người đã đọc, false
  /// chưa đọc, null tất cả; [page] là trang người nhận.
  Future<SentNotificationDetailModel> fetchSentDetail(
    int id, {
    bool? read,
    int page = 0,
  }) async {
    final data = await _apiClient.get(
      '/notifications/sent/$id',
      query: {'read': ?read, 'page': page},
    );
    return SentNotificationDetailModel.fromJson(data as Map<String, dynamic>);
  }

  /// Đường dẫn tải tệp đính kèm, dùng với `AttachmentService`.
  static String filePath(int notificationId, int fileId) =>
      '/notifications/$notificationId/files/$fileId';
}
