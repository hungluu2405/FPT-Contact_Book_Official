// Khung chat tin nhắn: cuộn xuống tin mới nhất, tự lấy tin mới mỗi 10 giây (mảnh HTML do máy chủ render, nội dung
// đã được escape bằng th:text), và tải tin cũ hơn tại chỗ. Tắt JavaScript thì vẫn dùng được bằng nút "Tải lại" và
// link "Tải tin cũ hơn".
(function () {
    var box = document.getElementById('chat-messages');
    if (!box) {
        return;
    }
    var POLL_MS = 10000;

    function scrollToBottom() {
        box.scrollTop = box.scrollHeight;
    }

    function nearBottom() {
        return box.scrollHeight - box.scrollTop - box.clientHeight < 80;
    }

    // Phiên hết hạn thì máy chủ chuyển hướng tới trang đăng nhập: không chèn trang đó vào khung chat.
    function fetchFragment(url) {
        return fetch(url, {credentials: 'same-origin', headers: {'Accept': 'text/html'}}).then(function (response) {
            if (!response.ok || response.redirected) {
                throw new Error('fragment unavailable');
            }
            return response.text();
        });
    }

    scrollToBottom();

    var newerUrl = box.getAttribute('data-newer-url');
    var polling = false;
    if (newerUrl) {
        var timer = window.setInterval(function () {
            if (document.hidden || polling) {
                return;
            }
            polling = true;
            var stick = nearBottom();
            var url = newerUrl + '?after=' + encodeURIComponent(box.getAttribute('data-after') || '0');
            fetchFragment(url).then(function (html) {
                if (html.trim() === '') {
                    return;
                }
                var empty = box.querySelector('.chat-empty');
                if (empty) {
                    empty.remove();
                }
                box.insertAdjacentHTML('beforeend', html);
                var items = box.querySelectorAll('.chat-message[data-id]');
                box.setAttribute('data-after', items[items.length - 1].getAttribute('data-id'));
                if (stick) {
                    scrollToBottom();
                }
            }).catch(function () {
                window.clearInterval(timer);
            }).finally(function () {
                polling = false;
            });
        }, POLL_MS);
    }

    box.addEventListener('click', function (event) {
        var link = event.target.closest('.js-load-older');
        if (!link) {
            return;
        }
        event.preventDefault();
        var holder = link.closest('.chat-older');
        var previousHeight = box.scrollHeight;
        fetchFragment(link.getAttribute('data-fragment-url')).then(function (html) {
            holder.insertAdjacentHTML('afterend', html);
            holder.remove();
            box.scrollTop += box.scrollHeight - previousHeight;
        }).catch(function () {
            window.location.href = link.href;
        });
    });
})();
