package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.ParentLinkForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Parent;
import vn.edu.fpt.contactbook.model.ParentStudent;
import vn.edu.fpt.contactbook.model.ParentStudentId;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.enums.ParentRelationship;
import vn.edu.fpt.contactbook.repository.ParentRepository;
import vn.edu.fpt.contactbook.repository.ParentStudentRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.PhoneUtils;

import java.util.Locale;

/**
 * Liên kết phụ huynh – sinh viên (SRS FR-USER-08). parent_student dùng parents.id và students.id,
 * không phải users.id. Mỗi sinh viên có tối đa một phụ huynh chính.
 */
@Service
@RequiredArgsConstructor
public class ParentLinkService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final UserAccessPolicy accessPolicy;
    private final AuditService auditService;

    /** Từ trang của sinh viên: thêm phụ huynh theo số điện thoại. */
    @Transactional
    public void linkParent(AppUserPrincipal actor, Long studentUserId, ParentLinkForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        Student student = studentOf(studentUserId);
        String phone = PhoneUtils.normalize(form.getOtherKey())
                .orElseThrow(() -> new BusinessException(UserMessages.PHONE_INVALID, "otherKey"));
        Parent parent = userRepository.findByPhone(phone)
                .flatMap(user -> parentRepository.findByUserId(user.getId()))
                .orElseThrow(() -> new BusinessException(
                        "Không tìm thấy phụ huynh có số điện thoại này (người dùng cần có hồ sơ phụ huynh)",
                        "otherKey"));
        link(actor, parent, student, form, ipAddress);
    }

    /** Từ trang của phụ huynh: thêm con theo mã sinh viên. */
    @Transactional
    public void linkChild(AppUserPrincipal actor, Long parentUserId, ParentLinkForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        Parent parent = parentOf(parentUserId);
        String code = form.getOtherKey() == null ? "" : form.getOtherKey().strip().toUpperCase(Locale.ROOT);
        Student student = studentRepository.findByStudentCode(code)
                .orElseThrow(() -> new BusinessException("Không tìm thấy học sinh có mã này", "otherKey"));
        link(actor, parent, student, form, ipAddress);
    }

    /** Từ trang của sinh viên: gỡ một phụ huynh (parents.id). */
    @Transactional
    public void unlinkParent(AppUserPrincipal actor, Long studentUserId, Long parentId, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        unlink(actor, findLink(parentId, studentOf(studentUserId).getId()), ipAddress);
    }

    /** Từ trang của phụ huynh: gỡ một con (students.id). */
    @Transactional
    public void unlinkChild(AppUserPrincipal actor, Long parentUserId, Long studentId, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        unlink(actor, findLink(parentOf(parentUserId).getId(), studentId), ipAddress);
    }

    private void unlink(AppUserPrincipal actor, ParentStudent link, String ipAddress) {
        Long studentUserId = link.getStudent().getUser().getId();
        String detail = detail(link);
        parentStudentRepository.delete(link);
        auditService.log(actor.getUserId(), AuditActions.UNLINK_PARENT, AuditActions.ENTITY_USER, studentUserId,
                detail, ipAddress);
    }

    /** Từ trang của sinh viên: đặt phụ huynh chính; phụ huynh chính cũ bị bỏ cờ. */
    @Transactional
    public void setPrimaryParent(AppUserPrincipal actor, Long studentUserId, Long parentId, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        Long studentId = studentOf(studentUserId).getId();
        ParentStudent link = findLink(parentId, studentId);
        clearPrimary(studentId);
        link.setPrimary(true);
        auditService.log(actor.getUserId(), AuditActions.LINK_PARENT, AuditActions.ENTITY_USER,
                link.getStudent().getUser().getId(), detail(link), ipAddress);
    }

    /** Sinh viên ứng với user, hoặc lỗi nếu người dùng chưa có hồ sơ sinh viên. */
    private Student studentOf(Long studentUserId) {
        return studentRepository.findByUserId(studentUserId)
                .orElseThrow(() -> new BusinessException("Người dùng này chưa có hồ sơ học sinh"));
    }

    private Parent parentOf(Long parentUserId) {
        return parentRepository.findByUserId(parentUserId)
                .orElseThrow(() -> new BusinessException("Người dùng này chưa có hồ sơ phụ huynh"));
    }

    private void link(AppUserPrincipal actor, Parent parent, Student student, ParentLinkForm form,
                      String ipAddress) {
        if (parentStudentRepository.existsById(new ParentStudentId(parent.getId(), student.getId()))) {
            throw new BusinessException(UserMessages.LINK_EXISTS, "otherKey");
        }
        if (form.isPrimary()) {
            clearPrimary(student.getId());
        }
        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationship(form.getRelationship() == null ? ParentRelationship.OTHER : form.getRelationship());
        link.setPrimary(form.isPrimary());
        try {
            parentStudentRepository.saveAndFlush(link);
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }
        auditService.log(actor.getUserId(), AuditActions.LINK_PARENT, AuditActions.ENTITY_USER,
                student.getUser().getId(), detail(link), ipAddress);
    }

    /** Khóa dòng sinh viên trước khi bỏ cờ, để hai thao tác đặt phụ huynh chính không chạy chồng nhau. */
    private void clearPrimary(Long studentId) {
        studentRepository.findByIdForUpdate(studentId);
        parentStudentRepository.findByIdStudentId(studentId).forEach(existing -> existing.setPrimary(false));
    }

    private ParentStudent findLink(Long parentId, Long studentId) {
        return parentStudentRepository.findById(new ParentStudentId(parentId, studentId))
                .orElseThrow(() -> new NotFoundException("Không tìm thấy liên kết"));
    }

    private static String detail(ParentStudent link) {
        return "parentUserId=" + link.getParent().getUser().getId()
                + ",studentUserId=" + link.getStudent().getUser().getId()
                + ",relationship=" + link.getRelationship()
                + ",primary=" + link.isPrimary();
    }
}
