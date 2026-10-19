// Bảng điểm danh: nút "Tất cả có mặt" chọn PRESENT cho mọi sinh viên. Server vẫn kiểm tra đủ trạng thái khi lưu.
(function () {
    var button = document.querySelector('[data-all-present]');
    if (!button) {
        return;
    }
    button.addEventListener('click', function () {
        document.querySelectorAll('input[type="radio"][value="PRESENT"]').forEach(function (radio) {
            radio.checked = true;
        });
    });
})();
