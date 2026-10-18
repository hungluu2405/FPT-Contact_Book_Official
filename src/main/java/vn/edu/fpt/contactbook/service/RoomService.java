package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.CatalogFilter;
import vn.edu.fpt.contactbook.dto.RoomForm;
import vn.edu.fpt.contactbook.dto.RoomView;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Room;
import vn.edu.fpt.contactbook.repository.RoomRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.LikePatterns;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Danh mục phòng học (SRS FR-ORG-07). Chỉ ADMIN. Không có chức năng xóa.
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    /** capacity là SMALLINT; ck_rooms_capacity yêu cầu > 0. */
    static final int MAX_CAPACITY = Short.MAX_VALUE;

    private final RoomRepository roomRepository;
    private final UserAccessPolicy accessPolicy;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<RoomView> list(AppUserPrincipal actor, CatalogFilter filter, int page) {
        accessPolicy.requireAdmin(actor);
        return roomRepository.search(LikePatterns.containsIgnoreCase(filter.getKeyword()), CatalogSupport.page(page))
                .map(RoomService::toView);
    }

    @Transactional(readOnly = true)
    public RoomView detail(AppUserPrincipal actor, Long id) {
        accessPolicy.requireAdmin(actor);
        return toView(find(id));
    }

    @Transactional(readOnly = true)
    public RoomForm editForm(AppUserPrincipal actor, Long id) {
        accessPolicy.requireAdmin(actor);
        Room room = find(id);
        RoomForm form = new RoomForm();
        form.setCode(room.getCode());
        form.setName(room.getName());
        form.setBuilding(room.getBuilding());
        form.setCapacity(room.getCapacity() == null ? null : room.getCapacity().intValue());
        return form;
    }

    @Transactional
    public Long create(AppUserPrincipal actor, RoomForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        Room room = apply(new Room(), form);
        save(room);
        auditService.log(actor.getUserId(), AuditActions.CREATE_ROOM, AuditActions.ENTITY_ROOM, room.getId(),
                "code=" + room.getCode(), ipAddress);
        return room.getId();
    }

    @Transactional
    public void update(AppUserPrincipal actor, Long id, RoomForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        Room room = apply(find(id), form);
        save(room);
        auditService.log(actor.getUserId(), AuditActions.UPDATE_ROOM, AuditActions.ENTITY_ROOM, id,
                "code=" + room.getCode(), ipAddress);
    }

    private Room apply(Room room, RoomForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        String code = CatalogSupport.requireCode(form.getCode(), errors);
        String name = CatalogSupport.requireText(form.getName(), 100, "name", "Vui lòng nhập tên",
                "Tên tối đa 100 ký tự", errors);
        String building = CatalogSupport.optionalText(form.getBuilding(), 100, "building",
                "Tòa nhà tối đa 100 ký tự", errors);
        Integer capacity = form.getCapacity();
        if (capacity != null && (capacity < 1 || capacity > MAX_CAPACITY)) {
            errors.put("capacity", "Sức chứa phải từ 1 đến " + MAX_CAPACITY);
        }
        if (code != null && (room.getId() == null ? roomRepository.existsByCode(code)
                : roomRepository.existsByCodeAndIdNot(code, room.getId()))) {
            errors.putIfAbsent("code", CatalogMessages.ROOM_CODE_TAKEN);
        }
        CatalogSupport.throwIfAny(errors);
        room.setCode(code);
        room.setName(name);
        room.setBuilding(building);
        room.setCapacity(capacity == null ? null : capacity.shortValue());
        return room;
    }

    private void save(Room room) {
        try {
            roomRepository.saveAndFlush(room);
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }
    }

    private Room find(Long id) {
        return roomRepository.findById(id).orElseThrow(() -> new NotFoundException(CatalogMessages.ROOM_NOT_FOUND));
    }

    private static RoomView toView(Room room) {
        return new RoomView(room.getId(), room.getCode(), room.getName(), room.getBuilding(), room.getCapacity());
    }
}
