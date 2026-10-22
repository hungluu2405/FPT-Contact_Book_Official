package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.AttachmentDownload;
import vn.edu.fpt.contactbook.dto.AttachmentUpload;
import vn.edu.fpt.contactbook.dto.LeaveFormOptions;
import vn.edu.fpt.contactbook.dto.LeaveRequestForm;
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.dto.OtherRequestForm;
import vn.edu.fpt.contactbook.dto.RequestDetail;
import vn.edu.fpt.contactbook.dto.RequestListItem;
import vn.edu.fpt.contactbook.dto.ReviewResult;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Attendance;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.RequestFile;
import vn.edu.fpt.contactbook.model.RequestReview;
import vn.edu.fpt.contactbook.model.StoredFile;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.UserRequest;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;
import vn.edu.fpt.contactbook.model.enums.RequestStatus;
import vn.edu.fpt.contactbook.model.enums.RequestType;
import vn.edu.fpt.contactbook.model.enums.ReviewDecision;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.AttendanceRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.NotificationAudienceRepository;
import vn.edu.fpt.contactbook.repository.ParentRepository;
import vn.edu.fpt.contactbook.repository.ParentStudentRepository;
import vn.edu.fpt.contactbook.repository.RequestFileRepository;
import vn.edu.fpt.contactbook.repository.RequestReviewRepository;
import vn.edu.fpt.contactbook.repository.RoleRepository;
import vn.edu.fpt.contactbook.repository.StoredFileRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRequestRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.RequestReviewerResolver.Reviewer;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Đơn từ (SRS FR-REQ-01..05, UC-05). Đơn nghỉ do sinh viên gửi hoặc phụ huynh gửi thay con đã liên kết; đơn khác
 * (bảng điểm, xác nhận sinh viên, khác) do sinh viên gửi. Tạo đơn báo cho người duyệt (RequestReviewerResolver).
 * Duyệt / từ chối / hủy chuyển trạng thái bằng một câu UPDATE có điều kiện PENDING, nên hai thao tác đồng thời không
 * cùng thành công. Duyệt đơn nghỉ chuyển các dòng vắng không phép trong khoảng ngày (đúng lớp nếu có) thành vắng có
 * phép trong cùng transaction, mỗi dòng một audit cũ → mới; người gửi và sinh viên nhận thông báo kết quả.
 */
@Service
@RequiredArgsConstructor
public class RequestService {

    /** Đường truy cập: trang sinh viên, trang con của phụ huynh, trang duyệt của giảng viên, trang duyệt của ADMIN. */
    public enum View { STUDENT, PARENT, TEACHER, ADMIN }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int TITLE_MAX = 200;
    private static final int REASON_MAX = 1000;
    private static final int COMMENT_MAX = 500;

    private final UserRequestRepository requestRepository;
    private final RequestReviewRepository reviewRepository;
    private final RequestFileRepository requestFileRepository;
    private final StoredFileRepository storedFileRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final NotificationAudienceRepository audienceRepository;
    private final ScheduleViewService scheduleViewService;
    private final RequestReviewerResolver reviewerResolver;
    private final AttachmentStorage attachmentStorage;
    private final NotificationService notificationService;
    private final AuditService auditService;

    /** Sinh viên liên quan và các lớp đang học (ô chọn lớp của đơn nghỉ). */
    @Transactional(readOnly = true)
    public LeaveFormOptions leaveOptions(AppUserPrincipal actor, View view, Long studentId) {
        Student student = student(actor, view, studentId);
        List<OptionView> sections = enrollmentRepository.findByStudentIdAndStatusOrderByEnrolledAtDesc(
                        student.getId(), EnrollmentStatus.ENROLLED).stream()
                .map(Enrollment::getClassSection)
                .map(s -> new OptionView(s.getId(), s.getCode(), s.getSubject().getCode() + " - "
                        + s.getSubject().getName() + " (" + s.getSemester().getCode() + ")", null))
                .toList();
        return new LeaveFormOptions(student.getId(), student.getStudentCode(), student.getUser().getFullName(),
                sections);
    }

    /** Đơn nghỉ của sinh viên (STUDENT) hoặc phụ huynh gửi thay con (PARENT); trả id đơn. */
    @Transactional
    public Long createLeave(AppUserPrincipal actor, View view, Long studentId, LeaveRequestForm form,
                            List<AttachmentUpload> files, String ipAddress) {
        Student student = student(actor, view, studentId);
        Map<String, String> errors = new LinkedHashMap<>();
        String title = CatalogSupport.optionalText(form.getTitle(), TITLE_MAX, "title", RequestMessages.TITLE_SIZE,
                errors);
        RequestRule.validateLeaveDates(form.getFromDate(), form.getToDate(), AppTime.today(), errors);
        ClassSection section = null;
        if (form.getSectionId() != null) {
            section = enrollmentRepository.findByClassSectionIdAndStudentId(form.getSectionId(), student.getId())
                    .filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
                    .map(Enrollment::getClassSection)
                    .orElse(null);
            if (section == null) {
                errors.putIfAbsent("sectionId", RequestMessages.SECTION_NOT_ENROLLED);
            }
        }
        String reason = CatalogSupport.requireText(form.getReason(), REASON_MAX, "reason",
                RequestMessages.REASON_REQUIRED, RequestMessages.REASON_SIZE, errors);
        CatalogSupport.throwIfAny(errors);
        attachmentStorage.validate(files);

        UserRequest request = new UserRequest();
        request.setRequestType(RequestType.LEAVE);
        request.setStudent(student);
        request.setClassSection(section);
        request.setTitle(title == null ? RequestRule.leaveTitle(form.getFromDate(), form.getToDate()) : title);
        request.setReason(reason);
        request.setFromDate(form.getFromDate());
        request.setToDate(form.getToDate());
        return persist(actor, request, files, ipAddress);
    }

    /** Đơn khác (bảng điểm, xác nhận sinh viên, khác) của chính sinh viên; trả id đơn. */
    @Transactional
    public Long createOther(AppUserPrincipal actor, OtherRequestForm form, List<AttachmentUpload> files,
                            String ipAddress) {
        Student student = student(actor, View.STUDENT, null);
        Map<String, String> errors = new LinkedHashMap<>();
        if (form.getRequestType() == null) {
            errors.putIfAbsent("requestType", RequestMessages.TYPE_REQUIRED);
        } else if (form.getRequestType() == RequestType.LEAVE) {
            errors.putIfAbsent("requestType", RequestMessages.TYPE_INVALID);
        }
        String title = CatalogSupport.requireText(form.getTitle(), TITLE_MAX, "title",
                RequestMessages.TITLE_REQUIRED, RequestMessages.TITLE_SIZE, errors);
        String reason = CatalogSupport.requireText(form.getReason(), REASON_MAX, "reason",
                RequestMessages.REASON_REQUIRED, RequestMessages.REASON_SIZE, errors);
        CatalogSupport.throwIfAny(errors);
        attachmentStorage.validate(files);

        UserRequest request = new UserRequest();
        request.setRequestType(form.getRequestType());
        request.setStudent(student);
        request.setTitle(title);
        request.setReason(reason);
        return persist(actor, request, files, ipAddress);
    }

    /** Đơn của sinh viên (kể cả đơn phụ huynh gửi thay), mới trước. */
    @Transactional(readOnly = true)
    public Page<RequestListItem> studentRequests(AppUserPrincipal actor, View view, Long studentId, int page) {
        Student student = student(actor, view, studentId);
        return requestRepository.findPageByStudent(student.getId(), CatalogSupport.page(page))
                .map(RequestService::toItem);
    }

    /**
     * Đơn người đăng nhập phải duyệt, lọc theo trạng thái (null là tất cả). TEACHER: theo hồ sơ giảng viên, chưa có
     * hồ sơ thì danh sách trống; ADMIN: phần thuộc ADMIN.
     */
    @Transactional(readOnly = true)
    public Page<RequestListItem> reviewQueue(AppUserPrincipal actor, View view, RequestStatus status, int page) {
        Pageable pageable = CatalogSupport.page(page);
        Page<UserRequest> requests = switch (view) {
            case TEACHER -> teacherRepository.findByUserId(actor.getUserId())
                    .map(t -> requestRepository.findTeacherQueue(t.getId(), status, pageable))
                    .orElse(Page.empty(pageable));
            case ADMIN -> {
                requireAdmin(actor);
                yield requestRepository.findAdminQueue(status, pageable);
            }
            case STUDENT, PARENT -> throw new AccessDeniedException("Not a reviewer view");
        };
        return requests.map(RequestService::toItem);
    }

    /** Số đơn chờ giảng viên này duyệt (0 nếu không có hồ sơ giảng viên). */
    @Transactional(readOnly = true)
    public long pendingForTeacher(AppUserPrincipal actor) {
        return teacherRepository.findByUserId(actor.getUserId())
                .map(t -> requestRepository.countTeacherQueue(t.getId(), RequestStatus.PENDING))
                .orElse(0L);
    }

    /** Số đơn chờ ADMIN xử lý. */
    @Transactional(readOnly = true)
    public long pendingForAdmin() {
        return requestRepository.countAdminQueue(RequestStatus.PENDING);
    }

    /**
     * Chi tiết theo đường truy cập: STUDENT đơn của chính mình; PARENT đơn của con đã liên kết (studentId phải khớp);
     * TEACHER chỉ đơn mình là người duyệt; ADMIN mọi đơn. Không thỏa (kể cả id không tồn tại) → 403.
     */
    @Transactional(readOnly = true)
    public RequestDetail detail(AppUserPrincipal actor, View view, Long studentId, Long requestId) {
        UserRequest request = requestRepository.findDetailById(requestId)
                .orElseThrow(() -> new AccessDeniedException("Request not visible"));
        Reviewer reviewer = reviewerResolver.resolve(request);
        switch (view) {
            case STUDENT, PARENT -> {
                Student student = student(actor, view, studentId);
                if (request.getStudent() == null || !Objects.equals(request.getStudent().getId(), student.getId())) {
                    throw new AccessDeniedException("Request not visible");
                }
            }
            case TEACHER -> {
                if (reviewer.admin() || !isUser(reviewer.teacher(), actor)) {
                    throw new AccessDeniedException("Not the reviewer of this request");
                }
            }
            case ADMIN -> requireAdmin(actor);
        }
        boolean pending = request.getStatus() == RequestStatus.PENDING;
        boolean canReview = pending && (view == View.TEACHER || view == View.ADMIN)
                && reviewerResolver.isReviewer(actor, request);
        Student student = request.getStudent();
        ClassSection section = request.getClassSection();
        List<RequestDetail.FileItem> files = requestFileRepository.findByIdRequestIdOrderByIdFileIdAsc(requestId)
                .stream()
                .map(rf -> new RequestDetail.FileItem(rf.getFile().getId(), rf.getFile().getFileName(),
                        rf.getFile().getSizeBytes()))
                .toList();
        List<RequestDetail.ReviewItem> reviews = reviewRepository.findByRequestIdOrderByReviewedAtAscIdAsc(requestId)
                .stream()
                .map(r -> new RequestDetail.ReviewItem(r.getReviewer().getFullName(), r.getDecision(),
                        r.getComment(), r.getReviewedAt()))
                .toList();
        return new RequestDetail(request.getId(), request.getRequestType(), request.getTitle(), request.getReason(),
                student == null ? null : student.getStudentCode(),
                student == null ? null : student.getUser().getFullName(), request.getRequester().getFullName(),
                student != null && !Objects.equals(student.getUser().getId(), request.getRequester().getId()),
                section == null ? null : section.getCode() + " - " + section.getSubject().getName(),
                request.getFromDate(), request.getToDate(), request.getStatus(), request.getCreatedAt(),
                request.getUpdatedAt(), reviewer.admin() ? null : reviewer.teacher().getUser().getFullName(), files,
                reviews, pending && Objects.equals(request.getRequester().getId(), actor.getUserId()), canReview);
    }

    /** Người gửi hủy đơn còn chờ duyệt; người khác → 403; đơn đã xử lý hoặc đã hủy → lỗi nghiệp vụ. */
    @Transactional
    public void cancel(AppUserPrincipal actor, Long requestId, String ipAddress) {
        UserRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new AccessDeniedException("Request not visible"));
        if (!Objects.equals(request.getRequester().getId(), actor.getUserId())) {
            throw new AccessDeniedException("Only the requester can cancel");
        }
        if (requestRepository.transitionFromPending(requestId, RequestStatus.CANCELLED, AppTime.now()) == 0) {
            throw new BusinessException(RequestMessages.NOT_PENDING);
        }
        auditService.log(actor.getUserId(), AuditActions.CANCEL_REQUEST, AuditActions.ENTITY_REQUEST, requestId,
                "status=PENDING -> CANCELLED", ipAddress);
    }

    /**
     * Duyệt hoặc từ chối (ghi chú bắt buộc khi từ chối). Chỉ người duyệt của đơn, qua đúng trang (TEACHER: giảng viên
     * của đơn; ADMIN: phần thuộc ADMIN), người khác → 403. Đơn không còn PENDING lúc ghi → lỗi nghiệp vụ.
     */
    @Transactional
    public ReviewResult review(AppUserPrincipal actor, View view, Long requestId, ReviewDecision decision,
                               String rawComment, String ipAddress) {
        UserRequest request = requestRepository.findDetailById(requestId)
                .orElseThrow(() -> new AccessDeniedException("Request not visible"));
        requireReviewer(actor, view, request);
        if (decision == null) {
            throw new BusinessException(RequestMessages.DECISION_REQUIRED);
        }
        String comment = CatalogSupport.trimToNull(rawComment);
        if (comment == null && decision == ReviewDecision.REJECTED) {
            throw new BusinessException(RequestMessages.COMMENT_REQUIRED, "comment");
        }
        if (comment != null && comment.length() > COMMENT_MAX) {
            throw new BusinessException(RequestMessages.COMMENT_SIZE, "comment");
        }
        RequestStatus newStatus = decision == ReviewDecision.APPROVED ? RequestStatus.APPROVED
                : RequestStatus.REJECTED;
        if (requestRepository.transitionFromPending(requestId, newStatus, AppTime.now()) == 0) {
            throw new BusinessException(RequestMessages.NOT_PENDING);
        }
        // Câu UPDATE đã xóa persistence context: đọc lại đơn ở trạng thái mới.
        request = requestRepository.findDetailById(requestId).orElseThrow();

        RequestReview review = new RequestReview();
        review.setRequest(request);
        review.setReviewer(userRepository.getReferenceById(actor.getUserId()));
        review.setDecision(decision);
        review.setComment(comment);
        reviewRepository.save(review);

        int excused = decision == ReviewDecision.APPROVED && request.getRequestType() == RequestType.LEAVE
                ? excuseAbsences(actor, request, ipAddress) : 0;
        auditService.log(actor.getUserId(), decision == ReviewDecision.APPROVED ? AuditActions.APPROVE_REQUEST
                        : AuditActions.REJECT_REQUEST, AuditActions.ENTITY_REQUEST, requestId,
                "status=PENDING -> " + newStatus + ", type=" + request.getRequestType() + studentDetail(request)
                        + ", excused=" + excused + (comment == null ? "" : ", comment=" + comment), ipAddress);
        notifyResult(actor, request, decision, comment, excused);
        return new ReviewResult(decision, excused);
    }

    /**
     * Tệp của đơn: người gửi, sinh viên liên quan, phụ huynh đã liên kết, người duyệt của đơn và ADMIN; người khác
     * (kể cả tệp không thuộc đơn) → 403.
     */
    @Transactional(readOnly = true)
    public AttachmentDownload download(AppUserPrincipal actor, Long requestId, Long fileId) {
        RequestFile link = requestFileRepository.findByIdRequestIdAndIdFileId(requestId, fileId)
                .orElseThrow(() -> new AccessDeniedException("Attachment not visible"));
        UserRequest request = requestRepository.findDetailById(requestId)
                .orElseThrow(() -> new AccessDeniedException("Attachment not visible"));
        if (!canSeeFiles(actor, request)) {
            throw new AccessDeniedException("Attachment not visible");
        }
        StoredFile file = link.getFile();
        return attachmentStorage.resolve(file.getFileUrl())
                .map(path -> new AttachmentDownload(file.getFileName(), file.getContentType(), path))
                .orElseThrow(() -> new NotFoundException(RequestMessages.FILE_NOT_FOUND));
    }

    private boolean canSeeFiles(AppUserPrincipal actor, UserRequest request) {
        if (actor.hasRole(RoleCodes.ADMIN)
                || Objects.equals(request.getRequester().getId(), actor.getUserId())
                || reviewerResolver.isReviewer(actor, request)) {
            return true;
        }
        Student student = request.getStudent();
        if (student == null) {
            return false;
        }
        if (Objects.equals(student.getUser().getId(), actor.getUserId())) {
            return true;
        }
        return parentRepository.findByUserId(actor.getUserId())
                .map(p -> parentStudentRepository.existsByIdParentIdAndIdStudentId(p.getId(), student.getId()))
                .orElse(false);
    }

    /** Lưu đơn, tệp minh chứng, audit, rồi báo người duyệt (cùng transaction). */
    private Long persist(AppUserPrincipal actor, UserRequest request, List<AttachmentUpload> files,
                         String ipAddress) {
        User requester = userRepository.getReferenceById(actor.getUserId());
        request.setRequester(requester);
        requestRepository.save(request);
        for (AttachmentUpload upload : files) {
            AttachmentStorage.Stored stored = attachmentStorage.store(upload);
            StoredFile file = new StoredFile();
            file.setFileName(stored.fileName());
            file.setFileUrl(stored.key());
            file.setContentType(stored.contentType());
            file.setSizeBytes(stored.sizeBytes());
            file.setUploadedBy(requester);
            storedFileRepository.save(file);
            RequestFile link = new RequestFile();
            link.setRequest(request);
            link.setFile(file);
            requestFileRepository.save(link);
        }
        auditService.log(actor.getUserId(), AuditActions.CREATE_REQUEST, AuditActions.ENTITY_REQUEST,
                request.getId(), "type=" + request.getRequestType() + studentDetail(request)
                        + (request.getClassSection() == null ? "" : ", section=" + request.getClassSection().getCode())
                        + (request.getFromDate() == null ? "" : ", from=" + request.getFromDate() + ", to="
                        + request.getToDate()) + ", files=" + files.size(), ipAddress);
        notifyReviewer(actor, request);
        return request.getId();
    }

    /** Báo người duyệt có đơn mới: giảng viên (cá nhân) hoặc mọi ADMIN đang hoạt động. */
    private void notifyReviewer(AppUserPrincipal actor, UserRequest request) {
        Reviewer reviewer = reviewerResolver.resolve(request);
        NotificationTargetType type;
        Long targetId;
        Collection<Long> recipients;
        if (reviewer.admin()) {
            type = NotificationTargetType.ROLE;
            targetId = roleRepository.findByCode(RoleCodes.ADMIN).map(r -> r.getId()).orElse(null);
            recipients = targetId == null ? List.of() : audienceRepository.withRole(targetId);
        } else {
            type = NotificationTargetType.USER;
            targetId = reviewer.teacher().getUser().getId();
            recipients = audienceRepository.activeAmong(List.of(targetId));
        }
        Student student = request.getStudent();
        String content = request.getRequester().getFullName() + " đã gửi " + typeLabel(request.getRequestType())
                + " cho sinh viên " + student.getUser().getFullName() + " (" + student.getStudentCode() + ")"
                + (request.getClassSection() == null ? "" : ", lớp " + request.getClassSection().getCode())
                + (request.getFromDate() == null ? "" : ", nghỉ từ " + request.getFromDate().format(DATE) + " đến "
                + request.getToDate().format(DATE)) + ".\nLý do: " + request.getReason();
        notificationService.sendAutomatic(actor.getUserId(), "Đơn mới cần duyệt: " + request.getTitle(), content,
                NotificationPriority.NORMAL, type, targetId, recipients);
    }

    /** Báo kết quả cho người gửi và sinh viên liên quan (nếu phụ huynh gửi thay). */
    private void notifyResult(AppUserPrincipal actor, UserRequest request, ReviewDecision decision, String comment,
                              int excused) {
        Set<Long> users = new LinkedHashSet<>();
        users.add(request.getRequester().getId());
        Long studentUserId = request.getStudent() == null ? null : request.getStudent().getUser().getId();
        if (studentUserId != null) {
            users.add(studentUserId);
        }
        boolean approved = decision == ReviewDecision.APPROVED;
        String content = "Đơn \"" + request.getTitle() + "\" (" + typeLabel(request.getRequestType()) + ")"
                + studentDetailVi(request) + (approved ? " đã được duyệt." : " đã bị từ chối.")
                + (approved && request.getRequestType() == RequestType.LEAVE
                ? " Có " + excused + " buổi vắng không phép được chuyển thành vắng có phép." : "")
                + (comment == null ? "" : "\nGhi chú của người duyệt: " + comment);
        notificationService.sendAutomatic(actor.getUserId(),
                (approved ? "Đơn đã được duyệt: " : "Đơn bị từ chối: ") + request.getTitle(), content,
                approved ? NotificationPriority.NORMAL : NotificationPriority.IMPORTANT, NotificationTargetType.USER,
                studentUserId == null ? request.getRequester().getId() : studentUserId,
                audienceRepository.activeAmong(users));
    }

    /**
     * Chuyển các dòng vắng không phép của sinh viên trong khoảng ngày (đúng lớp nếu đơn có chọn lớp) thành vắng có
     * phép; tham chiếu đơn nối sau ghi chú của giáo viên (không đè, tối đa 255 ký tự; ghi chú cũ cũng giữ trong
     * audit); marked_by giữ nguyên. Không bị chặn bởi hạn điểm danh của giáo viên. Trả số dòng đã đổi.
     */
    private int excuseAbsences(AppUserPrincipal actor, UserRequest request, String ipAddress) {
        List<Attendance> rows = attendanceRepository.findUnexcusedInRange(request.getStudent().getId(),
                request.getFromDate(), request.getToDate(),
                request.getClassSection() == null ? null : request.getClassSection().getId());
        if (rows.isEmpty()) {
            return 0;
        }
        String reference = "Vắng có phép theo đơn #" + request.getId();
        List<String> oldNotes = new ArrayList<>(rows.size());
        for (Attendance row : rows) {
            oldNotes.add(row.getNote());
            row.setStatus(AttendanceStatus.ABSENT_EXCUSED);
            String teacherNote = CatalogSupport.trimToNull(row.getNote());
            String note = teacherNote == null ? reference : teacherNote + " · " + reference;
            row.setNote(note.length() > 255 ? note.substring(0, 255) : note);
        }
        attendanceRepository.saveAllAndFlush(rows);
        for (int i = 0; i < rows.size(); i++) {
            Attendance row = rows.get(i);
            String oldNote = oldNotes.get(i);
            auditService.log(actor.getUserId(), AuditActions.EXCUSE_ATTENDANCE, AuditActions.ENTITY_ATTENDANCE,
                    row.getId(), "request=" + request.getId() + ", section="
                            + row.getSession().getClassSection().getCode() + ", session="
                            + row.getSession().getSessionNo() + ", date=" + row.getSession().getSessionDate()
                            + ", student=" + request.getStudent().getStudentCode() + ", status="
                            + AttendanceStatus.ABSENT_UNEXCUSED + " -> " + AttendanceStatus.ABSENT_EXCUSED
                            + ", oldNote=" + (oldNote == null || oldNote.isEmpty() ? "-" : oldNote), ipAddress);
        }
        return rows.size();
    }

    /** Hồ sơ sinh viên actor được thao tác: STUDENT là chính mình, PARENT là con đã liên kết; khác → 403. */
    private Student student(AppUserPrincipal actor, View view, Long studentId) {
        Audience audience = switch (view) {
            case STUDENT -> Audience.STUDENT;
            case PARENT -> Audience.PARENT;
            case TEACHER, ADMIN -> throw new AccessDeniedException("Not a student view");
        };
        Long id = scheduleViewService.studentId(actor, audience, studentId);
        return studentRepository.findById(id).orElseThrow(() -> new AccessDeniedException("Student not found"));
    }

    private void requireReviewer(AppUserPrincipal actor, View view, UserRequest request) {
        Reviewer reviewer = reviewerResolver.resolve(request);
        boolean allowed = switch (view) {
            case TEACHER -> !reviewer.admin() && isUser(reviewer.teacher(), actor);
            case ADMIN -> reviewer.admin() && actor.hasRole(RoleCodes.ADMIN);
            case STUDENT, PARENT -> false;
        };
        if (!allowed) {
            throw new AccessDeniedException("Not the reviewer of this request");
        }
    }

    private static void requireAdmin(AppUserPrincipal actor) {
        if (!actor.hasRole(RoleCodes.ADMIN)) {
            throw new AccessDeniedException("Admin only");
        }
    }

    private static boolean isUser(Teacher teacher, AppUserPrincipal actor) {
        return Objects.equals(teacher.getUser().getId(), actor.getUserId());
    }

    private static String studentDetail(UserRequest request) {
        return request.getStudent() == null ? "" : ", student=" + request.getStudent().getStudentCode();
    }

    private static String studentDetailVi(UserRequest request) {
        Student student = request.getStudent();
        return student == null ? "" : " của sinh viên " + student.getUser().getFullName() + " ("
                + student.getStudentCode() + ")";
    }

    static String typeLabel(RequestType type) {
        return switch (type) {
            case LEAVE -> "đơn xin nghỉ học";
            case TRANSCRIPT -> "đơn xin bảng điểm";
            case CERTIFICATE -> "đơn xin xác nhận sinh viên";
            case OTHER -> "đơn khác";
        };
    }

    private static RequestListItem toItem(UserRequest r) {
        Student student = r.getStudent();
        return new RequestListItem(r.getId(), r.getRequestType(), r.getTitle(),
                student == null ? null : student.getStudentCode(),
                student == null ? null : student.getUser().getFullName(), r.getRequester().getFullName(),
                r.getClassSection() == null ? null : r.getClassSection().getCode(), r.getFromDate(), r.getToDate(),
                r.getStatus(), r.getCreatedAt());
    }
}
