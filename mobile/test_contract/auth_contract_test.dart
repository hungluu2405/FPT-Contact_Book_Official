import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/token_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/auth_service.dart';

import '../test/support/fakes.dart';
import 'contract_config.dart';

/// Hợp đồng API xác thực, chạy với backend thật trên localhost và dữ liệu
/// mẫu của database/fpt_contact_book.sql (mật khẩu chung Fpt@12345).
/// Không test nào đổi mật khẩu hay khóa tài khoản mẫu.
///
/// Chạy: `flutter test test_contract` khi backend đang chạy ở cổng 8080.
const String kSeedPassword = 'Fpt@12345';

class ContractClient {
  final MemoryTokenStorage storage = MemoryTokenStorage();
  late final ApiClient api = ApiClient(
    baseUrl: contractApiRoot(),
    tokenStorage: storage,
  );
  late final AuthService auth = AuthService(api, storage);
}

/// Số điện thoại không có trong dữ liệu mẫu, đổi mỗi lần chạy để không
/// chạm ngưỡng khóa tạm 5 lần sai.
String randomUnknownPhone() {
  final random = Random();
  return '099${List.generate(7, (_) => random.nextInt(10)).join()}';
}

TypeMatcher<ApiErrorModel> apiError(String code, {String? field}) {
  var matcher = isA<ApiErrorModel>().having((e) => e.code, 'code', code);
  if (field != null) {
    matcher = matcher.having(
      (e) => e.fieldErrors.keys,
      'fieldErrors',
      contains(field),
    );
  }
  return matcher;
}

void main() {
  group('login with seed accounts', () {
    const accounts = {
      '0900000004': RoleCodes.student,
      '0900000005': RoleCodes.parent,
      '0900000003': RoleCodes.teacher,
      '0900000001': RoleCodes.admin,
    };

    for (final entry in accounts.entries) {
      test('${entry.key} gets tokens and the ${entry.value} role', () async {
        final client = ContractClient();

        final token = await client.auth.login(entry.key, kSeedPassword);

        expect(token.accessToken, isNotEmpty);
        expect(token.refreshToken, isNotEmpty);
        expect(token.expiresIn, greaterThan(0));
        expect(token.user.phone, entry.key);
        expect(token.user.fullName, isNotEmpty);
        expect(token.user.roles, contains(entry.value));
        expect(token.user.mustChangePassword, isFalse);
        expect(client.storage.refreshToken, token.refreshToken);
      });
    }
  });

  test('phone in +84 form is normalised by the server', () async {
    final token = await ContractClient().auth.login(
      '+84 900 000 004',
      kSeedPassword,
    );

    expect(token.user.phone, '0900000004');
  });

  test('GET /me matches the user of the login response', () async {
    final client = ContractClient();
    final token = await client.auth.login('0900000005', kSeedPassword);

    final me = await client.auth.fetchCurrentUser();

    expect(me.toJson(), token.user.toJson());
  });

  test('refresh returns a new token pair', () async {
    final client = ContractClient();
    final first = await client.auth.login('0900000004', kSeedPassword);

    final data = await client.api.post(
      '/auth/refresh',
      data: {'refreshToken': first.refreshToken},
      authenticated: false,
    );
    final refreshed = TokenModel.fromJson(data as Map<String, dynamic>);

    expect(refreshed.accessToken, isNotEmpty);
    expect(refreshed.refreshToken, isNotEmpty);
    expect(refreshed.user.id, first.user.id);
  });

  test('wrong credentials return the generic Vietnamese message', () async {
    await expectLater(
      ContractClient().auth.login(randomUnknownPhone(), 'Sai@12345'),
      throwsA(
        apiError('BAD_CREDENTIALS')
            .having((e) => e.status, 'status', 401)
            .having(
              (e) => e.message,
              'message',
              'Số điện thoại hoặc mật khẩu không đúng',
            ),
      ),
    );
  });

  test('API call without a token is UNAUTHORIZED', () async {
    await expectLater(
      ContractClient().auth.fetchCurrentUser(),
      throwsA(apiError(ApiErrorModel.unauthorized)),
    );
  });

  group('change password errors (no data change)', () {
    test('mismatched confirmation is reported on confirmPassword', () async {
      final client = ContractClient();
      await client.auth.login('0900000004', kSeedPassword);

      await expectLater(
        client.auth.changePassword(
          currentPassword: kSeedPassword,
          newPassword: 'Moi@123456',
          confirmPassword: 'Khac@123456',
        ),
        throwsA(apiError('BUSINESS_ERROR', field: 'confirmPassword')),
      );
    });

    test('wrong current password is reported on currentPassword', () async {
      final client = ContractClient();
      await client.auth.login('0900000004', kSeedPassword);

      await expectLater(
        client.auth.changePassword(
          currentPassword: 'Sai@123456',
          newPassword: 'Moi@123456',
          confirmPassword: 'Moi@123456',
        ),
        throwsA(
          apiError('BUSINESS_ERROR', field: 'currentPassword').having(
            (e) => e.fieldErrors['currentPassword'],
            'message',
            'Mật khẩu hiện tại không đúng',
          ),
        ),
      );

      // Mật khẩu mẫu vẫn dùng được: không có gì bị đổi.
      await client.auth.login('0900000004', kSeedPassword);
    });
  });

  test('logout returns 204 and clears the stored tokens', () async {
    final client = ContractClient();
    await client.auth.login('0900000003', kSeedPassword);

    await client.api.post('/auth/logout');
    await client.auth.logout();

    expect(client.storage.accessToken, isNull);
    expect(client.storage.refreshToken, isNull);
  });
}
