import 'package:flutter_test/flutter_test.dart';

import '../test_contract/contract_config.dart';

void main() {
  test('defaults to the local backend', () {
    expect(contractApiRoot({}), 'http://localhost:8080/api/v1');
  });

  test('accepts localhost and 127.0.0.1 with any port', () {
    expect(
      contractApiRoot({'API_BASE_URL': 'http://127.0.0.1:9090/'}),
      'http://127.0.0.1:9090/api/v1',
    );
    expect(
      contractApiRoot({'API_BASE_URL': 'https://localhost'}),
      'https://localhost/api/v1',
    );
  });

  test('rejects any other host', () {
    for (final url in [
      'http://10.0.2.2:8080',
      'http://192.168.1.10:8080',
      'https://contactbook.example.com',
      'http://localhost.example.com',
      'ftp://localhost',
      'not a url',
    ]) {
      expect(
        () => contractApiRoot({'API_BASE_URL': url}),
        throwsStateError,
        reason: url,
      );
    }
  });
}
