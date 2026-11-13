# Block – hướng dẫn

App tự khóa các app làm bạn mất kiểm soát (MetaTrader 5 và bất kỳ app nào bạn chọn).

## Dùng hằng ngày

- Chọn thời gian bằng chip: **15 phút**, **1 giờ**, **Hết ngày** (khóa tới 0 giờ), hoặc **Khác…** (1 phút để thử, 5, 30 phút, 2, 4, 8, 12, 24 giờ).
- Bấm **Chọn** ở mục "App bị khóa" để tick thêm app.
- Bấm nút đỏ **KHÓA**. Mở app trong danh sách là bị che màn hình, hiện vòng đếm ngược và lời nhắc của bạn, rồi đẩy về màn hình chính.
- Không có nút dừng. Tắt app, vuốt khỏi đa nhiệm hay khởi động lại máy vẫn còn khóa.
- **Lời nhắc**: bấm Sửa để viết câu bạn muốn đọc lúc đang nóng đầu. Câu này hiện trên màn hình khóa.
- **Chuỗi ngày kỷ luật**: cuối ngày bấm "Đã giữ đúng luật" hoặc "Đã phá luật". Chuỗi tính số ngày giữ luật liên tiếp.
- **Nhật ký**: mỗi lần khóa được ghi giờ, thời lượng, app. Chạm vào để ghi vì sao bạn khóa (khi đang khóa cũng có nút ghi nhanh).

## Giao diện

Vào **Cài đặt → Giao diện**:
- **Chế độ nền**: Tự động (theo hệ thống), Sáng (mặc định) hoặc Tối.
- **Màu nền**: *Theo trạng thái* (xanh khi sẵn sàng, cam khi đang khóa) hoặc *Chuyển màu từ từ* (màu trôi chậm qua cả dải màu).
- Khi mở app bị khóa, màn hình che có nút **×** nhỏ ở góc phải để thoát ngay. Không bấm thì tự thoát sau vài giây.

## Hai lớp chặn (độc lập, nên bật cả hai)

1. **Trợ năng**: hệ thống báo cho app biết khi một app khác mở lên.
2. **Giám sát nền**: dịch vụ có thông báo cố định, cứ 0,3 giây đọc xem app nào đang ở trên cùng (cần quyền Truy cập sử dụng) và che màn hình bằng lớp phủ (cần quyền Hiện trên app khác).

Vào **Cài đặt** (góc trên bên phải) để cấp quyền và xem mỗi lớp đang bật hay tắt, vừa thấy app nào mở.
Chấm cam cạnh chữ Cài đặt nghĩa là còn quyền chưa cấp.

## Build APK bằng GitHub

Repository chỉ cần `.github/workflows/build.yml` (đã chứa sẵn toàn bộ mã app) cùng `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` ở thư mục ngoài cùng. Vào tab **Actions**, đợi **Build APK** chạy xong, tải **KhoaMT5-apk** ở mục Artifacts.

## Cài lên Android

1. Chép `app-debug.apk` sang điện thoại, mở file và cho phép cài.
   Nếu đã cài bản cũ thì gỡ bản cũ trước (mỗi lần GitHub build, chữ ký khác nhau nên không cài đè được).
2. Mở app, vào Cài đặt, cấp lần lượt các quyền. Mục nào xong sẽ chuyển xanh.
3. Công tắc Trợ năng bị mờ: **Cài đặt → Ứng dụng → Block → dấu ⋮ → Cho phép cài đặt bị hạn chế**, rồi bật lại.
4. Máy Xiaomi, Oppo, Realme, Vivo: cho phép **tự khởi động** và khóa thẻ app trong màn hình đa nhiệm.

## Thử trước khi dùng thật

Chọn **Khác… → 1 phút**, tick một app thử (ví dụ Máy tính), bấm khóa rồi mở app đó. Thấy màn hình "ĐANG KHÓA" là chạy đúng.
Rồi thử lại với MT5.

## Giới hạn

- App hệ thống thiết yếu (Cài đặt, Điện thoại/gọi khẩn cấp, màn hình chính) không bao giờ bị khóa, để khỏi kẹt.
- Tắt quyền hoặc gỡ app thì mở lại được app bị khóa. Muốn chắc hơn, nhờ người thân đặt mật khẩu các cài đặt này.
- Chỉnh đồng hồ điện thoại tiến lên có thể làm hết khóa sớm.
- Chỉ chặn trên điện thoại này, không chặn đăng nhập từ máy tính hay web.
- Khóa app không đóng lệnh MT5 đang mở. Mỗi lệnh phải có SL.
