import 'package:dio/dio.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/leave_option_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/page_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/picked_file_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/request_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/attachment_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/leave_rule.dart';

/// API đơn từ của HS ([studentId] null) hoặc của con PH đang xem
/// (docs/API.md mục "Học sinh và phụ huynh"). Lỗi ném [ApiErrorModel].
class RequestService {
  final ApiClient _apiClient;

  RequestService(this._apiClient);

  static String _root(int? studentId) =>
      '${StudentService.rootPath(studentId)}/requests';

  /// Mọi loại đơn, mới nhất trước; [page] đếm từ 0.
  Future<PageModel<RequestModel>> fetchRequests(
    int? studentId, {
    int page = 0,
  }) async {
    final data = await _apiClient.get(_root(studentId), query: {'page': page});
    return PageModel.fromJson(
      data as Map<String, dynamic>,
      RequestModel.fromJson,
    );
  }

  Future<RequestDetailModel> fetchRequest(int? studentId, int id) async {
    final data = await _apiClient.get('${_root(studentId)}/$id');
    return RequestDetailModel.fromJson(data as Map<String, dynamic>);
  }

  Future<LeaveOptionsModel> fetchLeaveOptions(int? studentId) async {
    final data = await _apiClient.get('${_root(studentId)}/leave-options');
    return LeaveOptionsModel.fromJson(data as Map<String, dynamic>);
  }

  /// Gửi đơn nghỉ kèm tệp minh chứng (đã qua [LeaveRule.fileError]); trả
  /// chi tiết đơn vừa tạo.
  Future<RequestDetailModel> submitLeave(
    int? studentId,
    LeaveFormModel form,
    List<PickedFileModel> files,
  ) async {
    final title = form.title.trim();
    final from = form.fromDate;
    final to = form.toDate;
    final data = FormData.fromMap({
      'fromDate': ?(from == null ? null : toApiDate(from)),
      'toDate': ?(to == null ? null : toApiDate(to)),
      'reason': form.reason.trim(),
      'title': ?(title.isEmpty ? null : title),
      'sectionId': ?form.sectionId,
      'files': [for (final file in files) multipartFileOf(file)],
    });
    final response = await _apiClient.post(
      '${_root(studentId)}/leave',
      data: data,
    );
    return RequestDetailModel.fromJson(response as Map<String, dynamic>);
  }

  /// Hủy đơn đang chờ duyệt, trả chi tiết sau khi hủy.
  Future<RequestDetailModel> cancel(int? studentId, int id) async {
    final data = await _apiClient.post('${_root(studentId)}/$id/cancel');
    return RequestDetailModel.fromJson(data as Map<String, dynamic>);
  }

  /// Đường dẫn tải tệp đính kèm của đơn, dùng với `AttachmentService`.
  static String filePath(int requestId, int fileId) =>
      '/requests/$requestId/files/$fileId';
}
