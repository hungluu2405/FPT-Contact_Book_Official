// Nút ẩn/hiện mật khẩu: <button data-toggle-password="#idCuaInput">.
document.addEventListener('click', function (event) {
    var button = event.target.closest('[data-toggle-password]');
    if (!button) {
        return;
    }
    var input = document.querySelector(button.getAttribute('data-toggle-password'));
    if (!input) {
        return;
    }
    var show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    var icon = button.querySelector('i');
    if (icon) {
        icon.className = show ? 'bi bi-eye-slash' : 'bi bi-eye';
    }
});
