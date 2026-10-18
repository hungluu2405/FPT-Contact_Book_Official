package vn.edu.fpt.contactbook.service;

import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.Map;
import java.util.Set;

/**
 * Dữ liệu dựng sẵn cho unit test các service danh mục.
 */
final class CatalogTestData {

    private CatalogTestData() {
    }

    static AppUserPrincipal principal(Long id, Set<String> roles, Map<String, Set<Long>> departments) {
        return new AppUserPrincipal(id, "09000000" + id, "Người " + id, null, true, false, roles, departments);
    }

    static Department department(Long id, String code) {
        Department department = new Department();
        department.setId(id);
        department.setCode(code);
        department.setName("Bộ môn " + code);
        return department;
    }

    static Subject subject(Long id, String code, Department department) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setCode(code);
        subject.setName("Môn " + code);
        subject.setCredits((short) 3);
        subject.setDepartment(department);
        return subject;
    }
}
