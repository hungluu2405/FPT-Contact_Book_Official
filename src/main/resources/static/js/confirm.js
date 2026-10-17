// Hộp thoại xác nhận cho thao tác khó hoàn tác (khóa, gỡ, đặt lại mật khẩu): <form data-confirm="Câu hỏi">.
document.addEventListener('submit', function (event) {
    var form = event.target;
    var question = form.getAttribute && form.getAttribute('data-confirm');
    if (question && !window.confirm(question)) {
        event.preventDefault();
    }
});
