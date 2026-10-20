// Form đơn nghỉ: tự điền tiêu đề "Đơn xin nghỉ học từ dd/MM/yyyy đến dd/MM/yyyy" theo ngày đã chọn, cho tới khi
// người dùng tự sửa tiêu đề. Không có JavaScript thì để trống tiêu đề, server tự điền cùng nội dung.
(function () {
    var title = document.querySelector('[data-leave-title]');
    var from = document.getElementById('fromDate');
    var to = document.getElementById('toDate');
    if (!title || !from || !to) {
        return;
    }

    // Ô ngày là ô chữ dd/mm/yyyy (nhận cả d/m/yyyy); trả '' khi chưa nhập đủ để không gợi ý tiêu đề sai.
    function format(value) {
        var match = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(value.trim());
        if (!match) {
            return '';
        }
        return ('0' + match[1]).slice(-2) + '/' + ('0' + match[2]).slice(-2) + '/' + match[3];
    }

    function suggestion() {
        var start = format(from.value);
        var end = format(to.value);
        return start && end ? 'Đơn xin nghỉ học từ ' + start + ' đến ' + end : '';
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
    from.addEventListener('input', refresh);
    to.addEventListener('input', refresh);
    refresh();
})();
