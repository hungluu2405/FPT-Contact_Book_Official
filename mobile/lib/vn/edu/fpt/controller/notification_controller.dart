import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/attachment_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/notification_service.dart';

/// Hộp thư thông báo (tải theo trang), chi tiết một thông báo và mở tệp đính
/// kèm. Dùng chung cho màn hình danh sách và chi tiết.
class NotificationController extends ChangeNotifier {
  final NotificationService _notificationService;
  final AttachmentService _attachmentService;

  /// Gọi khi số chưa đọc trên server vừa đổi (mở thông báo chưa đọc, đánh
  /// dấu tất cả), để trang chủ tải lại số trên biểu tượng chuông.
  VoidCallback? onUnreadChanged;

  final List<NotificationModel> _items = [];
  int _page = -1;
  bool _hasMore = true;
  bool _loading = false;
  ApiErrorModel? _error;
  bool _markingAll = false;

  NotificationDetailModel? _detail;
  bool _loadingDetail = false;
  ApiErrorModel? _detailError;
  int? _openingFileId;

  /// Tăng mỗi lần tải lại danh sách hoặc mở thông báo khác để bỏ phản hồi
  /// về muộn.
  int _listGeneration = 0;
  int _detailGeneration = 0;

  NotificationController(this._notificationService, this._attachmentService);

  List<NotificationModel> get items =>
      List<NotificationModel>.unmodifiable(_items);

  bool get hasMore => _hasMore;

  bool get isLoading => _loading;

  ApiErrorModel? get error => _error;

  bool get isMarkingAll => _markingAll;

  NotificationDetailModel? get detail => _detail;

  bool get isLoadingDetail => _loadingDetail;

  ApiErrorModel? get detailError => _detailError;

  /// Tệp đang được tải để mở; null khi không tải tệp nào.
  int? get openingFileId => _openingFileId;

  /// Có thông báo chưa đọc trong các trang đã tải.
  bool get hasUnreadLoaded => _items.any((item) => !item.read);

  /// Xóa dữ liệu và tệp đã tải (gọi khi vào app và khi rời app sau đăng
  /// xuất).
  Future<void> reset() {
    _listGeneration++;
    _detailGeneration++;
    _items.clear();
    _page = -1;
    _hasMore = true;
    _loading = false;
    _error = null;
    _markingAll = false;
    _detail = null;
    _loadingDetail = false;
    _detailError = null;
    _openingFileId = null;
    return _attachmentService.clearCache();
  }

  /// Tải lại hộp thư từ trang đầu (mở tab, kéo xuống để tải lại).
  Future<void> open() {
    _listGeneration++;
    _items.clear();
    _page = -1;
    _hasMore = true;
    _loading = false;
    _error = null;
    return loadMore();
  }

  /// Tải trang kế tiếp; bỏ qua nếu đang tải hoặc đã hết.
  Future<void> loadMore() async {
    if (_loading || !_hasMore) {
      return;
    }
    final generation = _listGeneration;
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final page = await _notificationService.fetchNotifications(
        page: _page + 1,
      );
      if (generation != _listGeneration) {
        return;
      }
      _items.addAll(page.items);
      _page = page.page;
      _hasMore = page.hasMore;
    } on ApiErrorModel catch (error) {
      if (generation == _listGeneration) {
        _error = error;
      }
    } finally {
      if (generation == _listGeneration) {
        _loading = false;
        notifyListeners();
      }
    }
  }

  /// Mở chi tiết; backend đánh dấu đã đọc nên dòng trong danh sách cũng
  /// chuyển sang đã đọc.
  Future<void> openNotification(int id) async {
    final generation = ++_detailGeneration;
    _detail = null;
    _detailError = null;
    _loadingDetail = true;
    notifyListeners();
    try {
      final detail = await _notificationService.fetchNotification(id);
      if (generation != _detailGeneration) {
        return;
      }
      _detail = detail;
      final item = _itemById(id);
      if (item == null || !item.read) {
        item?.read = true;
        onUnreadChanged?.call();
      }
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
  }

  /// Đánh dấu tất cả đã đọc; trả lỗi để màn hình báo, null khi thành công.
  Future<ApiErrorModel?> markAllRead() async {
    if (_markingAll) {
      return null;
    }
    _markingAll = true;
    notifyListeners();
    try {
      await _notificationService.markAllRead();
      // Trang đang tải dở mang trạng thái đọc cũ: bỏ nó, cuộn tiếp sẽ tải lại.
      if (_loading) {
        _listGeneration++;
        _loading = false;
      }
      for (final item in _items) {
        item.read = true;
      }
      onUnreadChanged?.call();
      return null;
    } on ApiErrorModel catch (error) {
      return error;
    } finally {
      _markingAll = false;
      notifyListeners();
    }
  }

  /// Tải tệp của thông báo [notificationId] rồi mở bằng ứng dụng trên máy;
  /// trả lỗi để màn hình báo, null khi thành công. Mỗi lúc chỉ tải một tệp.
  Future<ApiErrorModel?> openFile(
    int notificationId,
    AttachmentModel file,
  ) async {
    if (_openingFileId != null) {
      return null;
    }
    _openingFileId = file.id;
    notifyListeners();
    try {
      await _attachmentService.downloadAndOpen(
        NotificationService.filePath(notificationId, file.id),
        file.fileName,
      );
      return null;
    } on ApiErrorModel catch (error) {
      return error;
    } finally {
      _openingFileId = null;
      notifyListeners();
    }
  }

  NotificationModel? _itemById(int id) {
    for (final item in _items) {
      if (item.id == id) {
        return item;
      }
    }
    return null;
  }
}
