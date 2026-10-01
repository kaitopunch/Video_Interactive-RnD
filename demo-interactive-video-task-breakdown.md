# Chia sub-task: Demo Interactive Video Gameplay

- **Yêu cầu gốc:** [README.md](README.md)
- **Các quyết định đã chốt:** [confirm.md](confirm.md) (các mã `Qn`, `Dn`, `En` bên dưới trỏ về file này)
- **Bản đồ code:** [LLM.md](LLM.md)
- **Ngày tạo:** 2026-09-29

## Cách đọc

- Số giờ là ước lượng thô cho 1 dev đã quen Compose và Media3. Chưa tính thời gian chờ BA.
- Mã `Fn` là các lỗi đã sửa, ghi ở `LLM.md` §11 "Fixed".
- Tên file và class trong bảng là tên thật trong `app/src/main/kotlin/com/pion/psremote/`.

## Tổng quan

| Nhóm | Nội dung | Số task | Giờ | Trạng thái |
|---|---|---|---|---|
| 1 | Phân tích và chốt yêu cầu | 10 | 12.5 | Đã có trong repo |
| 2 | Setup project và nền tảng | 12 | 15 | Đã có trong repo |
| 3 | Domain model | 7 | 5.5 | Đã có trong repo |
| 4 | Parser JSON | 9 | 7.5 | Đã có trong repo |
| 5 | Validator và use case | 11 | 8.5 | Đã có trong repo |
| 6 | Data layer và phát video | 12 | 12.5 | Đã có trong repo |
| 7 | Logic input: StepProgress | 6 | 5.5 | Đã có trong repo |
| 8 | MVI: Contract, ViewModel, Route | 17 | 20 | Đã có trong repo |
| 9 | UI: bộ điều khiển | 10 | 14 | Đã có trong repo |
| 10 | UI: tutorial overlay | 8 | 9 | Đã có trong repo |
| 11 | UI: màn hình và panel | 11 | 12.5 | Đã có trong repo |
| 12 | Dữ liệu demo mẫu | 2 | 1.5 | Đã có trong repo |
| 13 | Unit test | 8 | 15.5 | Đã có trong repo |
| 14 | Sửa lỗi phát hiện khi test | 5 | 8.5 | Đã có trong repo |
| 15 | QA thủ công trên máy thật | 10 | 9.5 | Chưa xác minh |
| 16 | Review, tài liệu, bàn giao | 4 | 4.5 | Chưa xác minh |
| **1–16** | | **142** | **162** (khoảng 20 ngày công) | |
| 17 | Còn lại và đang bị chặn | 14 | 15.5 (+8 nếu nhiều hơn 1 demo) | Đang chặn |

---

## 1. Phân tích và chốt yêu cầu (12.5h)

| # | Sub-task | Giờ |
|---|---|---|
| 1.1 | Đọc README, liệt kê chỗ spec tự mâu thuẫn (`targetButtonId`/`targetButtonIds`, `pauseTimeMs`/`slowDurationMs`, `> 0`/`≥ 0`, thiếu trường chế độ thao tác) | 2 |
| 1.2 | Soạn confirm.md: các câu hỏi A–D kèm đề xuất gửi BA | 3 |
| 1.3 | Chốt danh sách 18 button ID (Q2) | 0.5 |
| 1.4 | Chốt trường mới `inputMode` và 3 giá trị của nó (Q8) | 0.5 |
| 1.5 | Chốt hành vi khi bấm sai, bấm sớm, bấm nút lặp (Q9, D4) | 1 |
| 1.6 | Chốt kiểu "click đồng thời" và giới hạn 5 điểm chạm (Q10, Q11) | 1 |
| 1.7 | Chốt hành vi khi app xuống nền và dialog đếm ngược 5s (D6) | 0.5 |
| 1.8 | Ghi mục E (cách hiểu khi triển khai) để BA soát lại | 1 |
| 1.9 | Viết [docs/demo-script-format.md](docs/demo-script-format.md) cho BA | 2 |
| 1.10 | Phản hồi README §8: mức khả thi, estimate, đề xuất sửa JSON, ngày bàn giao | 1 |

## 2. Setup project và nền tảng (15h)

| # | Sub-task | Giờ |
|---|---|---|
| 2.1 | Tạo project Gradle và version catalog (AGP 9.2.1, Kotlin 2.3.21, compileSdk 37, targetSdk 36, minSdk 28) | 2 |
| 2.2 | Thêm dependency Compose, Media3 `exoplayer` và `ui-compose` | 1 |
| 2.3 | AndroidManifest: 1 Activity, khóa màn hình ngang, không xin permission | 0.5 |
| 2.4 | `MainActivity`: immersive, giữ màn hình sáng, tránh display cutout | 1.5 |
| 2.5 | Icon, tên app, `themes.xml` | 0.5 |
| 2.6 | `compose-stability.conf` | 0.5 |
| 2.7 | `core/common`: `AppError`, `AppResult`, `AppLogger` | 1 |
| 2.8 | `core/log`: `AndroidAppLogger` | 0.5 |
| 2.9 | `core/mvi`: `MviViewModel` (`launchSafely`), `CollectEffects` | 2 |
| 2.10 | `core/ui`: `PsRemoteTheme`, `PsColors`, `Spacing`, `TextSize` | 1.5 |
| 2.11 | Viết lại LLM.md cho dự án này | 3 |
| 2.12 | Viết AGENTS.md | 1 |

## 3. Domain model (5.5h)

| # | Sub-task | Giờ |
|---|---|---|
| 3.1 | Enum `ControllerButton` (18 nút) | 0.5 |
| 3.2 | Enum `InputMode`, mặc định `SEQUENCE` | 0.5 |
| 3.3 | `TutorialStep` và công thức `stopPositionMs`, chống tràn số | 1 |
| 3.4 | `ScriptViolation` (18 loại lỗi) và `FieldType` | 1.5 |
| 3.5 | `DemoSource` / `DemoLoad` | 0.5 |
| 3.6 | Interface `DemoRepository` | 0.5 |
| 3.7 | Port `VideoPlayback` và `PlaybackEvent` | 1 |

## 4. Parser JSON (7.5h)

File: `domain/script/DemoScriptParser.kt`

| # | Sub-task | Giờ |
|---|---|---|
| 4.1 | Parse mảng gốc, báo lỗi `NotAJsonArray` | 1 |
| 4.2 | Kiểm tra mỗi phần tử là object (`StepNotAnObject`) | 0.5 |
| 4.3 | Kiểm tra đủ trường bắt buộc (`MissingField`) | 1 |
| 4.4 | Kiểm tra kiểu INTEGER / NUMBER / STRING / STRING_ARRAY (`WrongType`) | 1.5 |
| 4.5 | Phát hiện trường cũ `targetButtonId` và gợi ý đổi tên (`RenamedField`) | 0.5 |
| 4.6 | Bỏ qua `pauseTimeMs` để file cũ vẫn chạy (E10) | 0.5 |
| 4.7 | Map ID nút, phân biệt hoa thường (`UnknownButton`) | 1 |
| 4.8 | Parse `inputMode`, mặc định `SEQUENCE` (`UnknownInputMode`) | 0.5 |
| 4.9 | Gom mọi lỗi một lượt, không dừng ở lỗi đầu tiên | 1 |

## 5. Validator và use case (8.5h)

File: `domain/script/DemoScriptValidator.kt`, `domain/usecase/LoadDemoUseCase.kt`

| # | Sub-task | Giờ |
|---|---|---|
| 5.1 | `step_sequence` ≥ 1 và không trùng | 1 |
| 5.2 | Sắp xếp các bước theo `step_sequence` (E12) | 0.5 |
| 5.3 | `triggerTimeMs` ≥ 0 | 0.5 |
| 5.4 | `targetButtonIds` không rỗng | 0.5 |
| 5.5 | `playbackSpeed` trong [0, 1], chặn giá trị < 0.1 trừ 0 (E11) | 1 |
| 5.6 | `slowDurationMs` ≥ 0; tốc độ 0 bắt buộc đi kèm duration 0 | 0.5 |
| 5.7 | `SIMULTANEOUS`: tối đa 5 nút, không lặp nút (E6) | 1 |
| 5.8 | Mốc dừng phải trước khi video kết thúc | 1 |
| 5.9 | Mốc trigger của bước sau phải lớn hơn mốc dừng của bước trước | 1 |
| 5.10 | Chỉ chạy luật timeline khi các luật khác đã pass | 0.5 |
| 5.11 | `LoadDemoUseCase`: parse + validate → Ready hoặc Invalid | 1 |

## 6. Data layer và phát video (12.5h)

File: `data/demo/AssetDemoRepository.kt`, `data/playback/Media3VideoPlayback.kt`, `feature/demo/DemoPlaybackHost.kt`

| # | Sub-task | Giờ |
|---|---|---|
| 6.1 | `AssetDemoRepository` đọc `assets/demos/<id>/script.json` | 1 |
| 6.2 | Đọc thời lượng video bằng `MediaMetadataRetriever` | 1.5 |
| 6.3 | Thiếu file hoặc đọc lỗi → `AppError` | 0.5 |
| 6.4 | `Media3VideoPlayback`: prepare, play, pause, seek | 2 |
| 6.5 | Đổi tốc độ phát, bật `setEnableAudioTrackPlaybackParams` | 1 |
| 6.6 | Tắt tiếng khi chạy chậm (D7) | 0.5 |
| 6.7 | `scheduleCue` / `cancelCue` bằng `PlayerMessage` theo vị trí video (D5) | 2 |
| 6.8 | Dùng `CUE_GUARD_MS` cho cue quá sát vị trí hiện tại | 1 |
| 6.9 | Phát `PlaybackEvent`: ready, ended, cue, error | 1 |
| 6.10 | Release player đúng một lần | 0.5 |
| 6.11 | `DemoPlaybackHost` giữ player qua config change | 1 |
| 6.12 | Bật `EventLogger` ở bản debug để đo timing | 0.5 |

## 7. Logic input: StepProgress (5.5h)

File: `domain/input/StepProgress.kt`

| # | Sub-task | Giờ |
|---|---|---|
| 7.1 | `SEQUENCE`: chỉ nút kế tiếp được active | 1 |
| 7.2 | Nút lặp phải nhả ra rồi nhấn lại (D4) | 1 |
| 7.3 | `ANY_ORDER`: active các nút chưa bấm | 1 |
| 7.4 | `SIMULTANEOUS`: hoàn thành khi mọi nút cùng đang được chạm (E5) | 1.5 |
| 7.5 | Xử lý nhả nút trong `SIMULTANEOUS` | 0.5 |
| 7.6 | Tính `activeButtons` cho highlight và enable | 0.5 |

## 8. MVI: Contract, ViewModel, Route (20h)

File: `feature/demo/DemoContract.kt`, `DemoViewModel.kt`, `DemoRoute.kt`

| # | Sub-task | Giờ |
|---|---|---|
| 8.1 | `DemoContract`: State, Phase, Intent, Effect | 1.5 |
| 8.2 | Load demo khi vào màn → `Playing` / `InvalidScript` / `Failed` | 1.5 |
| 8.3 | Phát từ đầu ở tốc độ 1.0× khi player sẵn sàng | 0.5 |
| 8.4 | Tới trigger: bật tutorial, đổi tốc độ, lên lịch điểm dừng | 2 |
| 8.5 | Tới mốc dừng: pause video, giữ tutorial và tiến độ | 1 |
| 8.6 | `ButtonPressed` / `ButtonReleased` → cập nhật `StepProgress` | 1 |
| 8.7 | Hoàn thành bước: hủy cue, ẩn tutorial, về 1.0×, phát tiếp, sang bước kế | 2 |
| 8.8 | Mỗi bước chỉ kích hoạt và hoàn thành đúng một lần | 1 |
| 8.9 | Video kết thúc → `Finished` | 0.5 |
| 8.10 | Replay: reset state, seek về 0 | 1 |
| 8.11 | Thoát → `DemoEffect.Exit` → đóng Activity | 0.5 |
| 8.12 | State dẫn xuất: `enabledButtons`, `highlightedButtons`, việc hiện nút Thoát | 1 |
| 8.13 | `ScreenStopped`: pause video, đặt countdown | 1 |
| 8.14 | `ScreenStarted`: đếm ngược 5s, rồi khôi phục đúng tốc độ và trạng thái chờ | 2 |
| 8.15 | Không hiện countdown khi video chưa phát hoặc demo đã kết thúc (E9) | 1 |
| 8.16 | Process bị kill → chạy lại demo từ đầu | 0.5 |
| 8.17 | `DemoRoute`: composition root, collect effect, lifecycle observer | 2 |

## 9. UI: bộ điều khiển (14h)

File: `feature/demo/component/ControllerGeometry.kt`, `ControllerOverlay.kt`, `ButtonGlyph.kt`, `PlaceAt.kt`

| # | Sub-task | Giờ |
|---|---|---|
| 9.1 | `ControllerGeometry`: bảng vị trí 18 nút theo tay cầm DualSense (tạm, chờ Figma) | 3 |
| 9.2 | Scale bố cục theo kích thước màn, tránh cutout | 2 |
| 9.3 | Vẽ vector các nút △ ○ ✕ □ | 1.5 |
| 9.4 | Vẽ mũi tên D-pad | 1 |
| 9.5 | Nút dạng chữ: L1, L2, R1, R2, L3, R3, Options, Create, PS, Touchpad | 1.5 |
| 9.6 | `ControllerButtonView`: `pointerInput` đa điểm chạm | 2 |
| 9.7 | Đặt `clip` trước `pointerInput` để vùng chạm đúng hình nút | 0.5 |
| 9.8 | Đọc `enabled` lúc chạm xuống, không tính nút đã giữ từ trước | 1 |
| 9.9 | Hiệu ứng nhấn cho mọi nút (D3) | 1 |
| 9.10 | Helper `PlaceAt` | 0.5 |

## 10. UI: tutorial overlay (9h)

File: `feature/demo/component/TutorialSpotlight.kt`, `TutorialCard.kt`

| # | Sub-task | Giờ |
|---|---|---|
| 10.1 | `TutorialSpotlight`: lớp tối phủ toàn màn | 1 |
| 10.2 | Đục lỗ đúng bounds của nút active | 2 |
| 10.3 | Viền highlight cho nút active | 1 |
| 10.4 | Lớp tối không nhận chạm, để chạm trong lỗ tới được nút bên dưới | 1 |
| 10.5 | `TutorialCard`: tiêu đề tự sinh theo `inputMode` | 1 |
| 10.6 | `TargetChips`: hiện các nút cần bấm | 1.5 |
| 10.7 | Căn vị trí card trong vùng được cấp | 1 |
| 10.8 | Ép `LayoutDirection.Ltr` | 0.5 |

## 11. UI: màn hình và panel (12.5h)

File: `feature/demo/DemoScreen.kt`, `component/VideoSurface.kt`, `ExitButton.kt`, `DemoPanels.kt`, `DemoMessages.kt`, `res/values*/strings.xml`

| # | Sub-task | Giờ |
|---|---|---|
| 11.1 | `VideoSurface` phủ kín màn (Q15) | 1 |
| 11.2 | `ExitButton` vẫn bấm được khi lớp tối đang hiện (E8) | 1 |
| 11.3 | `LoadingIndicator` | 0.5 |
| 11.4 | `FinishedPanel`: Replay và Thoát (Q13) | 1 |
| 11.5 | `ErrorPanel`: liệt kê từng lỗi kịch bản (Q12) | 1.5 |
| 11.6 | Panel cho lỗi chung (không đọc được video hoặc file) | 0.5 |
| 11.7 | `ResumeCountdownOverlay` đếm 5s | 1 |
| 11.8 | `DemoMessages`: câu thông báo cho cả 18 loại lỗi | 2 |
| 11.9 | `DemoScreen` ghép các lớp: video, controller, spotlight, card, panel | 2 |
| 11.10 | `strings.xml` tiếng Anh | 1 |
| 11.11 | `strings.xml` tiếng Việt | 1 |

## 12. Dữ liệu demo mẫu (1.5h)

Thư mục: `app/src/main/assets/demos/sample/`

| # | Sub-task | Giờ |
|---|---|---|
| 12.1 | Video mẫu 70s tạo bằng ffmpeg `testsrc`, có đồng hồ trên hình | 0.5 |
| 12.2 | `script.json` mẫu: đủ 3 chế độ, có nút lặp, có bước dừng ngay | 1 |

## 13. Unit test (15.5h)

Thư mục: `app/src/test/kotlin/com/pion/psremote/`

| # | Sub-task | Giờ |
|---|---|---|
| 13.1 | `FakeVideoPlayback`, `FakeDemoRepository`, fixture cho ViewModel | 2 |
| 13.2 | `DemoScriptParserTest` | 2 |
| 13.3 | `DemoScriptValidatorTest`, gồm cả việc kiểm tra `script.json` đang đóng gói | 2 |
| 13.4 | `TutorialStepTest` (mốc dừng, tràn số) | 1 |
| 13.5 | `StepProgressTest` (3 chế độ, nút lặp, bấm sớm) | 2 |
| 13.6 | `DemoViewModelTest`: trigger → chạy chậm → dừng → hoàn thành | 3 |
| 13.7 | `DemoViewModelLifecycleTest`: xuống nền, countdown, `Ended` đến sau `ON_STOP` | 2.5 |
| 13.8 | `ControllerGeometryTest`: nút nào cũng có vị trí | 1 |

## 14. Sửa lỗi phát hiện khi test (8.5h)

Chi tiết từng lỗi: `LLM.md` §11 "Fixed" và §12.

| # | Sub-task | Giờ |
|---|---|---|
| 14.1 | F1: 0.66s đầu mỗi pha chạy chậm vẫn phát ở 1× | 2 |
| 14.2 | F2: bật RTL làm spotlight lệch khỏi nút | 1.5 |
| 14.3 | F3: nút tròn nhận chạm ở góc của khung vuông | 1 |
| 14.4 | F4: cue dừng quá sát trigger không bao giờ chạy | 2 |
| 14.5 | F5: tốc độ < 0.1×, tràn số `triggerTimeMs`, countdown đè lên panel Finished | 2 |

## 15. QA thủ công trên máy thật (9.5h)

Chưa xác minh đã chạy. Cách đo timing ở `LLM.md` §9.

| # | Sub-task | Giờ |
|---|---|---|
| 15.1 | Đo bằng `EventLogger`: mỗi lần dừng phải khớp `stopPositionMs` | 1.5 |
| 15.2 | Multi-touch `SIMULTANEOUS` từ 2 đến 5 nút (không làm được bằng adb) | 1 |
| 15.3 | Các tỷ lệ màn 16:9, 20:9, máy có notch | 1.5 |
| 15.4 | Xuống nền, quay lại, dialog đếm ngược | 1 |
| 15.5 | Process bị kill (bật "Don't keep activities") | 0.5 |
| 15.6 | Đổi ngôn ngữ EN/VI, bật Force RTL | 0.5 |
| 15.7 | Nạp các JSON lỗi, kiểm tra màn báo lỗi | 1 |
| 15.8 | Bấm sớm, bấm liên tục, nút lặp, bấm nút không được highlight | 1 |
| 15.9 | Máy yếu hoặc Android 9 (minSdk 28) | 1 |
| 15.10 | Đổi cấu hình giữa chừng (dark mode, chia đôi màn hình) | 0.5 |

## 16. Review, tài liệu, bàn giao (4.5h)

Chưa xác minh đã chạy.

| # | Sub-task | Giờ |
|---|---|---|
| 16.1 | Chạy checklist MVI §9 ([docs/android-mvi-best-practices.md](docs/android-mvi-best-practices.md)) | 1 |
| 16.2 | Code review toàn bộ | 2 |
| 16.3 | Cập nhật `LLM.md` §11 | 0.5 |
| 16.4 | Build APK debug cho bản thử nghiệm, viết release note | 1 |

## 17. Còn lại và đang bị chặn (15.5h, cộng 8h nếu nhiều hơn 1 demo)

| # | Sub-task | Giờ | Đang chặn bởi |
|---|---|---|---|
| 17.1 | Xin quyền edit Figma cho tài khoản đang kết nối (Q1, E1) | – | BA/PM |
| 17.2 | Cập nhật bảng `SPECS` (vị trí, kích thước nút) theo Figma | 3 | 17.1 |
| 17.3 | Đổi kiểu highlight và `TutorialCard` theo Figma | 3 | 17.1 |
| 17.4 | Đổi màu và font theo Figma (`PsColors`, `TextSize`) | 1.5 | 17.1 |
| 17.5 | Nhận video và JSON thật, thay vào `assets/demos/sample` (Q3) | 1 | BA |
| 17.6 | Chạy validator trên JSON thật, gửi lỗi lại cho BA | 1 | 17.5 |
| 17.7 | Chốt video đóng gói trong APK hay stream (E2) | – | BA |
| 17.8 | Chốt số lượng demo (E2) | – | BA |
| 17.9 | Nếu nhiều hơn 1 demo: thêm Navigation Compose | 2 | 17.8 |
| 17.10 | Nếu nhiều hơn 1 demo: màn chọn demo (Contract, ViewModel, Screen, test) | 4 | 17.8 |
| 17.11 | Nếu nhiều hơn 1 demo: chuyển wiring sang Koin (`LLM.md` §6) | 2 | 17.8 |
| 17.12 | BA soát lại E1–E12 trong confirm.md | – | BA |
| 17.13 | Release signing và bật R8 (keep rules cho Media3) | 2 | – |
| 17.14 | Chạy lại QA nhóm 15 với Figma và video thật | 4 | 17.2–17.6 |
