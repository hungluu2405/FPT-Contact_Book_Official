import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/attendance_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/attendance_sheet_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/auth_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/club_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/department_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/department_notification_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/exam_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/grade_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/home_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/notification_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/password_reset_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/profile_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/request_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/request_review_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/schedule_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/teacher_section_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/change_password_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/account/login_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/home/home_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/screen/home/splash_screen.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/attachment_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/auth_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/club_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/department_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/notification_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/profile_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/request_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/teacher_service.dart';
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
  final notificationService = NotificationService(apiClient);
  final attachmentService = AttachmentService(apiClient);
  final departmentService = DepartmentService(apiClient);
  final teacherService = TeacherService(apiClient);
  final authController = AuthController(authService)..restoreSession();
  runApp(
    MyApp(
      authController: authController,
      passwordResetController: PasswordResetController(authService),
      homeController: HomeController(studentService, notificationService),
      profileController: ProfileController(ProfileService(apiClient)),
      scheduleController: ScheduleController(studentService),
      examController: ExamController(studentService),
      gradeController: GradeController(studentService),
      attendanceController: AttendanceController(studentService),
      clubController: ClubController(ClubService(apiClient)),
      notificationController: NotificationController(
        notificationService,
        attachmentService,
      ),
      requestController: RequestController(
        RequestService(apiClient),
        attachmentService,
      ),
      departmentController: DepartmentController(departmentService),
      departmentNotificationController: DepartmentNotificationController(
        departmentService,
        notificationService,
        attachmentService,
      ),
      teacherSectionController: TeacherSectionController(
        teacherService,
        studentService,
      ),
      attendanceSheetController: AttendanceSheetController(teacherService),
      requestReviewController: RequestReviewController(
        teacherService,
        attachmentService,
      ),
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
  final GradeController gradeController;
  final AttendanceController attendanceController;
  final ClubController clubController;
  final NotificationController notificationController;
  final RequestController requestController;
  final DepartmentController departmentController;
  final DepartmentNotificationController departmentNotificationController;
  final TeacherSectionController teacherSectionController;
  final AttendanceSheetController attendanceSheetController;
  final RequestReviewController requestReviewController;

  const MyApp({
    super.key,
    required this.authController,
    required this.passwordResetController,
    required this.homeController,
    required this.profileController,
    required this.scheduleController,
    required this.examController,
    required this.gradeController,
    required this.attendanceController,
    required this.clubController,
    required this.notificationController,
    required this.requestController,
    required this.departmentController,
    required this.departmentNotificationController,
    required this.teacherSectionController,
    required this.attendanceSheetController,
    required this.requestReviewController,
  });

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Sổ liên lạc điện tử FPT',
      debugShowCheckedModeBanner: false,
      theme: buildAppTheme(),
      // Lịch chọn ngày và chữ của Material bằng tiếng Việt, tuần bắt đầu
      // từ thứ Hai.
      locale: const Locale('vi'),
      supportedLocales: const [Locale('vi')],
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
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
            gradeController: gradeController,
            attendanceController: attendanceController,
            clubController: clubController,
            notificationController: notificationController,
            requestController: requestController,
            departmentController: departmentController,
            departmentNotificationController: departmentNotificationController,
            teacherSectionController: teacherSectionController,
            attendanceSheetController: attendanceSheetController,
            requestReviewController: requestReviewController,
          ),
        },
      ),
    );
  }
}
