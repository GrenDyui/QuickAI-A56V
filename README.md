# Quick AI A56

Android app optimized for Samsung Galaxy A56 / One UI: enter questions directly from the notification shade using Android Direct Reply, send them to Gemini API, and receive a compact answer back in the same notification.

## Requirements
- Android 7.0+ (minSdk 24)
- Samsung Galaxy A56 is supported.
- Android notification permission must be granted on Android 13+.
- Gemini API key.

## Build without Android Studio
This repository is designed for GitHub Actions. Push the project to GitHub, then open **Actions → Build Android APK**. The workflow installs JDK 17, Android SDK 35, Gradle 8.9, builds `assembleDebug`, and uploads the APK as an artifact.

## App flow
1. Open Quick AI.
2. Enter Gemini API key and model (default `gemini-3.8-flash`).
3. Press **LƯU & BẬT TRỢ LÝ**.
4. Pull down the notification shade.
5. Tap **Nhập câu hỏi**.
6. Paste the question.
7. Send. WorkManager calls Gemini in the background and updates the same notification with the answer.

## Security
The API key is encrypted locally using Android Keystore. Do not commit API keys into GitHub.

## Notes
The notification uses Android's Direct Reply / RemoteInput. This is the supported Android mechanism for direct text input from a notification; Samsung's One UI decides the exact visual layout.

## Troubleshooting

### Notification không hiện trên Samsung One UI (A56)
Nguyên nhân: kênh thông báo cũ được tạo với `IMPORTANCE_LOW` → One UI gom vào mục "Thông báo im lặng" bị thu gọn. Hơn nữa Android **khóa mức importance sau khi kênh đã tạo**, nên chỉ sửa code không đủ.

Bản 1.1.0 đã: chuyển sang `IMPORTANCE_DEFAULT`, đổi `CHANNEL_ID` sang `quick_ai_channel_v2` (kênh mới nhận importance mới), và tự xoá kênh cũ.

Sau khi cài bản mới, nếu vẫn không thấy:
1. **Cài đặt → Ứng dụng → Quick AI → Thông báo** → bật "Hiển thị thông báo" và đảm bảo kênh "Quick AI" bật ở mức Mặc định.
2. Vuốt thanh thông báo xuống → kéo tới tận cùng phần "Thông báo im lặng" để kiểm tra không bị ẩn.
3. Tắt "Tự động tối ưu hóa" cho Quick AI: **Chăm sóc thiết bị → Tự động tối ưu hóa** (One UI có thể ngủ app, làm notification biến mất).

### Gemini HTTP 503 (Service Unavailable)
503 là lỗi tạm thời do máy chủ Gemini quá tải ("High Demand"), **không phải do sai model hay API key** (model `gemini-3.8-flash` là model ổn định mới, hợp lệ).

Bản 1.1.0 đã thêm **retry exponential backoff** (thử tối đa 5 lần, delay 1s→2s→4s→8s + jitter) ngay trong `AiClient`, áp dụng cho cả nút TEST lẫn worker nền. Nút TEST giờ sẽ tự thử lại thay vì báo lỗi ngay ở lần 503 đầu tiên.

Nếu vẫn bị 503 liên tục:
- Thử model khác ít tải hơn, ví dụ `gemini-3.5-flash-lite` (ô Model trong app).
- Kiểm tra key chưa bị block/leak ở Google AI Studio.
- Với free tier, hạn mức thấp → 503 dễ xuất hiện lúc cao điểm, chỉ cần thử lại sau.
