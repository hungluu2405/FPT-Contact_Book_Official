import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_config.dart';

void main() {
  String resolve(String defined, {bool debug = false, String demoHost = ''}) =>
      AppConfig.resolveBaseUrl(
        defined: defined,
        debug: debug,
        demoCleartextHost: demoHost,
      );

  group('debug build', () {
    test('defaults to the emulator host when API_BASE_URL is empty', () {
      expect(resolve('', debug: true), AppConfig.debugBaseUrl);
    });

    test('accepts plain HTTP', () {
      expect(
        resolve('http://192.168.1.20:8080', debug: true),
        'http://192.168.1.20:8080',
      );
    });

    test('rejects a value that is not an absolute URL', () {
      expect(() => resolve('localhost:8080', debug: true), throwsStateError);
    });
  });

  group('release build', () {
    test('requires API_BASE_URL', () {
      expect(() => resolve(''), throwsStateError);
    });

    test('accepts HTTPS and drops the trailing slash', () {
      expect(
        resolve('https://lienlac.example.edu.vn/'),
        'https://lienlac.example.edu.vn',
      );
    });

    test('rejects plain HTTP without a demo host', () {
      expect(() => resolve('http://192.168.1.20:8080'), throwsStateError);
    });

    test('accepts plain HTTP to the demo host only', () {
      expect(
        resolve('http://192.168.1.20:8080', demoHost: '192.168.1.20'),
        'http://192.168.1.20:8080',
      );
      expect(
        () => resolve('http://192.168.1.21:8080', demoHost: '192.168.1.20'),
        throwsStateError,
      );
    });

    test('rejects other schemes and URLs without a host', () {
      expect(() => resolve('ftp://192.168.1.20'), throwsStateError);
      expect(() => resolve('https://'), throwsStateError);
    });
  });
}
