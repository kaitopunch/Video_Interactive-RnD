# Xác nhận yêu cầu — Demo Interactive Video Gameplay

- **Nguồn yêu cầu:** [README.md](README.md)
- **Ngày tạo:** 2026-09-29
- **Trạng thái:** Đã chốt, đã triển khai (2026-09-29). Cách hiểu các câu còn trống hoặc mơ hồ nằm ở mục E.

Cách trả lời: với mỗi câu, đánh dấu `[x]` nếu đồng ý với đề xuất, hoặc ghi ý kiến vào dòng "Ý kiến khác".
Câu nào chưa trả lời thì chưa được triển khai phần phụ thuộc vào câu đó.

**Hiện trạng khi viết file này**

- Figma MCP báo tài khoản đang kết nối không có quyền edit với file thiết kế, nên chưa đọc được giao diện.
- `LLM.md` và `AGENTS.md` trong repo đang mô tả dự án khác (PhoneCleanerDemo), không phải PS Remote.
- Repo chưa có code Android.

---

## A. Cần bạn hoặc BA cung cấp (đang chặn)

### 1. Quyền truy cập Figma

Cần share file cho tài khoản Figma đã kết nối, hoặc export PNG màn demo. Nếu không có, tôi không biết vị trí nút, kiểu highlight và nội dung tutorial.

**Trả lời:**
- [v] Đã share quyền
- [ ] Sẽ gửi PNG
- Ý kiến khác:

### 2. Danh sách button ID

JSON mẫu chỉ có `DPAD_UP`, `CROSS`, `R1`.

**Đề xuất:** `DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`, `CROSS`, `CIRCLE`, `SQUARE`, `TRIANGLE`, `L1`, `L2`, `R1`, `R2`, `L3`, `R3`, `OPTIONS`, `CREATE`, `PS`, `TOUCHPAD`.
Analog stick (thao tác kéo) nằm ngoài phạm vi, vì JSON chỉ mô tả thao tác bấm.

**Trả lời:**
- [v] Đồng ý đề xuất
- Ý kiến khác:

### 3. Video và JSON kịch bản

BA chưa gửi video và JSON.

**Đề xuất:**
- Trong lúc chờ, dùng một video mẫu công khai và JSON tự viết để phát triển.
- Video đóng gói trong APK, không stream từ URL.
- Có bao nhiêu demo? Có cần màn chọn demo, hay app mở thẳng vào demo? — *cần bạn trả lời*

**Trả lời:**
- [v] Đồng ý dùng video mẫu trong lúc chờ
- [ ] Video đóng gói trong APK
- Số lượng demo / màn chọn demo:
- Ý kiến khác:

### 4. Thiết lập project

Đây là app demo độc lập, hay sau này sẽ gộp vào app PS Remote có sẵn? Nếu gộp, cấu trúc nên theo app đó.

**Đề xuất:**
- Một module `:app`, package `com.pion.psremote`, minSdk 28.
- Media3 ExoPlayer để phát video.
- Theo `docs/android-mvi-best-practices.md` (`MviViewModel` + Contract). Chỉ có một màn nên không dùng Koin.
- Viết lại `LLM.md` cho dự án này.

**Trả lời:**
- App độc lập hay gộp vào app có sẵn:
- [v] Đồng ý đề xuất
- Ý kiến khác:

---

## B. Chỗ spec tự mâu thuẫn

### 5. `targetButtonId` hay `targetButtonIds`

§4 bảo đổi `targetButtonId` thành `targetButtonIds`, nhưng bảng và JSON mẫu vẫn dùng `targetButtonId`.

**Đề xuất:** dùng `targetButtonIds`.

**Trả lời:**
- [v] Đồng ý đề xuất
- Ý kiến khác:

### 6. `pauseTimeMs` và `slowDurationMs`

§4 bảo không dùng `pauseTimeMs` cùng lúc với `slowDurationMs`, nhưng bảng và ví dụ vẫn có cả hai.

**Đề xuất:** bỏ `pauseTimeMs`. App tự tính `stopPositionMs = triggerTimeMs + playbackSpeed × slowDurationMs`.

**Trả lời:**
- [v] Đồng ý đề xuất
- Ý kiến khác:

### 7. Giới hạn dưới của `triggerTimeMs`

Bảng ghi `> 0`, §5 ghi `≥ 0`.

**Đề xuất:** `≥ 0`.

**Trả lời:**
- [v] Đồng ý đề xuất
- Ý kiến khác:

### 8. JSON thiếu trường chế độ thao tác

§2 và §7 yêu cầu 3 chế độ (bấm theo thứ tự, nhấn giữ đồng thời, bấm đủ nút không cần thứ tự), nhưng JSON không có trường nào để phân biệt.

**Đề xuất:** thêm trường `inputMode`, nhận một trong ba giá trị:

| Giá trị | Ý nghĩa |
| --- | --- |
| `"SEQUENCE"` | Bấm lần lượt theo thứ tự trong mảng. Là giá trị mặc định khi thiếu trường. |
| `"SIMULTANEOUS"` | Giữ tất cả nút cùng lúc. |
| `"ANY_ORDER"` | Bấm đủ các nút, không cần thứ tự. |

**Trả lời:**
- [v] Đồng ý đề xuất
- Ý kiến khác:

---

## C. Hành vi cần chốt

### 9. Bấm sai giữa chừng

Nếu bấm sai chỉ bị bỏ qua, user bấm loạn mọi nút rồi cũng hoàn thành được chuỗi bất kỳ. Điều này trái với §7 ("bấm liên tục không bỏ qua bước").

**Đề xuất:**
- Bấm sai thì tiến độ của bước về 0, kèm rung hoặc chớp đỏ. Áp dụng cho cả `SEQUENCE` và `ANY_ORDER`.
- Khi video tự pause vì hết thời gian chạy chậm, tiến độ vẫn được giữ như §6 quy định.

**Trả lời:**
- [ ] Đồng ý đề xuất
- Ý kiến khác: Không cho user bấm nút khác ngoài nút được highlight

### 10. Nhấn giữ đồng thời

- Tính hoàn thành ngay khi tất cả nút cùng được giữ, hay phải giữ tối thiểu một khoảng thời gian?
- Nút đã nhấn từ trước khi tutorial hiện có được tính không?

**Đề xuất:** phải giữ tối thiểu 300 ms. Nút nhấn trước khi tutorial hiện không được tính, user phải nhấn lại.

**Trả lời:**
- [ ] Đồng ý đề xuất
- Thời gian giữ tối thiểu khác (ms):
- Ý kiến khác: click đồng thời, không giữ

### 11. Giới hạn số điểm chạm

Android chỉ bảo đảm 2 điểm chạm, phần lớn máy hiện nay có từ 5 trở lên.

**Đề xuất:** `SIMULTANEOUS` tối đa 3 nút. Validator từ chối JSON vượt quá.

Lưu ý: cầm ngang bằng hai ngón cái thì rất khó giữ 3 nút cùng lúc, nên BA cần chọn tổ hợp dễ bấm.

**Trả lời:**
- [ ] Đồng ý đề xuất
- Giới hạn khác: giới hạn max là 5
- Ý kiến khác:

### 12. JSON không hợp lệ

**Đề xuất:** hiện màn lỗi liệt kê từng vi phạm để BA dễ sửa, thay vì lặng lẽ bỏ qua bước lỗi.

**Trả lời:**
- [v] Đồng ý đề xuất
- Ý kiến khác:

### 13. Khi video kết thúc

Hiện màn "Hoàn thành" có nút Replay và Thoát, hay phát lặp lại?

**Đề xuất:** màn "Hoàn thành" có Replay và Thoát.

**Trả lời:**
- [v] Đồng ý đề xuất
- [ ] Phát lặp lại
- Ý kiến khác:

### 14. Nội dung tutorial

- Text tự sinh từ chế độ và nút (ví dụ "Nhấn giữ L1 + R1"), hay mỗi bước có trường `message` riêng trong JSON?
- Ngôn ngữ: EN, VI hay cả hai?

**Đề xuất:** text tự sinh; hỗ trợ cả EN và VI.

**Trả lời:**
- [v] Đồng ý đề xuất
- [ ] Dùng trường `message` trong JSON
- Ngôn ngữ:
- Ý kiến khác:

**Thay đổi 2026-10-01:** bỏ thẻ chữ hướng dẫn, chỉ còn highlight nút. Xem mục F.

### 15. Tỷ lệ video

Video 16:9 trên máy 20:9: hiển thị đủ khung (có viền đen hai bên) hay phủ kín màn (cắt trên/dưới)?

**Đề xuất:** hiển thị đủ khung. Bộ nút vẫn phủ trên toàn màn.

**Trả lời:**
- [ ] Đồng ý đề xuất
- [v] Phủ kín màn
- Ý kiến khác:

### 16. Điều khiển khác

Có cần nút Skip, Replay, tắt tiếng, seek bar không? §4 có nhắc "mở menu", vậy có menu pause không?

**Đề xuất:** chỉ có nút Thoát, không menu, không seek.

**Trả lời:**
- [v] Đồng ý đề xuất
- Ý kiến khác:

---

## D. Giả định sẽ áp dụng nếu không có phản đối

Ghi "ok" hoặc ý kiến vào cột Trả lời.

| # | Giả định | Trả lời |
| --- | --- | --- |
| D1 | Khóa màn hình ngang, chỉ hỗ trợ điện thoại (không làm tablet). | ok|
| D2 | Lớp tối của tutorial không chặn chạm: mọi nút vẫn nhận input. Vùng highlight lấy đúng bounds của nút, nên luôn trùng vùng chạm trên mọi kích thước màn hình. | Vùng tối chặn chạm, chỉ cho nút được active (highlight có thể click) |
| D3 | Nhấn nút ngoài lúc có tutorial chỉ hiện hiệu ứng nhấn, không ảnh hưởng gì. | ok|
| D4 | Một lần nhấn được tính khi ngón tay chạm xuống. Nút lặp (ví dụ `["CROSS","CROSS"]`) phải nhả ra rồi nhấn lại. |ok |
| D5 | Điểm dừng tính theo vị trí video (`stopPositionMs`), không theo đồng hồ thực. Cách này tự loại trừ thời gian buffering và thời gian app ở nền, đúng như §4 yêu cầu. | ok|
| D6 | App xuống nền thì pause. Khi quay lại, khôi phục đúng trạng thái đang có. Nếu process bị hệ thống kill thì chạy lại demo từ đầu. | (khi user quay lại app mà process chưa bị kill thì hiển thị dialog đếm ngược 5s để user bình tĩnh rồi ẩn dialog tiếp tục task tại vị trí bị suspend) |
| D7 | Tắt tiếng khi video chạy chậm, vì âm thanh ở tốc độ 0.25× nghe méo. |ok |
| D8 | `step_sequence` chỉ cần duy nhất và tăng dần, không bắt buộc liên tục. `triggerTimeMs` phải tăng theo cùng thứ tự đó. | ok|
| D9 | Validator kiểm tra mọi quy tắc ở §5, trừ "trước cảnh hành động": app không nhận biết được nội dung cảnh, nên phần này do BA đảm bảo. | ok|

---

## E. Cách hiểu khi triển khai (cần BA soát lại)

Các câu dưới đây còn để trống hoặc có thể hiểu theo nhiều cách. Tôi đã triển khai theo cách hiểu bên dưới. Cách hiểu nào sai thì ghi vào cột "Trả lời".

| # | Cách hiểu đã triển khai | Trả lời |
| --- | --- | --- |
| E1 (Q1) | Figma vẫn báo thiếu quyền. Tài khoản Figma đang kết nối là `lothanhduy2003@gmail.com`, và Figma MCP cần quyền **edit** trên file. Trong lúc chờ, bộ nút được bố trí theo tay cầm DualSense. Khi có Figma, chỉ cần sửa một bảng vị trí. | |
| E2 (Q3) | Chưa có câu trả lời về việc đóng gói video, nên tạm đóng gói video trong APK. Từ 2026-10-01, app mở vào màn Home liệt kê mọi thư mục trong `assets/demos/` (hiện có `sample` và `spiderman`). Tên game lấy từ tên thư mục, viết hoa chữ đầu mỗi từ (`god-of-war` → "God Of War"). Demo `sample` là video thử nghiệm, vẫn hiện trong danh sách. | |
| E3 (Q4) | Làm app độc lập, không gộp vào app PS Remote có sẵn. | |
| E4 (Q9) | Không cho bấm nút ngoài highlight. Cụ thể: `SEQUENCE` chỉ highlight nút kế tiếp; `ANY_ORDER` highlight các nút chưa bấm; `SIMULTANEOUS` highlight tất cả. Do đó user không thể bấm sai. | |
| E5 (Q10) | "Click đồng thời, không giữ": bước hoàn thành ngay khi tất cả nút cùng đang được chạm, không yêu cầu thời gian giữ tối thiểu. Nút đã chạm từ trước khi tutorial hiện thì không tính. | |
| E6 (Q11) | Tối đa 5 nút cho `SIMULTANEOUS`. Ngoài ra, một bước `SIMULTANEOUS` không được lặp cùng một nút. | |
| E7 (Q14) | Có cả EN và VI, ngôn ngữ đi theo ngôn ngữ của máy: máy đặt tiếng Việt thì hiện VI, còn lại hiện EN. | |
| E8 (D2) | Nút Thoát vẫn bấm được khi lớp tối đang hiện, để user không bị kẹt ở một bước. | |
| E9 (D6) | Dialog đếm ngược 5 giây cũng hiện khi video đang dừng chờ thao tác. Dialog không hiện nếu app xuống nền trước khi video bắt đầu phát, hoặc sau khi demo đã kết thúc. | |
| E11 (README §4) | README cho phép `playbackSpeed` bất kỳ trong khoảng từ 0 đến 1. App chặn các giá trị nhỏ hơn 0.1 (trừ 0), vì nhiều máy không phát được audio ở tốc độ thấp như vậy: tốc độ sẽ không đổi và video dừng sớm. | |
| E12 (README §5) | Các bước không cần xếp theo thứ tự trong file: app sắp xếp theo `step_sequence`. Nếu BA muốn app báo lỗi khi file xếp sai thứ tự, ghi vào đây. | |
| E10 (Q6) | File cũ còn trường `pauseTimeMs` vẫn chạy được, vì app bỏ qua trường này. File còn dùng `targetButtonId` sẽ báo lỗi, kèm gợi ý đổi tên. | |

## F. Thay đổi ngày 2026-10-01

Yêu cầu: (1) video tua chậm phải mượt, không khựng; (2) bỏ dialog hướng dẫn, chỉ highlight nút điều khiển.

| # | Đã chốt | Ghi chú |
| --- | --- | --- |
| F1 | Bỏ thẻ chữ hướng dẫn và dòng "Đã tạm dừng…". Hướng dẫn chỉ còn lớp tối và các nút được highlight. | Thay cho Q14. |
| F2 | `SEQUENCE` nhiều nút: mọi nút chưa bấm cùng sáng, mỗi nút có số 1, 2, 3 ở góc trên bên phải. Nút kế tiếp sáng rõ và nhấp nháy. Nút sau sáng mờ và chưa bấm được (vẫn giữ Q9). Nút đã bấm tắt sáng. Nút lặp hiện số nhỏ nhất chưa bấm. Bước 1 nút không có số. | |
| F3 | `SIMULTANEOUS`: các nút cùng sáng, không có dấu hiệu riêng, nên trông giống `ANY_ORDER`. | `ANY_ORDER` giữ như cũ. Nút phải bấm nhiều lần hiện "×2". |
| F4 | Nguyên nhân giật: video 30 fps chạy 0.25× chỉ còn 7,5 khung/giây. Cách sửa: nội suy khung hình bằng ffmpeg `minterpolate`, chỉ ở các đoạn chạy chậm. Video gốc lưu ở `demo-sources/`. | Chọn ffmpeg thay vì AI (RIFE). Cảnh camera lia nhanh có thể hơi méo. Chỉ xử lý `spiderman`. |
| F5 | Video giảm tốc dần từ 1× xuống `playbackSpeed` trong 0,3 giây, không đổi tốc độ đột ngột. Mốc dừng không đổi. | Thời gian chạy chậm thực tế ngắn hơn. Trên Galaxy A16, `slowDurationMs` 2000 ở 0.25× còn khoảng 1,25 giây. |

Định dạng JSON chính thức cho BA: [docs/demo-script-format.md](docs/demo-script-format.md).
