import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';

/// Cặp token trả về khi đăng nhập, làm mới token và đổi mật khẩu.
class TokenModel {
  String _accessToken;
  String _refreshToken;
  int _expiresIn;
  UserModel _user;

  TokenModel({
    required this._accessToken,
    required this._refreshToken,
    required this._expiresIn,
    required this._user,
  });

  factory TokenModel.fromJson(Map<String, dynamic> json) {
    return TokenModel(
      accessToken: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String,
      expiresIn: (json['expiresIn'] as num).toInt(),
      user: UserModel.fromJson(json['user'] as Map<String, dynamic>),
    );
  }

  Map<String, dynamic> toJson() => {
    'accessToken': _accessToken,
    'refreshToken': _refreshToken,
    'expiresIn': _expiresIn,
    'user': _user.toJson(),
  };

  String get accessToken => _accessToken;
  set accessToken(String value) => _accessToken = value;

  String get refreshToken => _refreshToken;
  set refreshToken(String value) => _refreshToken = value;

  /// Thời gian sống của access token, tính bằng giây.
  int get expiresIn => _expiresIn;
  set expiresIn(int value) => _expiresIn = value;

  UserModel get user => _user;
  set user(UserModel value) => _user = value;
}
