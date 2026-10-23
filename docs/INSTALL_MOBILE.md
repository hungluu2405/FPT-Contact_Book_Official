# Hướng dẫn cài đặt app Android và cấu hình server

Tài liệu cho giai đoạn 11. App Android (`mobile/`) không có database riêng, mọi dữ liệu đi qua REST API `/api/v1/**`
của backend Spring Boot (cùng database `FptContactBook` với web). Phần cài database và chạy web xem `README.md`.

Có hai loại APK:

| Loại | Dùng khi | Kết nối server |
|---|---|---|
| **Release** | Cài cho người dùng thật | Chỉ HTTPS. Build bị từ chối nếu `API_BASE_URL` không bắt đầu bằng `https://` |
| **Demo LAN** | Trình diễn trong một mạng LAN, server là máy dev chưa có HTTPS | HTTP thường tới **đúng một** địa chỉ IPv4 nội bộ ghi lúc build. Không dùng để phát hành |

Địa chỉ server được ghi vào APK lúc build (giả định 150). **Đổi server thì phải build lại APK.**

---

## 1. Chuẩn bị máy build

- Flutter stable (đã thử với 3.47.2) và Android SDK (Android Studio hoặc command-line tools). Kiểm tra bằng `flutter doctor`.
- JDK để có `keytool` (JDK 21 của backend dùng được).
- Chạy trong thư mục `mobile/`:
  ```bash
  flutter pub get
  ```

## 2. Cấu hình backend cho app

Ngoài các biến `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` ở `README.md` mục 3, khi cho điện thoại kết nối cần đặt thêm:

| Biến môi trường | Bắt buộc | Ghi chú |
|---|---|---|
| `JWT_SECRET` | **Có** | Chuỗi ngẫu nhiên tối thiểu 32 byte. Để trống thì backend tự sinh khóa mỗi lần khởi động và mọi điện thoại phải đăng nhập lại (giả định 129) |
| `SPRINGDOC_APIDOCS_ENABLED` | Nên đặt `false` | Tắt `/v3/api-docs` khi backend mở ra ngoài localhost (giả định 133); Spring đọc thành `springdoc.api-docs.enabled` |
| `SPRINGDOC_SWAGGERUI_ENABLED` | Nên đặt `false` | Tắt `/swagger-ui.html` (`springdoc.swagger-ui.enabled`) |
| `MAIL_*` | Khi dùng quên mật khẩu | Xem giả định 156. Dữ liệu mẫu có email thật nên đừng trỏ tới SMTP thật khi còn dữ liệu mẫu |

Sinh `JWT_SECRET` trên PowerShell (không in ra màn hình, đặt cho phiên hiện tại):
```powershell
$env:JWT_SECRET = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])
```

Backend mặc định nghe trên mọi địa chỉ của máy ở cổng 8080. Khi mở cho mạng LAN, **web và API đều truy cập được từ
mọi máy trong mạng đó**: chỉ làm trong mạng tin cậy, đổi mật khẩu các tài khoản mẫu trước khi trình diễn trước người
khác, và chỉ mở đúng cổng 8080 trên Windows Firewall cho mạng Private:
```powershell
New-NetFirewallRule -DisplayName "FPT Contact Book 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Profile Private -Action Allow
```
Gỡ sau khi trình diễn: `Remove-NetFirewallRule -DisplayName "FPT Contact Book 8080"`.

### HTTPS cho bản release
Spring Boot không cần đổi code: đặt backend sau một reverse proxy có chứng chỉ hợp lệ (ví dụ IIS, Nginx, Caddy) và
trỏ tên miền về proxy. Điện thoại chỉ tin chứng chỉ của CA công khai; **chứng chỉ tự ký không dùng được** vì app
không cài thêm CA riêng. Đăng nhập sai bị khóa tạm theo số điện thoại, không theo IP, nên proxy không làm sai bộ đếm;
nhật ký đăng nhập sẽ ghi IP của proxy (giả định 14).

## 3. Tạo khóa ký release (làm một lần)

Khóa ký quyết định ai được cập nhật app trên máy người dùng: **mất khóa là không cập nhật được app đã cài**, lộ khóa
là người khác ký được bản giả. Giữ file `.jks` và mật khẩu ngoài git, có bản sao lưu.

1. Tạo keystore ngoài repo (ví dụ `C:\Project\keystore`), `keytool` sẽ hỏi mật khẩu:
   ```powershell
   keytool -genkeypair -v -keystore C:\Project\keystore\fpt-contact-book-release.jks -alias fpt-contact-book -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Chép `mobile/android/key.properties.example` thành `mobile/android/key.properties` (đã có trong `.gitignore`) và
   điền mật khẩu. `storeFile` tính từ thư mục `mobile/android/`; ví dụ trên ứng với `../../../keystore/fpt-contact-book-release.jks`.

Thiếu `key.properties` hoặc thiếu một trong bốn khóa thì build release dừng với thông báo lỗi, không còn ký bằng khóa debug.

## 4. Build APK release (HTTPS)

Trong `mobile/`:
```bash
flutter build apk --release --dart-define=API_BASE_URL=https://lienlac.truong.edu.vn
```
Kết quả: `mobile/build/app/outputs/flutter-apk/app-release.apk`. Kiểm tra chữ ký (đường dẫn build-tools theo máy):
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.1.0\apksigner.bat" verify --print-certs build\app\outputs\flutter-apk\app-release.apk
```
Dòng `Signer #1 certificate DN` phải là thông tin bạn nhập ở bước 3, không phải `CN=Android Debug`.

## 5. Build APK demo LAN (HTTP)

1. Lấy IPv4 của máy chạy backend trong mạng LAN (`ipconfig`, dòng IPv4 Address, ví dụ `192.168.1.20`). Chỉ nhận địa
   chỉ nội bộ `10.x`, `172.16–31.x`, `192.168.x`.
2. Trong `mobile/`:
   ```powershell
   .\tool\build_demo_apk.ps1 -HostIp 192.168.1.20 -Port 8080
   ```
   Script truyền cùng một địa chỉ cho Dart (`API_BASE_URL`, `DEMO_CLEARTEXT_HOST`) và Gradle (`demoCleartextHost`);
   Gradle sinh cấu hình mạng chỉ cho phép HTTP tới đúng địa chỉ đó. Kết quả:
   `mobile/build/app/outputs/flutter-apk/app-demo-lan.apk`, phiên bản có hậu tố `-demo-lan`.
3. Máy ảo Android gọi máy dev qua `10.0.2.2`, nên muốn thử bản demo trên máy ảo thì dùng `-HostIp 10.0.2.2`.

IP của máy đổi (đổi mạng Wi-Fi, DHCP cấp lại) thì phải build lại bản demo.

## 6. Cài APK lên điện thoại

- **Qua USB**: bật Tùy chọn nhà phát triển → Gỡ lỗi USB, rồi:
  ```bash
  adb install -r build/app/outputs/flutter-apk/app-release.apk
  ```
- **Chép file**: gửi APK sang điện thoại, mở file và cho phép "Cài ứng dụng không rõ nguồn gốc" cho trình quản lý tệp.
- Bản release và bản demo cùng applicationId `vn.edu.fpt.contactbook`, ký cùng khóa, nên cài bản này đè bản kia được.
  Bản cài trước đó bằng `flutter run` (khóa debug) phải gỡ trước: `adb uninstall vn.edu.fpt.contactbook`.

## 7. Kiểm tra sau khi cài

1. Mở app, đăng nhập bằng một tài khoản (dữ liệu mẫu: `CLAUDE.md` mục 10, chỉ dùng cho dev/test).
2. Báo "Không kết nối được máy chủ": kiểm tra điện thoại cùng mạng, gõ `http://<IP>:8080/` (bản demo) hoặc địa chỉ
   HTTPS trên trình duyệt điện thoại, kiểm tra firewall.
3. Mọi lần đăng nhập ghi vào nhật ký hệ thống; xem ở web `/admin/audit-logs`.

## 8. Phát triển (bản debug)

`flutter run` không cần khóa ký và mặc định gọi `http://10.0.2.2:8080` (backend trên máy dev, xem từ máy ảo). Bản
debug được dùng HTTP thường tới mọi địa chỉ (`android/app/src/debug/res/xml/network_security_config.xml`).
