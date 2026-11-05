# Khóa MT5 – hướng dẫn build và cài

App nhỏ: một nút khóa MT5, thời gian chọn được bằng nút − / + (5 phút, 15 phút, 30 phút, 1, 2, 4, 8, 12 hoặc 24 giờ, mặc định 15 phút). Bấm xong, mỗi lần mở MetaTrader 5 là bị đẩy về màn hình chính cho tới khi hết giờ. Không có nút dừng. Tắt app, vuốt khỏi đa nhiệm hay khởi động lại máy vẫn còn khóa.

## 1. Build APK bằng GitHub (miễn phí, không cần cài gì)

1. Vào github.com, tạo tài khoản và tạo một repository mới (ví dụ `khoa-mt5`, chọn **Private**).
2. Bấm **Add file → Upload files**, kéo **toàn bộ nội dung** thư mục `khoa-mt5` vào (nhớ cả thư mục `.github`), bấm **Commit changes**.
   - Nếu thư mục `.github` không lên: bấm **Add file → Create new file**, gõ tên `.github/workflows/build.yml` và dán nội dung file `build.yml`.
3. Vào tab **Actions**, đợi workflow **Build APK** chạy xong (khoảng 3–6 phút, có dấu tick xanh). Nếu nó chưa tự chạy thì bấm **Build APK → Run workflow**.
4. Bấm vào lần chạy đó, kéo xuống mục **Artifacts**, tải **KhoaMT5-apk**. Giải nén ra được `app-debug.apk`.

## 2. Cài lên điện thoại Android

1. Chép `app-debug.apk` sang điện thoại (cáp USB hoặc tải thẳng trên điện thoại), mở file và cho phép cài từ nguồn này.
2. Mở app **Khóa MT5**, bấm **Bật quyền Trợ năng**, chọn **Khóa MT5** và bật công tắc.
3. Nếu công tắc bị mờ (Android 13 trở lên với app cài ngoài Play Store): **Cài đặt → Ứng dụng → Khóa MT5 → dấu ⋮ góc trên → Cho phép cài đặt bị hạn chế**, rồi bật lại công tắc Trợ năng.
4. Một số máy (Xiaomi, Oppo, Realme, Vivo) cần thêm: Pin → Khóa MT5 → **Không hạn chế**, và cho phép **tự khởi động**, nếu không hệ thống có thể tắt dịch vụ.

## 3. Thử trước khi dùng thật

Chọn mức **5 phút** bằng nút − / +, bấm khóa, thoát ra, mở MT5. Nếu MT5 vừa hiện đã bị đưa về màn hình chính thì đã chạy đúng.

## Giới hạn cần biết

- Tắt quyền Trợ năng hoặc gỡ app thì mở lại được MT5. Muốn chắc hơn, nhờ người thân đặt mật khẩu/khóa các cài đặt này.
- Chỉnh đồng hồ điện thoại tiến lên có thể làm hết khóa sớm. Chỉnh lùi thì không có lợi.
- Khóa từ 1 giờ trở lên thì app hỏi xác nhận một lần. Đã bắt đầu thì không dừng được.
- App chỉ chặn MT5 trên điện thoại này. Đăng nhập tài khoản từ máy tính hay web thì không bị chặn.
- Khóa app không đóng lệnh đang mở. Mỗi lệnh phải có SL.
- Nếu MT5 của bạn có tên gói khác `net.metaquotes.metatrader5` (hiếm), sửa ở `LockStore.kt` và `accessibility_service_config.xml`.
