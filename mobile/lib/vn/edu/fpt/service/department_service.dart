import 'package:dio/dio.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/department_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/page_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/picked_file_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/sent_notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/teacher_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/attachment_service.dart';

/// API của tổ trưởng chuyên môn (docs/API.md mục "Tổ trưởng chuyên môn").
/// Lỗi ném [ApiErrorModel].
class DepartmentService {
  final ApiClient _apiClient;

  DepartmentService(this._apiClient);

  /// Các tổ mình làm tổ trưởng, kèm số giáo viên.
  Future<List<DepartmentModel>> fetchDepartments() async {
    final data = await _apiClient.get('/head/departments');
    return (data as List<dynamic>)
        .map((item) => DepartmentModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  /// Giáo viên của tổ [departmentId] (null: mọi tổ mình phụ trách), [query]
  /// tìm theo tên, mã, số điện thoại; [page] đếm từ 0.
  Future<PageModel<TeacherModel>> fetchTeachers({
    int? departmentId,
    String? query,
    int page = 0,
  }) async {
    final keyword = query?.trim() ?? '';
    final data = await _apiClient.get(
      '/head/teachers',
      query: {
        'departmentId': ?departmentId,
        'q': ?(keyword.isEmpty ? null : keyword),
        'page': page,
      },
    );
    return PageModel.fromJson(
      data as Map<String, dynamic>,
      TeacherModel.fromJson,
    );
  }

  Future<TeacherDetailModel> fetchTeacher(int userId) async {
    final data = await _apiClient.get('/head/teachers/$userId');
    return TeacherDetailModel.fromJson(data as Map<String, dynamic>);
  }

  /// Gửi thông báo nội bộ: [teacherUserId] khác null là gửi riêng giáo viên
  /// đó, ngược lại gửi cả tổ [departmentId]. Trả thông báo vừa gửi kèm thống
  /// kê.
  Future<SentNotificationDetailModel> sendNotification(
    DepartmentNotificationFormModel form, {
    int? departmentId,
    int? teacherUserId,
    List<PickedFileModel> files = const [],
  }) async {
    final data = FormData.fromMap({
      'title': form.title.trim(),
      'content': form.content.trim(),
      'priority': form.priority,
      if (teacherUserId != null)
        'teacherUserId': teacherUserId
      else
        'departmentId': ?departmentId,
      'files': [for (final file in files) multipartFileOf(file)],
    });
    final response = await _apiClient.post('/head/notifications', data: data);
    return SentNotificationDetailModel.fromJson(
      response as Map<String, dynamic>,
    );
  }
}
