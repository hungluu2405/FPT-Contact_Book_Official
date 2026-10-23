import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/request_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/attachment_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/request_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/teacher_service.dart';

/// Duyệt đơn của giáo viên: hàng đợi đơn mình phụ trách theo trạng thái (tải
/// theo trang), số đơn chờ duyệt cho ô trên trang chủ, chi tiết một đơn và
/// duyệt hoặc từ chối (từ chối bắt buộc ghi chú). Dùng chung cho danh sách,
/// chi tiết và ô "Duyệt đơn".
class RequestReviewController extends ChangeNotifier {
  static const String pending = 'PENDING';
  static const String all = 'ALL';
  static const int commentMax = 500;
  static const String commentRequired = 'Vui lòng nhập ghi chú khi từ chối đơn';
  static const String commentSize = 'Ghi chú tối đa $commentMax ký tự';

  final TeacherService _teacherService;
  final AttachmentService _attachmentService;

  String _filter = pending;
  final List<RequestModel> _items = [];
  int _page = -1;
  bool _hasMore = true;
  bool _loading = false;
  ApiErrorModel? _error;
  int? _pendingCount;

  /// Đơn đã rời hàng đợi "Chờ duyệt" sau khi tải trang: backend phân trang
  /// theo vị trí nên trang kế tiếp đã dồn lên, phải tải lại từ đầu.
  bool _shifted = false;

  RequestDetailModel? _detail;
  bool _loadingDetail = false;
  ApiErrorModel? _detailError;
  bool _reviewing = false;
  String? _commentError;
  String? _reviewError;
  int? _openingFileId;

  /// Tăng mỗi lần đổi bộ lọc, tải lại hoặc mở đơn khác để bỏ phản hồi về
  /// muộn.
  int _listGeneration = 0;
  int _detailGeneration = 0;
  int _countGeneration = 0;

  RequestReviewController(this._teacherService, this._attachmentService);

  /// `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` hoặc `ALL`.
  String get filter => _filter;

  List<RequestModel> get items => List<RequestModel>.unmodifiable(_items);

  bool get hasMore => _hasMore;

  bool get isLoading => _loading;

  ApiErrorModel? get error => _error;

  /// Số đơn chờ duyệt; null khi chưa tải được (ô trang chủ không hiện số).
  int? get pendingCount => _pendingCount;

  RequestDetailModel? get detail => _detail;

  bool get isLoadingDetail => _loadingDetail;

  ApiErrorModel? get detailError => _detailError;

  bool get isReviewing => _reviewing;

  String? get commentError => _commentError;

  /// Lỗi khi duyệt không gắn ô ghi chú (ví dụ đơn vừa bị hủy).
  String? get reviewError => _reviewError;

  int? get openingFileId => _openingFileId;

  /// Xóa dữ liệu của phiên trước (gọi khi vào app và khi đăng xuất).
  void reset() {
    _listGeneration++;
    _detailGeneration++;
    _countGeneration++;
    _filter = pending;
    _items.clear();
    _page = -1;
    _hasMore = true;
    _shifted = false;
    _loading = false;
    _error = null;
    _pendingCount = null;
    _detail = null;
    _loadingDetail = false;
    _detailError = null;
    _reviewing = false;
    _commentError = null;
    _reviewError = null;
    _openingFileId = null;
  }

  /// Mở hàng đợi ở bộ lọc "Chờ duyệt".
  Future<void> open() => setFilter(pending);

  Future<void> setFilter(String filter) {
    _filter = filter;
    return reload();
  }

  /// Tải lại từ trang đầu với bộ lọc hiện tại (kéo xuống để tải lại).
  Future<void> reload() {
    _listGeneration++;
    _items.clear();
    _page = -1;
    _hasMore = true;
    _shifted = false;
    _loading = false;
    _error = null;
    return loadMore();
  }

  /// Tải trang kế tiếp; bỏ qua nếu đang tải hoặc đã hết.
  Future<void> loadMore() async {
    if (_loading || !_hasMore) {
      return;
    }
    if (_shifted) {
      return reload();
    }
    final generation = _listGeneration;
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final page = await _teacherService.fetchReviewRequests(
        status: _filter,
        page: _page + 1,
      );
      if (generation != _listGeneration) {
        return;
      }
      _items.addAll(page.items);
      _page = page.page;
      _hasMore = page.hasMore;
      if (_filter == pending) {
        _pendingCount = page.totalElements;
      }
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

  /// Tải riêng số đơn chờ duyệt (ô "Duyệt đơn" trên trang chủ).
  Future<void> loadPendingCount() async {
    final generation = ++_countGeneration;
    try {
      final page = await _teacherService.fetchReviewRequests(status: pending);
      if (generation == _countGeneration) {
        _pendingCount = page.totalElements;
        notifyListeners();
      }
    } on ApiErrorModel {
      // Chỉ là số trên ô, lỗi thì để trống.
    }
  }

  /// Mở chi tiết một đơn mình phụ trách.
  Future<void> openRequest(int id) async {
    final generation = ++_detailGeneration;
    _detail = null;
    _detailError = null;
    _commentError = null;
    _reviewError = null;
    _reviewing = false;
    _loadingDetail = true;
    notifyListeners();
    try {
      final detail = await _teacherService.fetchReviewRequest(id);
      if (generation == _detailGeneration) {
        _detail = detail;
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

  void clearCommentError() {
    if (_commentError != null) {
      _commentError = null;
      notifyListeners();
    }
  }

  /// Duyệt ([approve]) hoặc từ chối đơn đang xem; trả kết quả, lỗi thì trả
  /// null. Đơn vừa đổi trạng thái ở nơi khác thì tải lại chi tiết.
  Future<ReviewResultModel?> review({
    required bool approve,
    String? comment,
  }) async {
    final current = _detail;
    if (current == null || _reviewing) {
      return null;
    }
    final text = comment?.trim() ?? '';
    _commentError = null;
    _reviewError = null;
    if (!approve && text.isEmpty) {
      _commentError = commentRequired;
    } else if (text.length > commentMax) {
      _commentError = commentSize;
    }
    if (_commentError != null) {
      notifyListeners();
      return null;
    }
    _reviewing = true;
    notifyListeners();
    try {
      final result = await _teacherService.review(
        current.id,
        decision: approve ? RequestStatus.approved : RequestStatus.rejected,
        comment: text,
      );
      if (_detail?.id == current.id) {
        _detail = result.request;
      }
      _updateItem(result.request);
      return result;
    } on ApiErrorModel catch (error) {
      final field = error.fieldErrors['comment'];
      if (field != null) {
        _commentError = field;
        return null;
      }
      _reviewing = false;
      final message = error.message;
      await openRequest(current.id);
      _reviewError = message;
      return null;
    } finally {
      _reviewing = false;
      notifyListeners();
    }
  }

  /// Đơn vừa duyệt rời hàng đợi "Chờ duyệt"; bộ lọc khác thì đổi trạng thái.
  void _updateItem(RequestDetailModel updated) {
    final index = _items.indexWhere((item) => item.id == updated.id);
    if (index >= 0) {
      if (_filter == pending) {
        _items.removeAt(index);
        _shifted = _hasMore;
      } else {
        _items[index].status = updated.status;
      }
    }
    final count = _pendingCount;
    if (count != null && count > 0) {
      _pendingCount = count - 1;
    }
  }

  /// Tải tệp minh chứng của đơn [requestId] rồi mở; trả lỗi để màn hình báo,
  /// null khi thành công. Mỗi lúc chỉ tải một tệp.
  Future<ApiErrorModel?> openFile(int requestId, AttachmentModel file) async {
    if (_openingFileId != null) {
      return null;
    }
    _openingFileId = file.id;
    notifyListeners();
    try {
      await _attachmentService.downloadAndOpen(
        RequestService.filePath(requestId, file.id),
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
}
