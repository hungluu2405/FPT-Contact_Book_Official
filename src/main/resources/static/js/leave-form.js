// Form đơn nghỉ: tự điền tiêu đề "Đơn xin nghỉ học từ dd/MM/yyyy đến dd/MM/yyyy" theo ngày đã chọn, cho tới khi
// người dùng tự sửa tiêu đề. Không có JavaScript thì để trống tiêu đề, server tự điền cùng nội dung.
(function () {
    var title = document.querySelector('[data-leave-title]');
    var from = document.getElementById('fromDate');
    var to = document.getElementById('toDate');
    if (!title || !from || !to) {
        return;
    }

    function format(value) {
        var parts = value.split('-');
        return parts.length === 3 ? parts[2] + '/' + parts[1] + '/' + parts[0] : '';
    }

    function suggestion() {
        return from.value && to.value ? 'Đơn xin nghỉ học từ ' + format(from.value) + ' đến ' + format(to.value) : '';
    }

    var auto = title.value === '' || title.value === suggestion();
    function refresh() {
        if (auto) {
            title.value = suggestion();
        }
    }

    title.addEventListener('input', function () {
        auto = title.value === '';
    });
    from.addEventListener('change', refresh);
    to.addEventListener('change', refresh);
    refresh();
})();
