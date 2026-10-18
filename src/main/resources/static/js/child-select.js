// Bộ chọn con của phụ huynh: đổi con thì mở trang cùng loại (lịch hoặc lịch thi) của con đó.
// Server vẫn kiểm tra con đã liên kết; script chỉ chuyển trang.
(function () {
    var select = document.querySelector('[data-child-select]');
    if (!select) {
        return;
    }
    select.addEventListener('change', function () {
        var template = select.getAttribute('data-url-template');
        window.location.href = template.replace('{id}', encodeURIComponent(select.value));
    });
})();
