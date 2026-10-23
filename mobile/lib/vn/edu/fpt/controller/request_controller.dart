import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attachment_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/leave_option_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/picked_file_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/request_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/attachment_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/request_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_clock.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/leave_rule.dart';

/// Đơn từ của HS hoặc của con PH: danh sách (tải theo trang), chi tiết, hủy
/// đơn, mở tệp đính kèm và form gửi đơn nghỉ kèm minh chứng. Dùng chung cho
/// màn hình danh sách, chi tiết và form.
class RequestController extends ChangeNotifier {
  static const Set<String> _formFields = {
    'fromDate',
    'toDate',
    'sectionId',
    'title',
    'reason',
  };

  final RequestService _requestService;
  final AttachmentService _attachmentService;
  final AppClock _clock;

  int? _studentId;

  final List<RequestModel> _items = [];
  int _page = -1;
  bool _hasMore = true;
  bool _loading = false;
  ApiErrorModel? _error;

  RequestDetailModel? _detail;
  bool _loadingDetail = false;
  ApiErrorModel? _detailError;
  bool _cancelling = false;
  int? _openingFileId;

  LeaveOptionsModel? _options;
  bool _loadingOptions = false;
  ApiErrorModel? _optionsError;
  final List<PickedFileModel> _files = [];
  Map<String, String> _fieldErrors = const {};
  String? _formError;
  bool _submitting = false;

  /// Tăng mỗi lần đổi người xem, tải lại hoặc mở đơn khác để bỏ phản hồi về
  /// muộn.
  int _listGeneration = 0;
  int _detailGeneration = 0;
  int _optionsGeneration = 0;

  RequestController(
    this._requestService,
    this._attachmentService, {
    this._clock = vietnamNow,
  });

  /// Hôm nay giờ Việt Nam (giới hạn của lịch chọn ngày).
  DateTime get today => _clock();

  /// Null là HS xem đơn của mình.
  int? get studentId => _studentId;

  List<RequestModel> get items => List<RequestModel>.unmodifiable(_items);

  bool get hasMore => _hasMore;

  bool get isLoading => _loading;

  ApiErrorModel? get error => _error;

  RequestDetailModel? get detail => _detail;

  bool get isLoadingDetail => _loadingDetail;

  ApiErrorModel? get detailError => _detailError;

  bool get isCancelling => _cancelling;

  int? get openingFileId => _openingFileId;

  LeaveOptionsModel? get options => _options;

  bool get isLoadingOptions => _loadingOptions;

  ApiErrorModel? get optionsError => _optionsError;

  /// Tệp minh chứng đã chọn, chưa gửi.
  List<PickedFileModel> get files => List<PickedFileModel>.unmodifiable(_files);

  /// Lỗi theo ô, khóa là tên trường của API (`fromDate`, `reason`...).
  Map<String, String> get fieldErrors =>
      Map<String, String>.unmodifiable(_fieldErrors);

  /// Lỗi chung của form (ví dụ tệp bị backend từ chối, lỗi mạng).
  String? get formError => _formError;

  bool get isSubmitting => _submitting;

  /// Xóa mọi dữ liệu của phiên trước, kể cả tệp đã chọn chưa gửi (gọi khi
  /// vào app và khi đăng xuất).
  void reset() {
    _listGeneration++;
    _detailGeneration++;
    _optionsGeneration++;
    _studentId = null;
    _items.clear();
    _page = -1;
    _hasMore = true;
    _loading = false;
    _error = null;
    _detail = null;
    _loadingDetail = false;
    _detailError = null;
    _cancelling = false;
    _openingFileId = null;
    _options = null;
    _loadingOptions = false;
    _optionsError = null;
    _files.clear();
    _fieldErrors = const {};
    _formError = null;
    _submitting = false;
  }

  /// Mở danh sách đơn của [studentId] từ trang đầu.
  Future<void> open(int? studentId) {
    _studentId = studentId;
    _listGeneration++;
    _items.clear();
    _page = -1;
    _hasMore = true;
    _loading = false;
    _error = null;
    return loadMore();
  }

  /// Tải lại danh sách (kéo xuống để tải lại).
  Future<void> reload() => open(_studentId);

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
      final page = await _requestService.fetchRequests(
        _studentId,
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

  /// Mở chi tiết một đơn của người đang xem.
  Future<void> openRequest(int id) async {
    final generation = ++_detailGeneration;
    _detail = null;
    _detailError = null;
    _loadingDetail = true;
    notifyListeners();
    try {
      final detail = await _requestService.fetchRequest(_studentId, id);
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

  /// Hủy đơn đang xem; trả lỗi để màn hình báo, null khi thành công. Lỗi
  /// (ví dụ đơn vừa được duyệt) thì tải lại chi tiết để thấy trạng thái mới.
  Future<ApiErrorModel?> cancel() async {
    final current = _detail;
    if (current == null || _cancelling) {
      return null;
    }
    _cancelling = true;
    notifyListeners();
    try {
      final updated = await _requestService.cancel(_studentId, current.id);
      if (_detail?.id == updated.id) {
        _detail = updated;
      }
      for (final item in _items) {
        if (item.id == updated.id) {
          item.status = updated.status;
        }
      }
      return null;
    } on ApiErrorModel catch (error) {
      _cancelling = false;
      await openRequest(current.id);
      return error;
    } finally {
      _cancelling = false;
      notifyListeners();
    }
  }

  /// Tải tệp của đơn [requestId] rồi mở bằng ứng dụng trên máy; trả lỗi để
  /// màn hình báo, null khi thành công. Mỗi lúc chỉ tải một tệp.
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

  /// Mở form đơn nghỉ mới cho người đang xem: xóa dữ liệu của lần trước và
  /// tải danh sách lớp môn học đang học.
  Future<void> startLeave() async {
    final generation = ++_optionsGeneration;
    _files.clear();
    _fieldErrors = const {};
    _formError = null;
    _submitting = false;
    _options = null;
    _optionsError = null;
    _loadingOptions = true;
    notifyListeners();
    try {
      final options = await _requestService.fetchLeaveOptions(_studentId);
      if (generation == _optionsGeneration) {
        _options = options;
      }
    } on ApiErrorModel catch (error) {
      if (generation == _optionsGeneration) {
        _optionsError = error;
      }
    } finally {
      if (generation == _optionsGeneration) {
        _loadingOptions = false;
        notifyListeners();
      }
    }
  }

  /// Chọn tệp trong máy; trả các lý do tệp bị bỏ (rỗng là nhận hết).
  Future<List<String>> pickFiles() async {
    try {
      return _addFiles(await _attachmentService.pickFiles());
    } on ApiErrorModel catch (error) {
      return [error.message];
    }
  }

  /// Chụp ảnh giấy tờ; trả các lý do ảnh bị bỏ (rỗng là nhận hoặc đã hủy).
  Future<List<String>> takePhoto() async {
    try {
      final photo = await _attachmentService.takePhoto();
      return photo == null ? const [] : _addFiles([photo]);
    } on ApiErrorModel catch (error) {
      return [error.message];
    }
  }

  List<String> _addFiles(List<PickedFileModel> picked) {
    final rejected = <String>[];
    for (final file in picked) {
      final error = LeaveRule.fileError(file);
      if (error != null) {
        rejected.add(error);
      } else if (_files.length >= LeaveRule.maxFiles) {
        if (!rejected.contains(LeaveRule.tooManyFiles)) {
          rejected.add(LeaveRule.tooManyFiles);
        }
      } else {
        _files.add(file);
      }
    }
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

  /// Kiểm tra rồi gửi đơn nghỉ; thành công thì trả đơn vừa tạo (cũng là
  /// [detail]) và tải lại danh sách, lỗi thì trả null và giữ lỗi theo ô.
  Future<RequestDetailModel?> submitLeave(LeaveFormModel form) async {
    if (_submitting) {
      return null;
    }
    final errors = LeaveRule.validate(form, _clock());
    _formError = null;
    if (errors.isNotEmpty) {
      _fieldErrors = errors;
      notifyListeners();
      return null;
    }
    _fieldErrors = const {};
    _submitting = true;
    notifyListeners();
    try {
      final created = await _requestService.submitLeave(
        _studentId,
        form,
        _files,
      );
      _detailGeneration++;
      _detail = created;
      _detailError = null;
      _loadingDetail = false;
      _files.clear();
      // Danh sách tải lại phía sau, không bắt người dùng chờ.
      unawaited(reload());
      return created;
    } on ApiErrorModel catch (error) {
      // Lỗi của ô không có trên form thì hiện ở phần lỗi chung.
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
      _submitting = false;
      notifyListeners();
    }
  }
}
