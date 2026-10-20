import 'package:flutter_test/flutter_test.dart';

import 'package:myfschoolse1945/main.dart';

void main() {
  testWidgets('App starts on the login screen', (WidgetTester tester) async {
    await tester.pumpWidget(const MyApp());

    expect(find.text('Đăng nhập'), findsWidgets);
  });
}
