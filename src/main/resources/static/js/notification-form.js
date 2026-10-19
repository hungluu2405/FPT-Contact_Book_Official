// Form gửi thông báo: chỉ hiện (và chỉ gửi) ô chọn nơi nhận của phạm vi đang chọn.
// Không có JavaScript thì mọi ô đều hiện; server chỉ đọc ô của phạm vi đã chọn và kiểm tra lại quyền.
(function () {
    var select = document.querySelector('[data-target-type-select]');
    if (!select) {
        return;
    }
    var types = ['ROLE', 'DEPARTMENT', 'CLASS', 'CLASS_SECTION', 'USER'];
    function update() {
        types.forEach(function (type) {
            document.querySelectorAll('.target-' + type).forEach(function (box) {
                var active = select.value === type;
                box.classList.toggle('d-none', !active);
                box.querySelectorAll('select, input').forEach(function (field) {
                    field.disabled = !active;
                });
            });
        });
    }
    select.addEventListener('change', update);
    update();
})();
