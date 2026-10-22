import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/auth_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/auth_widget.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/widget/error_retry_widget.dart';

/// Màn hình lúc mở app, trong khi khôi phục phiên đăng nhập.
/// Lỗi mạng thì hiện nút thử lại, token vẫn giữ.
class SplashScreen extends StatelessWidget {
  final AuthController controller;

  const SplashScreen({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: ListenableBuilder(
          listenable: controller,
          builder: (context, _) {
            final error = controller.startupError;
            if (error != null) {
              return ErrorRetryWidget(
                message: error.message,
                onRetry: controller.restoreSession,
              );
            }
            return Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Image.asset('assets/images/fpt_schools_logo.png', height: 72),
                  const SizedBox(height: 16),
                  const ThreeColorStripe(),
                  const SizedBox(height: 32),
                  const CircularProgressIndicator(),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}
