# PS Remote — Demo Interactive Video Gameplay

App Android cho user thử các nút điều khiển trên màn hình bằng video gameplay quay sẵn, không cần kết nối máy
PlayStation. Video phát theo một kịch bản JSON. Tới mốc trong kịch bản, màn hình tối đi, các nút cần bấm sáng lên,
video chạy chậm dần rồi dừng chờ cho tới khi user bấm đúng.

**Trạng thái (2026-10-06):** bản `0.1.0`. Đã triển khai yêu cầu gốc ([`requirements.md`](requirements.md)) theo các
quyết định trong [`confirm.md`](confirm.md). Đã test trên JVM, Robolectric và Galaxy A16 (SM-A165F). Danh sách game
lấy từ catalogue của store, nên thêm hoặc sửa game không cần build lại app. Các điểm còn lệch nằm ở
[Hạn chế đã biết](#hạn-chế-đã-biết).

## Tính năng

- **Home** liệt kê các game trên catalogue, sắp theo `priority`. Game có `status = false` bị ẩn. Khi lỗi mạng hoặc
  danh sách rỗng, Home hiện nút Thử lại.
- **Màn demo** phát video quay ngang, giữ đúng tỷ lệ, với tay cầm kiểu PlayStation phủ lên trên. Vùng sáng của
  tutorial trùng với vùng nhận chạm của nút trên mọi kích thước màn hình. Màn hình không có chữ hướng dẫn: nút sáng
  lên chính là hướng dẫn, và được đánh số 1, 2, 3 khi phải bấm theo thứ tự.
- **Ba chế độ bấm** (`inputMode`):
  - `SEQUENCE`: bấm lần lượt.
  - `SIMULTANEOUS`: chạm cùng lúc, tối đa 5 nút.
  - `ANY_ORDER`: bấm đủ các nút, thứ tự bất kỳ.
- **Chạy chậm và dừng chờ:** video giảm dần về `playbackSpeed` trong 0,3 giây, rồi dừng ở
  `triggerTimeMs + playbackSpeed × slowDurationMs`. Mốc dừng tính theo vị trí trên video, nên thời gian buffering
  hay lúc app xuống nền không bị trừ vào thời gian chạy chậm.
- **Điểm:** một bước được 100 điểm nếu bấm xong khi video còn chạy, 50 điểm nếu video đã dừng chờ. Tổng điểm hiện
  ở góc trên bên phải và ở màn Hoàn thành. Thoát demo thì điểm không được lưu.
- **Kịch bản sai:** khi mở game, app hiện màn liệt kê từng lỗi. Câu báo lỗi giống hệt câu mà công cụ `check` của
  BA in ra.
- Video phát trực tuyến và được cache trên máy, tối đa 256 MB. Giao diện có tiếng Anh và tiếng Việt. Quyền duy nhất
  app cần là INTERNET.

## Build và chạy

Cần có:

- Android SDK có API 37.
- JDK 17 trở lên. Máy dev đang dùng JDK 24.
- Một điện thoại Android 9 trở lên để chạy test trên máy thật.

**1. Thêm API key của catalogue** vào `local.properties`. File này không có trong git.

```properties
sdk.dir=/đường/dẫn/tới/Android/sdk
CATALOGUE_API_KEY=<key>
```

Có thể đặt biến môi trường `CATALOGUE_API_KEY` thay cho dòng trên. Nếu thiếu key, build vẫn thành công và Gradle in
cảnh báo, nhưng server trả 404 nên Home báo không tải được danh sách.

**2. Build và cài:**

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Các lệnh khác:

| Việc | Lệnh | Ghi chú |
| --- | --- | --- |
| Test JVM và Robolectric | `./gradlew :app:testDebugUnitTest` | |
| Test trên điện thoại | `./gradlew :app:connectedDebugAndroidTest --no-configuration-cache` | Phải có `--no-configuration-cache`: AGP 9.2.1 không lưu được input của task manifest androidTest vào configuration cache. Bộ này gồm `RemoteDemoRepositoryDeviceTest`, test kiểm tra mọi game trên catalogue thật. |
| Bản release | `./gradlew :app:assembleRelease` | Chạy R8 và shrink resource. APK được ký bằng `keystore.properties`; thiếu file này thì APK ra không ký. Debug và release dùng key khác nhau, nên phải `adb uninstall com.pion.psremote` trước khi cài bản này đè lên bản kia. |
| Benchmark | `./gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pion.psremote.benchmark.<Class>` | `<Class>` là `StartupBenchmark`, `OpenDemoBenchmark` hoặc `TutorialFrameBenchmark`. `BaselineProfileGenerator` tạo baseline profile. Điện thoại phải sáng màn hình, đã mở khoá và đang cắm sạc. |
| Test công cụ của BA | `python3 -m unittest discover -s tools` | Cần Python 3.9+, chỉ dùng thư viện chuẩn. Các suite cần ffmpeg tự bỏ qua nếu máy không có ffmpeg. |

## Thêm hoặc sửa một game

Không cần build lại app, vì Home đọc catalogue mỗi lần mở app. Mỗi game là một mục trong category PS Remote trên
CMS:

| Trường trên CMS | Dùng để |
| --- | --- |
| `name` | Tên game hiện trên Home |
| `priority` | Thứ tự trên Home, số nhỏ đứng trước |
| `status` | `false` thì ẩn game khỏi Home |
| `custom_fields.json` | Kịch bản: một mảng JSON các bước |
| `custom_fields.source_vid` | Link video MP4 quay ngang |

BA tự làm được cả quy trình bằng công cụ trong `tools/`. Công cụ cần Python 3.9+ và ffmpeg.

1. Đặt video gốc và kịch bản vào cùng một thư mục, với tên `video.mp4` và `script.json`.
2. Chạy `python3 tools/demo-assets.py check <thư-mục-game>`. Lệnh kiểm tra kịch bản theo đúng luật của app, dựa trên
   độ dài thật của video, và không sửa file nào.
3. Chạy `python3 tools/demo-assets.py build <thư-mục-game>`. Lệnh tạo `video-interpolated.mp4`, bản đã nội suy để các
   đoạn chạy chậm không bị giật.
4. Upload `video-interpolated.mp4` **lên một link mới**, rồi dán link vào `source_vid`. Nếu ghi đè file cũ ở link
   cũ, điện thoại đã cache bản cũ sẽ tiếp tục phát bản cũ.
5. Dán nội dung `script.json` vào `custom_fields.json`. Nội dung phải giống hệt file đã dùng để chạy `build`.
6. Mở game trên điện thoại và chơi thử.

Hướng dẫn chi tiết cho BA, gồm cả cách đọc kết quả, nằm trong [`tools/README.md`](tools/README.md).

Đây là ba bước đầu của kịch bản `spiderman`:

```json
[
  {"step_sequence": 1, "triggerTimeMs": 4500, "targetButtonIds": ["CROSS"], "inputMode": "SEQUENCE", "playbackSpeed": 0, "slowDurationMs": 0},
  {"step_sequence": 2, "triggerTimeMs": 11000, "targetButtonIds": ["R2"], "inputMode": "SEQUENCE", "playbackSpeed": 0.25, "slowDurationMs": 2000},
  {"step_sequence": 3, "triggerTimeMs": 24500, "targetButtonIds": ["L2", "R2"], "inputMode": "SIMULTANEOUS", "playbackSpeed": 0.25, "slowDurationMs": 2000}
]
```

Bước 1 dừng ngay khi tutorial hiện. Bước 2 và 3 chạy chậm ở 0.25× trong 2 giây, rồi dừng chờ ở mốc
`triggerTimeMs + 500`. ID của nút là tên hằng trong
[`ControllerButton.kt`](app/src/main/kotlin/com/pion/psremote/domain/model/ControllerButton.kt), phân biệt chữ hoa và
chữ thường. Luật đầy đủ của từng trường nằm trong [`tools/demo-script-format.md`](tools/demo-script-format.md).

## Cấu trúc mã nguồn

```
app/              ứng dụng: một module, các tầng là package trong com.pion.psremote
  core/           MVI base, theme, design token, logger
  domain/         model, đọc và kiểm tra kịch bản, xử lý thao tác bấm, tính điểm; Kotlin thuần, test trên JVM
  data/           catalogue (OkHttp), phát video và cache (Media3)
  feature/home/   màn chọn game
  feature/demo/   màn demo: video, tay cầm, tutorial, điểm
  di/             Koin graph
  navigation/     NavHost và route
benchmark/        Macrobenchmark và tạo baseline profile, chạy trên app đã cài
tools/            công cụ asset cho BA: kiểm tra kịch bản, tạo video nội suy (Python và ffmpeg)
demo-sources/     video gốc và kịch bản của từng game, không có trong git
```

Stack: Kotlin, Jetpack Compose, MVI, Koin, Navigation Compose, Media3 (ExoPlayer) và OkHttp. Phiên bản của từng thư
viện nằm trong [`gradle/libs.versions.toml`](gradle/libs.versions.toml). SDK: minSdk 28, targetSdk 36, compileSdk 37.
App chỉ chạy ở chế độ ngang.

Trước khi sửa code, hãy đọc `LLM.md` (file mới đặt ở đâu) và `docs/android-mvi-best-practices.md` (viết một màn MVI
thế nào).

## Tài liệu

| File | Nội dung | Có trong git |
| --- | --- | --- |
| [`requirements.md`](requirements.md) | Yêu cầu gốc của BA, giữ nguyên văn | Có |
| [`confirm.md`](confirm.md) | Mọi quyết định đã chốt trên yêu cầu gốc, kể cả các thay đổi sau đó như tính điểm và lấy game từ API | Có |
| [`demo-interactive-video-task-breakdown.md`](demo-interactive-video-task-breakdown.md) | Chia task và estimate ban đầu | Có |
| [`tools/README.md`](tools/README.md) | Hướng dẫn công cụ asset, viết cho BA | Có |
| [`tools/demo-script-format.md`](tools/demo-script-format.md) | Định dạng kịch bản JSON, viết cho BA | Có |
| `LLM.md` | Bản đồ mã nguồn: ranh giới các tầng, package, chỗ đặt file mới, lệnh build, các điểm lệch đã biết (§11) | Không |
| `docs/android-mvi-best-practices.md` | Cách viết một màn MVI, kèm checklist trước khi tạo PR | Không |
| `docs/button-press-scoring-rules.md` | Luật tính điểm, viết cho dev | Không |
| `AGENTS.md`, `.claude/` | Cấu hình cho AI agent | Không |

Các file ghi "Không" nằm trong `.gitignore`, nên chỉ có trên máy dev.

## Hạn chế đã biết

Danh sách đầy đủ nằm trong `LLM.md` §11. Các điểm cần xử lý trước khi phát hành:

- Bố cục tay cầm đang theo tay cầm DualSense, chưa theo Figma, vì tài khoản đang dùng chưa có quyền xem file Figma
  (§11 #1).
- Bản release được ký bằng một keystore thử nghiệm. Mật khẩu của keystore này nằm ở dạng text trong
  `keystore.properties`, và file đó có trong git. Không phát hành APK ký bằng key này: sau đó sẽ không cập nhật được
  bằng key thật (§11 #3).
- API key của catalogue được build vào APK, nên ai giải nén APK cũng đọc được. Điều này được chấp nhận vì endpoint là
  public (confirm.md H5, §11 #12).
- Kịch bản trên CMS không được kiểm tra lúc build app. Sau mỗi lần sửa CMS, cần chạy `RemoteDemoRepositoryDeviceTest`
  trên điện thoại (§11 #13).
