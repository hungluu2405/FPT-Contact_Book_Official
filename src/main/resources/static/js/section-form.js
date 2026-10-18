// Form lớp học phần: chỉ hiện giảng viên thuộc bộ môn của môn học đang chọn.
// Server vẫn kiểm tra lại; script chỉ giúp chọn đúng nhanh hơn.
(function () {
    var subject = document.querySelector('[data-subject-select]');
    var teacher = document.querySelector('[data-teacher-select]');
    if (!subject || !teacher) {
        return;
    }

    function refresh() {
        var option = subject.options[subject.selectedIndex];
        var department = option ? option.getAttribute('data-department') : null;
        Array.prototype.forEach.call(teacher.options, function (item) {
            var group = item.getAttribute('data-department');
            item.hidden = !!(group && department && group !== department);
        });
        var selected = teacher.options[teacher.selectedIndex];
        if (selected && selected.hidden) {
            teacher.value = '';
        }
    }

    subject.addEventListener('change', refresh);
    refresh();
})();
