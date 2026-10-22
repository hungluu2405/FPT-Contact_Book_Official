import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/profile_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/profile_service.dart';

/// Hồ sơ của người đang đăng nhập cho tab Tài khoản.
class ProfileController extends ChangeNotifier {
  final ProfileService _profileService;

  ProfileModel? _profile;
  bool _loading = false;
  ApiErrorModel? _error;

  /// Tăng mỗi lần [reset] để bỏ kết quả của phiên trước về muộn.
  int _generation = 0;

  ProfileController(this._profileService);

  ProfileModel? get profile => _profile;

  bool get isLoading => _loading;

  ApiErrorModel? get error => _error;

  /// Xóa hồ sơ của phiên trước (gọi khi vào trang chủ sau đăng nhập).
  void reset() {
    _generation++;
    _profile = null;
    _loading = false;
    _error = null;
    notifyListeners();
  }

  /// Tải hồ sơ nếu chưa có; [force] để tải lại.
  Future<void> load({bool force = false}) async {
    if (_loading || (_profile != null && !force)) {
      return;
    }
    final generation = _generation;
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final profile = await _profileService.fetchProfile();
      if (generation == _generation) {
        _profile = profile;
      }
    } on ApiErrorModel catch (error) {
      if (generation == _generation) {
        _error = error;
      }
    } finally {
      if (generation == _generation) {
        _loading = false;
        notifyListeners();
      }
    }
  }
}
