import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/auth_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/exam_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/home_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/password_reset_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/profile_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/schedule_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/change_password_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/login_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/home/home_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/home/splash_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/auth_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/notification_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/profile_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/token_storage.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_config.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  final tokenStorage = SecureTokenStorage();
  final apiClient = ApiClient(
    baseUrl: AppConfig.apiRoot,
    tokenStorage: tokenStorage,
  );
  final authService = AuthService(apiClient, tokenStorage);
  final studentService = StudentService(apiClient);
  final authController = AuthController(authService)..restoreSession();
  runApp(
    MyApp(
      authController: authController,
      passwordResetController: PasswordResetController(authService),
      homeController: HomeController(
        studentService,
        NotificationService(apiClient),
      ),
      profileController: ProfileController(ProfileService(apiClient)),
      scheduleController: ScheduleController(studentService),
      examController: ExamController(studentService),
    ),
  );
}

class MyApp extends StatelessWidget {
  final AuthController authController;
  final PasswordResetController passwordResetController;
  final HomeController homeController;
  final ProfileController profileController;
  final ScheduleController scheduleController;
  final ExamController examController;

  const MyApp({
    super.key,
    required this.authController,
    required this.passwordResetController,
    required this.homeController,
    required this.profileController,
    required this.scheduleController,
    required this.examController,
  });

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Sổ liên lạc điện tử FPT',
      debugShowCheckedModeBanner: false,
      theme: buildAppTheme(),
      // Màn hình gốc đổi theo trạng thái phiên đăng nhập.
      home: ListenableBuilder(
        listenable: authController,
        builder: (context, _) => switch (authController.status) {
          AuthStatus.unknown => SplashScreen(controller: authController),
          AuthStatus.unauthenticated => LoginScreen(
            controller: authController,
            passwordResetController: passwordResetController,
          ),
          AuthStatus.mustChangePassword => ChangePasswordScreen(
            controller: authController,
          ),
          AuthStatus.authenticated => HomeScreen(
            controller: authController,
            homeController: homeController,
            profileController: profileController,
            scheduleController: scheduleController,
            examController: examController,
          ),
        },
      ),
    );
  }
}
