import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/department_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/picked_file_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/sent_notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/attachment_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/department_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/notification_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/leave_rule.dart';

/// Thông báo nội bộ của tổ trưởng: soạn và gửi (cả tổ hoặc riêng một giáo
/// viên, kèm tệp), danh sách thông báo đã gửi và thống kê đã đọc của một
/// thông báo. Dùng chung cho form gửi, danh sách và chi tiết đã gửi.
class DepartmentNotificationController extends ChangeNotifier {
  static const int titleMax = 200;
  static const String titleRequired = 'Vui lòng nhập tiêu đề';
  static const String titleSize = 'Tiêu đề tối đa $titleMax ký tự';
  static const String contentRequired = 'Vui lòng nhập nội dung';
  static const Set<String> _formFields = {'title', 'content', 'priority'};

  final DepartmentService _departmentService;
  final NotificationService _notificationService;
  final AttachmentService _attachmentService;

  DepartmentModel? _department;
  int? _teacherUserId;
  String? _teacherName;
  final List<PickedFileModel> _files = [];
  Map<String, String> _fieldErrors = const {};
  String? _formError;
  bool _sending = false;

  final List<SentNotificationModel> _sentItems = [];
  int _sentPage = -1;
  bool _sentHasMore = true;
  bool _loadingSent = false;
  ApiErrorModel? _sentError;

  SentNotificationDetailModel? _detail;
  bool? _readFilter;
  final List<RecipientModel> _recipients = [];
  int _recipientPage = 0;
  bool _recipientsHasMore = false;
  bool _loadingRecipients = false;
  bool _loadingDetail = false;
  ApiErrorModel? _detailError;
  int? _openingFileId;

  /// Tăng mỗi lần tải lại danh sách, mở thông báo khác hoặc đổi bộ lọc để
  /// bỏ phản hồi về muộn.
  int _sentGeneration = 0;
  int _detailGeneration = 0;

  DepartmentNotificationController(
    this._departmentService,
    this._notificationService,
    this._attachmentService,
  );

  /// Người nhận của form đang soạn: "Cả `tổ`" hoặc "Riêng thầy/cô `tên`";
  /// rỗng khi chưa soạn.
  String get targetLabel {
    if (_teacherName != null) {
      return 'Riêng thầy/cô $_teacherName';
    }
    return _department == null ? '' : 'Cả ${_department!.name}';
  }

  /// Gửi riêng một giáo viên (thay vì cả tổ).
  bool get isToTeacher => _teacherUserId != null;

  List<PickedFileModel> get files => List<PickedFileModel>.unmodifiable(_files);

  /// Lỗi theo ô (`title`, `content`, `priority`).
  Map<String, String> get fieldErrors =>
      Map<String, String>.unmodifiable(_fieldErrors);

  String? get formError => _formError;

  bool get isSending => _sending;

  List<SentNotificationModel> get sentItems =>
      List<SentNotificationModel>.unmodifiable(_sentItems);

  bool get sentHasMore => _sentHasMore;

  bool get isLoadingSent => _loadingSent;

  ApiErrorModel? get sentError => _sentError;

  SentNotificationDetailModel? get detail => _detail;

  /// null: mọi người nhận; true: đã đọc; false: chưa đọc.
  bool? get readFilter => _readFilter;

  List<RecipientModel> get recipients =>
      List<RecipientModel>.unmodifiable(_recipients);

  bool get recipientsHasMore => _recipientsHasMore;

  bool get isLoadingRecipients => _loadingRecipients;

  bool get isLoadingDetail => _loadingDetail;

  ApiErrorModel? get detailError => _detailError;

  int? get openingFileId => _openingFileId;

  /// Xóa dữ liệu của phiên trước (gọi khi vào app và khi đăng xuất).
  void reset() {
    _sentGeneration++;
    _detailGeneration++;
    _clearCompose();
    _department = null;
    _sentItems.clear();
    _sentPage = -1;
    _sentHasMore = true;
    _loadingSent = false;
    _sentError = null;
    _clearDetail();
    _openingFileId = null;
  }

  /// Mở form mới: gửi cả [department], hoặc riêng giáo viên [teacherUserId].
  void startCompose({
    required DepartmentModel? department,
    int? teacherUserId,
    String? teacherName,
  }) {
    _clearCompose();
    _department = department;
    _teacherUserId = teacherUserId;
    _teacherName = teacherUserId == null ? null : teacherName;
    notifyListeners();
  }

  void _clearCompose() {
    _teacherUserId = null;
    _teacherName = null;
    _files.clear();
    _fieldErrors = const {};
    _formError = null;
    _sending = false;
  }

  /// Chọn tệp trong máy; trả lý do các tệp bị bỏ.
  Future<List<String>> pickFiles() async {
    try {
      return _addFiles(await _attachmentService.pickFiles());
    } on ApiErrorModel catch (error) {
      return [error.message];
    }
  }

  /// Chụp ảnh; trả lý do ảnh bị bỏ (rỗng là nhận hoặc đã hủy).
  Future<List<String>> takePhoto() async {
    try {
      final photo = await _attachmentService.takePhoto();
      return photo == null ? const [] : _addFiles([photo]);
    } on ApiErrorModel catch (error) {
      return [error.message];
    }
  }

  List<String> _addFiles(List<PickedFileModel> picked) {
    final rejected = LeaveRule.addFiles(_files, picked);
    if (picked.isNotEmpty) {
      _formError = null;
      notifyListeners();
    }
    return rejected;
  }

  void removeFile(int index) {
    if (index < 0 || index >= _files.length) {
      return;
    }
    _files.removeAt(index);
    notifyListeners();
  }

  /// Bỏ lỗi của một ô khi người dùng sửa ô đó.
  void clearFieldError(String field) {
    if (!_fieldErrors.containsKey(field)) {
      return;
    }
    _fieldErrors = Map<String, String>.of(_fieldErrors)..remove(field);
    notifyListeners();
  }

  /// Kiểm tra rồi gửi; thành công thì trả thông báo vừa gửi (cũng là
  /// [detail]) và danh sách đã gửi sẽ tải lại khi mở; lỗi thì trả null.
  Future<SentNotificationDetailModel?> send(
    DepartmentNotificationFormModel form,
  ) async {
    if (_sending) {
      return null;
    }
    final errors = <String, String>{};
    final title = form.title.trim();
    if (title.isEmpty) {
      errors['title'] = titleRequired;
    } else if (title.length > titleMax) {
      errors['title'] = titleSize;
    }
    if (form.content.trim().isEmpty) {
      errors['content'] = contentRequired;
    }
    _formError = null;
    if (errors.isNotEmpty) {
      _fieldErrors = errors;
      notifyListeners();
      return null;
    }
    _fieldErrors = const {};
    _sending = true;
    notifyListeners();
    try {
      final sent = await _departmentService.sendNotification(
        form,
        departmentId: _department?.id,
        teacherUserId: _teacherUserId,
        files: _files,
      );
      _files.clear();
      _showDetail(sent);
      // Danh sách đã gửi đang có (nếu có) đã cũ: lần mở sau tải lại.
      _sentGeneration++;
      _sentItems.clear();
      _sentPage = -1;
      _sentHasMore = true;
      _loadingSent = false;
      return sent;
    } on ApiErrorModel catch (error) {
      final known = <String, String>{};
      final others = <String>[];
      error.fieldErrors.forEach((field, message) {
        if (_formFields.contains(field)) {
          known[field] = message;
        } else {
          others.add(message);
        }
      });
      _fieldErrors = known;
      _formError = others.isNotEmpty
          ? others.join('\n')
          : (known.isEmpty ? error.message : null);
      return null;
    } finally {
      _sending = false;
      notifyListeners();
    }
  }

  /// Tải lại danh sách đã gửi từ trang đầu.
  Future<void> openSent() {
    _sentGeneration++;
    _sentItems.clear();
    _sentPage = -1;
    _sentHasMore = true;
    _loadingSent = false;
    _sentError = null;
    return loadMoreSent();
  }

  /// Tải trang kế tiếp; bỏ qua nếu đang tải hoặc đã hết.
  Future<void> loadMoreSent() async {
    if (_loadingSent || !_sentHasMore) {
      return;
    }
    final generation = _sentGeneration;
    _loadingSent = true;
    _sentError = null;
    notifyListeners();
    try {
      final page = await _notificationService.fetchSent(page: _sentPage + 1);
      if (generation != _sentGeneration) {
        return;
      }
      _sentItems.addAll(page.items);
      _sentPage = page.page;
      _sentHasMore = page.hasMore;
    } on ApiErrorModel catch (error) {
      if (generation == _sentGeneration) {
        _sentError = error;
      }
    } finally {
      if (generation == _sentGeneration) {
        _loadingSent = false;
        notifyListeners();
      }
    }
  }

  /// Mở thống kê một thông báo đã gửi với mọi người nhận.
  Future<void> openSentDetail(int id) {
    _clearDetail();
    return _loadDetail(id);
  }

  /// Lọc người nhận: null tất cả, true đã đọc, false chưa đọc.
  Future<void> setReadFilter(bool? read) async {
    final current = _detail;
    if (current == null || read == _readFilter) {
      return;
    }
    _readFilter = read;
    await _loadDetail(current.id);
  }

  Future<void> _loadDetail(int id) async {
    final generation = ++_detailGeneration;
    _loadingDetail = true;
    _detailError = null;
    notifyListeners();
    try {
      final detail = await _notificationService.fetchSentDetail(
        id,
        read: _readFilter,
      );
      if (generation == _detailGeneration) {
        _showDetail(detail);
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

  /// Tải thêm một trang người nhận theo bộ lọc hiện tại.
  Future<void> loadMoreRecipients() async {
    final current = _detail;
    if (current == null || _loadingRecipients || !_recipientsHasMore) {
      return;
    }
    final generation = _detailGeneration;
    _loadingRecipients = true;
    notifyListeners();
    try {
      final next = await _notificationService.fetchSentDetail(
        current.id,
        read: _readFilter,
        page: _recipientPage + 1,
      );
      if (generation != _detailGeneration) {
        return;
      }
      _recipients.addAll(next.recipients.items);
      _recipientPage = next.recipients.page;
      _recipientsHasMore = next.recipients.hasMore;
    } on ApiErrorModel {
      // Phần phụ của màn hình; kéo xuống để tải lại.
    } finally {
      if (generation == _detailGeneration) {
        _loadingRecipients = false;
        notifyListeners();
      }
    }
  }

  void _showDetail(SentNotificationDetailModel detail) {
    _detail = detail;
    _recipients
      ..clear()
      ..addAll(detail.recipients.items);
    _recipientPage = detail.recipients.page;
    _recipientsHasMore = detail.recipients.hasMore;
    _detailError = null;
  }

  void _clearDetail() {
    _detailGeneration++;
    _detail = null;
    _readFilter = null;
    _recipients.clear();
    _recipientPage = 0;
    _recipientsHasMore = false;
    _loadingRecipients = false;
    _loadingDetail = false;
    _detailError = null;
  }

  /// Tải tệp đính kèm của thông báo mình đã gửi rồi mở; trả lỗi để màn
  /// hình báo, null khi thành công. Mỗi lúc chỉ tải một tệp.
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
}
