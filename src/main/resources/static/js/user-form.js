// Form người dùng: hiện khối hồ sơ theo vai trò đang chọn và lọc lớp theo ngành.
// Server vẫn kiểm tra lại mọi trường; script chỉ giúp form gọn hơn.
(function () {
    var sections = document.querySelectorAll('[data-role-section]');
    var checkboxes = document.querySelectorAll('[data-role-toggle]');
    var roleSelect = document.querySelector('[data-role-select]');

    function selectedRoles() {
        var roles = [];
        checkboxes.forEach(function (box) {
            if (box.checked) {
                roles.push(box.value);
            }
        });
        if (roleSelect && roleSelect.value) {
            roles.push(roleSelect.value);
        }
        return roles;
    }

    function refreshSections() {
        var roles = selectedRoles();
        sections.forEach(function (section) {
            var wanted = section.getAttribute('data-role-section').split(' ');
            var show = wanted.some(function (role) {
                return roles.indexOf(role) >= 0;
            });
            section.hidden = !show;
        });
    }

    function filterClasses() {
        var major = document.querySelector('[data-major-select]');
        var classSelect = document.getElementById('profile.classId');
        if (!major || !classSelect) {
            return;
        }
        Array.prototype.forEach.call(classSelect.options, function (option) {
            var classMajor = option.getAttribute('data-major');
            option.hidden = classMajor !== null && major.value !== '' && classMajor !== major.value;
        });
    }

    checkboxes.forEach(function (box) {
        box.addEventListener('change', refreshSections);
    });
    if (roleSelect) {
        roleSelect.addEventListener('change', refreshSections);
    }
    var majorSelect = document.querySelector('[data-major-select]');
    if (majorSelect) {
        majorSelect.addEventListener('change', filterClasses);
    }
    if (checkboxes.length || roleSelect) {
        refreshSections();
    }
    filterClasses();
})();
