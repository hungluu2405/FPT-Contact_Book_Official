package vn.edu.fpt.contactbook.dto;

/**
 * Định dạng ngày giờ của các ô nhập trên form (CLAUDE.md mục 6): ô chữ dd/MM/yyyy và dd/MM/yyyy HH:mm thay cho ô
 * date/datetime-local của trình duyệt (định dạng của chúng phụ thuộc ngôn ngữ máy). Các mẫu phụ (fallback) nhận cả
 * ngày, giờ một chữ số (d/M/yyyy, H:mm) và dạng ISO cũ, để link, URL và form gửi theo kiểu cũ vẫn chạy.
 */
public final class DateFormats {

    public static final String DATE = "dd/MM/yyyy";
    public static final String DATE_SHORT = "d/M/yyyy";
    public static final String ISO_DATE = "yyyy-MM-dd";

    public static final String DATE_TIME = "dd/MM/yyyy HH:mm";
    public static final String DATE_TIME_SHORT = "d/M/yyyy H:mm";
    public static final String ISO_DATE_TIME = "yyyy-MM-dd'T'HH:mm";

    public static final String TIME = "HH:mm";
    public static final String TIME_SHORT = "H:mm";

    private DateFormats() {
    }
}
