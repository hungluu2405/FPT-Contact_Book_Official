import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/token_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';

import '../support/fakes.dart';

void main() {
  group('UserModel', () {
    test('reads the user object of the API', () {
      final user = UserModel.fromJson(
        userJson(roles: ['TEACHER', 'HEAD_OF_DEPARTMENT']),
      );

      expect(user.id, 4);
      expect(user.phone, '0900000004');
      expect(user.fullName, 'Nguyễn Văn An');
      expect(user.roles, ['TEACHER', 'HEAD_OF_DEPARTMENT']);
      expect(user.mustChangePassword, isFalse);
      expect(user.toJson(), userJson(roles: ['TEACHER', 'HEAD_OF_DEPARTMENT']));
    });

    test(
      'setters change the value and roles cannot be changed from outside',
      () {
        final user = UserModel.fromJson(userJson());

        user.fullName = 'Trần Thị Bình';
        user.mustChangePassword = true;
        user.roles = ['PARENT'];

        expect(user.fullName, 'Trần Thị Bình');
        expect(user.mustChangePassword, isTrue);
        expect(user.isParent, isTrue);
        expect(() => user.roles.add('ADMIN'), throwsUnsupportedError);
      },
    );

    test('missing optional fields fall back to empty values', () {
      final user = UserModel.fromJson({'id': 9});

      expect(user.fullName, '');
      expect(user.roles, isEmpty);
      expect(user.mustChangePassword, isFalse);
    });

    test('head of department uses the app as a teacher', () {
      final user = UserModel.fromJson(userJson(roles: ['HEAD_OF_DEPARTMENT']));

      expect(user.isTeacher, isTrue);
      expect(user.canUseMobile, isTrue);
    });

    test('admin-only account has no mobile features', () {
      final admin = UserModel.fromJson(userJson(roles: ['ADMIN']));

      expect(admin.canUseMobile, isFalse);
      expect(admin.hasRole(RoleCodes.admin), isTrue);
    });
  });

  test('TokenModel reads the token response', () {
    final token = TokenModel.fromJson(
      tokenJson(user: userJson(mustChangePassword: true)),
    );

    expect(token.accessToken, 'access-1');
    expect(token.refreshToken, 'refresh-1');
    expect(token.expiresIn, 900);
    expect(token.user.mustChangePassword, isTrue);
  });

  group('ApiErrorModel', () {
    final request = RequestOptions(path: '/me');

    DioException withResponse(int status, Object? data) => DioException(
      requestOptions: request,
      response: Response<dynamic>(
        requestOptions: request,
        statusCode: status,
        data: data,
      ),
    );

    test('keeps the first message of each field', () {
      final error = ApiErrorModel.fromJson(
        FakeReply.error(
              400,
              'VALIDATION_FAILED',
              'Dữ liệu gửi lên không hợp lệ',
              fieldErrors: [
                {'field': 'newPassword', 'message': 'Quá ngắn'},
                {'field': 'newPassword', 'message': 'Thiếu chữ số'},
                {'field': 'confirmPassword', 'message': 'Không khớp'},
              ],
            ).body!
            as Map<String, dynamic>,
      );

      expect(error.status, 400);
      expect(error.code, 'VALIDATION_FAILED');
      expect(error.fieldErrors, {
        'newPassword': 'Quá ngắn',
        'confirmPassword': 'Không khớp',
      });
    });

    test('no response means a network error', () {
      final error = ApiErrorModel.fromDioException(
        DioException.connectionError(
          requestOptions: request,
          reason: 'refused',
        ),
      );

      expect(error.isNetworkError, isTrue);
      expect(error.status, 0);
      expect(error.message, ApiErrorModel.networkMessage);
    });

    test('uses the ApiError body when the server sends one', () {
      final error = ApiErrorModel.fromDioException(
        withResponse(
          401,
          FakeReply.error(
            401,
            'BAD_CREDENTIALS',
            'Số điện thoại hoặc mật khẩu không đúng',
          ).body,
        ),
      );

      expect(error.code, 'BAD_CREDENTIALS');
      expect(error.message, 'Số điện thoại hoặc mật khẩu không đúng');
    });

    test('413 without a body explains the upload limit', () {
      final error = ApiErrorModel.fromDioException(withResponse(413, null));

      expect(error.code, ApiErrorModel.payloadTooLarge);
      expect(error.message, ApiErrorModel.payloadTooLargeMessage);
    });

    test('401 without a body is an expired session', () {
      final error = ApiErrorModel.fromDioException(withResponse(401, ''));

      expect(error.code, ApiErrorModel.unauthorized);
    });

    test('other responses without ApiError become an internal error', () {
      final error = ApiErrorModel.fromDioException(
        withResponse(502, '<html>Bad gateway</html>'),
      );

      expect(error.status, 502);
      expect(error.code, ApiErrorModel.internalError);
      expect(error.message, ApiErrorModel.internalMessage);
    });

    test('an ApiErrorModel carried by the exception is returned as is', () {
      final cause = ApiErrorModel(status: 0, code: 'X', message: 'm');

      expect(
        ApiErrorModel.fromDioException(
          DioException(requestOptions: request, error: cause),
        ),
        same(cause),
      );
    });
  });
}
