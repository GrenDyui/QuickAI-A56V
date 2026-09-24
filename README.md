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
2. Enter Gemini API key and model (default `gemini-3.5-flash-lite`).
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
503 là lỗi tạm thời do máy chủ Gemini quá tải ("High Demand"), **không phải do sai model hay API key** (các model dùng trong app đều là model ổn định, hợp lệ theo [ai.google.dev/gemini-api/docs/models](https://ai.google.dev/gemini-api/docs/models)).

Bản 1.2.0 xử lý 503 triệt để trong code:
- **Model mặc định** đổi sang `gemini-3.5-flash-lite` — ổn định, ít tải hơn `gemini-3.8-flash` (vốn mới ra, hay bị "High Demand"), đủ thông minh cho bài trắc nghiệm A/B/C/D.
- **Retry exponential backoff** mỗi model: tối đa 3 lần, delay 1s→2s + jitter (theo đúng khuyến nghị của Google: chỉ retry 429/5xx, backoff, jitter, có giới hạn).
- **Fallback qua nhiều model**: nếu model đang dùng vẫn 503 sau khi đã retry, tự thử model dự phòng (`gemini-3.5-flash`, `gemini-3.6-flash`). Lỗi key sai (400/403) hoặc sai tên model (404) thì **không** fallback — báo luôn để bạn sửa.

Áp dụng cho cả nút TEST lẫn worker nền (WorkManager). Nút TEST giờ sẽ tự thử lại + đổi model thay vì báo lỗi ngay ở lần 503 đầu tiên.

Nếu vẫn bị 503 liên tục (thường là do free tier bị throttle lúc cao điểm):
- Bật billing (lên paid tier) ở project Google AI Studio — free tier bị giới hạn RPM thấp nhất, dễ 503 nhất.
- Kiểm tra key chưa bị block/leak ở [Google AI Studio](https://ai.google.dev/gemini-api/docs/api-keys).
- Vẫn có thể gõ model khác (vd `gemini-3.8-flash`) vào ô Model; app sẽ thử model đó trước rồi mới dùng dự phòng.
