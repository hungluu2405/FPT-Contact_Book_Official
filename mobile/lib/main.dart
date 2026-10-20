import 'package:flutter/material.dart';
import 'package:myfschoolse1945/vn/edu/fpt/screen/account/login_screen.dart';

const Color fptNavy = Color(0xFF0A1930); // nền header + card phía trên
const Color fptOrange = Color(0xFFF97316); // nút, chữ cam
const Color fptBackground = Color(0xFFF8F7F4); // nền trang
const Color fptBlue = Color(0xFF0066B3);
const Color fptGreen = Color(0xFF00A651);

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Sổ liên lạc điện tử FPT',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        scaffoldBackgroundColor: fptBackground,
        colorScheme: ColorScheme.fromSeed(
          seedColor: fptOrange,
          primary: fptOrange,
          secondary: fptNavy,
        ),
      ),
      home: const LoginScreen(),
    );
  }
}