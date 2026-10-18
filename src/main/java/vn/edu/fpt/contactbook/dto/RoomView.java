package vn.edu.fpt.contactbook.dto;

/**
 * Phòng học; capacity null là chưa xác định.
 */
public record RoomView(Long id, String code, String name, String building, Short capacity) {
}
