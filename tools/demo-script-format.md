# Định dạng kịch bản demo (JSON)

Tài liệu cho BA khi viết kịch bản cho chế độ Demo Interactive Video Gameplay.
Các quyết định gốc nằm trong [`confirm.md`](../confirm.md).

## Mỗi game là một mục trên catalogue

Từ ngày 2026-10-02, app lấy danh sách game từ catalogue của store (category PS Remote) thay vì đóng gói trong APK
([`confirm.md`](../confirm.md) mục H). Mỗi mục trên CMS là một game:

| Trường trên CMS | Dùng để làm gì |
| --- | --- |
| `name` | Tên game hiện trên Home, giữ nguyên chính tả, ví dụ "Spider Man". |
| `priority` | Thứ tự trên Home, số nhỏ đứng trước. |
| `status` | `false` thì ẩn game khỏi Home. |
| `custom_fields.json` | Kịch bản tương tác: mảng JSON mô tả ở các mục bên dưới. |
| `custom_fields.source_vid` | Link video gameplay quay ngang (MP4). App phát trực tuyến và lưu cache trên máy. |

Ngày 2026-10-02, catalogue có `Sample` (video thử nghiệm) và `Spider Man`.

### Thêm hoặc sửa một game

BA tự làm được cả quy trình bằng công cụ `tools/demo-assets.py`. Hướng dẫn cài đặt và cách đọc kết quả nằm ở
[`tools/README.md`](README.md), cùng thư mục với file này.

1. Đặt video gốc vào `<thư-mục-game>/video.mp4` và kịch bản vào `<thư-mục-game>/script.json`. Thư mục để ở đâu cũng
   được, thường là `demo-sources/<id-demo>/` ở gốc repo. `<id-demo>` là tên tự đặt, kiểu `ten-game`.
2. Chạy `python3 tools/demo-assets.py check <thư-mục-game>`. Công cụ kiểm tra kịch bản theo đúng luật của app, với độ
   dài thật của video, và báo lỗi bằng đúng câu mà màn lỗi trong app sẽ hiện. Sửa tới khi hết lỗi.
3. Chạy `python3 tools/demo-assets.py build <thư-mục-game>`. Lệnh tạo ra `<thư-mục-game>/video-interpolated.mp4`
   (xem mục "Video chạy chậm mượt" bên dưới).
4. Upload `video-interpolated.mp4` lên CMS, rồi dán link vào `source_vid`. Dán kịch bản vào `custom_fields.json`.
   Kịch bản trên CMS phải giống hệt `script.json` đã dùng để tạo video.
5. Mở game trên điện thoại để chơi thử. Muốn kiểm tra mọi game trên catalogue cùng lúc, dev chạy
   `RemoteDemoRepositoryDeviceTest` trên máy thật. Kịch bản không còn được kiểm tra lúc build app.

Không cần build lại app. Home đọc catalogue mỗi lần mở app. Mục thiếu kịch bản, thiếu video hoặc kịch bản sai vẫn
hiện trong danh sách. Khi mở game đó, app hiện đầy đủ lỗi để sửa.

Upload video mới thì luôn dùng link mới, đừng ghi đè file cũ cùng link: app nhận diện video trong cache theo link,
nên máy đã xem bản cũ sẽ tiếp tục phát bản cũ.

## Thay đổi so với yêu cầu gốc (`requirements.md`)

| Bản nháp | Bản dùng thật | Lý do |
| --- | --- | --- |
| `targetButtonId` | `targetButtonIds` | Đổi tên theo [`requirements.md`](../requirements.md) §4. File còn dùng tên cũ sẽ báo lỗi, kèm gợi ý tên mới. |
| `pauseTimeMs` | Bỏ | App tự tính mốc dừng từ `triggerTimeMs`, `playbackSpeed` và `slowDurationMs`. Nếu file vẫn có trường này, app bỏ qua nó. |
| (không có) | `inputMode` | Trường mới để phân biệt 3 cách bấm. Không bắt buộc, mặc định là `SEQUENCE`. |

## Các trường của một bước

| Trường | Kiểu | Bắt buộc | Quy tắc |
| --- | --- | --- | --- |
| `step_sequence` | Số nguyên | Có | Từ 1 trở lên. Không được trùng giữa các bước. Không cần liên tục (1, 2, 5 vẫn hợp lệ). |
| `triggerTimeMs` | Số nguyên | Có | Từ 0 trở lên. Đây là thời điểm hiện tutorial trên timeline video, đơn vị ms. |
| `targetButtonIds` | Mảng chuỗi | Có | Ít nhất 1 nút. Chỉ dùng ID trong bảng "ID các nút" bên dưới, viết hoa đúng như bảng. |
| `inputMode` | Chuỗi | Không | `SEQUENCE`, `SIMULTANEOUS` hoặc `ANY_ORDER`. Mặc định `SEQUENCE`. |
| `playbackSpeed` | Số | Có | Từ 0.1 đến 1 để chạy chậm. Bằng 0 để dừng ngay. Không dùng giá trị trong khoảng từ 0 đến 0.1, vì nhiều máy không phát được audio ở tốc độ thấp như vậy. |
| `slowDurationMs` | Số nguyên | Có | Thời gian thực chạy ở tốc độ chậm, từ 0 trở lên. Nếu `playbackSpeed` = 0 thì trường này phải bằng 0. |

## Mốc dừng

App tính mốc dừng như sau:

```
mốc dừng = triggerTimeMs + playbackSpeed × slowDurationMs
```

Ví dụ: `triggerTimeMs` 34000, `playbackSpeed` 0.25, `slowDurationMs` 3000. Mốc dừng là 34000 + 750 = 34750. Video chạy ở tốc độ 0.25× trong 3 giây thực tế, rồi dừng ở giây 34,75.

- Mốc dừng tính theo vị trí trên video, không theo đồng hồ. Vì vậy thời gian video buffering, app xuống nền hay đang đếm ngược đều không bị trừ vào thời gian chạy chậm.
- Nếu user hoàn thành trước mốc dừng, video trở lại tốc độ 1.0 và phát tiếp từ vị trí hiện tại.
- Nếu `slowDurationMs` bằng 0, hoặc `playbackSpeed` bằng 0, video dừng ngay khi tutorial hiện.
- Video không đổi tốc độ đột ngột mà giảm dần từ 1.0 xuống `playbackSpeed` trong 0,3 giây đầu. Mốc dừng
  không đổi. Tuy vậy, trong lúc giảm tốc video chạy nhanh hơn `playbackSpeed`, nên thời gian chạy chậm thực
  tế ngắn hơn `slowDurationMs`. Đo trên Galaxy A16: `slowDurationMs` 2000 ở tốc độ 0.25 cho khoảng 1,25 giây
  chạy chậm thực tế. Muốn user có đủ thời gian thì tăng `slowDurationMs`, và nhớ rằng mốc dừng sẽ lùi về sau
  theo công thức trên.

## Tính điểm

Mỗi bước được chấm điểm một lần, ngay khi user bấm xong bước đó:

| Hạng | Khi nào | Điểm |
| --- | --- | --- |
| Hoàn hảo (PERFECT) | Hoàn thành khi video còn đang chạy chậm, trước mốc dừng | 100 |
| Tốt (GOOD) | Hoàn thành sau khi video đã dừng chờ ở mốc dừng | 50 |

- Bước dừng ngay (`playbackSpeed` = 0 hoặc `slowDurationMs` = 0) luôn được 100 điểm, vì bước đó không có pha chạy chậm.
- Điểm tối đa của một demo bằng số bước × 100. Điểm hiện ở góc trên bên phải trong lúc chơi, và hiện cùng điểm tối đa ở
  màn Hoàn thành. Bấm Xem lại thì điểm về 0. Thoát demo thì điểm không được lưu.
- Không bị trừ điểm. User không thể bấm sai, vì chỉ nút đang sáng mới nhận chạm.
- App xuống nền giữa lúc chạy chậm không làm user mất hạng Hoàn hảo, vì mốc dừng tính theo vị trí video.

**Lưu ý khi viết kịch bản:** thời gian để đạt Hoàn hảo bằng thời gian chạy chậm thực tế, ngắn hơn `slowDurationMs`
(xem mục "Mốc dừng"). Ví dụ trên Galaxy A16, `slowDurationMs` 2000 ở tốc độ 0.25 chỉ cho khoảng 1,25 giây. Muốn một bước dễ
đạt Hoàn hảo hơn, hãy tăng `slowDurationMs`, nhất là với bước cần bấm 2–3 nút.

Giữ `playbackSpeed × slowDurationMs` từ **250 trở lên** cho mọi bước chạy chậm (ví dụ 0.25 × 1000). App hiện chưa kiểm tra
điều này:

- Dưới khoảng 250, thời gian để đạt Hoàn hảo ngắn hơn phản xạ của người bấm.
- Dưới khoảng 100, video đã dừng ngay khi tutorial hiện, nên bước đó chỉ đạt tối đa 50 điểm, và không ai đạt được điểm
  tối đa của demo.

Nếu muốn một bước dừng ngay và vẫn được 100 điểm, hãy đặt `playbackSpeed` = 0 và `slowDurationMs` = 0.

Toàn bộ quy tắc, viết cho dev, nằm trong `docs/button-press-scoring-rules.md`. File đó không có trong git, chỉ có trên
máy dev. Những gì BA cần biết về điểm đã có đủ trong mục này.

## Video chạy chậm mượt

Video 30 khung hình/giây khi chạy ở tốc độ 0.25 chỉ còn 7,5 khung/giây, nên bị giật. Công cụ
`tools/interpolate-slow-segments.py` chèn thêm khung hình nội suy vào đúng các đoạn chạy chậm. Mỗi đoạn tính
từ `triggerTimeMs` đến mốc dừng. Nhờ đó đoạn chậm vẫn hiện 30 khung/giây. Phần còn lại của video, âm thanh
và vị trí mọi khung hình gốc đều giữ nguyên, nên mốc thời gian trong kịch bản vẫn rơi đúng cảnh cũ.

- Sau mỗi lần sửa `triggerTimeMs`, `playbackSpeed` hoặc `slowDurationMs` của một bước chạy chậm, phải chạy lại
  `python3 tools/demo-assets.py build <thư-mục-game>` rồi upload lại video. Lệnh luôn đi từ video gốc. Nếu quên, demo
  vẫn đúng nhưng đoạn chậm mới sẽ giật như trước. Lệnh `check` phát hiện được việc này: video upload ghi lại các
  đoạn chạy chậm mà nó được tạo cho, và `check` báo "được tạo cho kịch bản cũ" khi chúng không còn khớp.
- Thời gian tạo video tăng theo độ dài video và số đoạn chạy chậm: `spiderman` (2 phút 19, 854×480, 11 đoạn) khoảng
  1 phút; `spiderman2` (3 phút 5, 1280×720 60 fps, 22 đoạn) khoảng 6,5 phút. Trong lúc chạy, lệnh hiện phần trăm đã
  xong và thời gian còn lại (`tools/README.md` mục 3).
- Video gốc cần có số khung hình/giây cố định. Video bắt đầu trễ một chút (tối đa 100 ms, hay gặp ở video có B-frame,
  như `spiderman2` trễ 33 ms) được dời về 0 khi tạo video upload. Trễ hơn thì công cụ báo lỗi và cần xuất lại video.
- Khung hình nội suy là khung "đoán". Ở cảnh camera lia nhanh có thể thấy hình hơi méo hoặc có bóng mờ.
  Video gốc 60 khung/giây sẽ cho kết quả đẹp hơn, vì công cụ phải đoán ít khung hơn.
- Dung lượng video gần như giữ nguyên (`spiderman`: 20,8 MB lên 22,6 MB).

## Ba chế độ bấm

| `inputMode` | User phải làm gì | Nút được highlight |
| --- | --- | --- |
| `SEQUENCE` | Bấm lần lượt theo thứ tự trong mảng. Nút lặp (ví dụ `["CROSS","CROSS"]`) phải nhả ra rồi bấm lại. | Mọi nút chưa bấm, mỗi nút có số thứ tự 1, 2, 3. Nút kế tiếp sáng rõ và nhấp nháy. Các nút sau sáng mờ và chưa bấm được. Nút đã bấm thì tắt sáng. Bước chỉ có 1 nút thì không hiện số. |
| `SIMULTANEOUS` | Bấm cùng lúc tất cả nút, không cần giữ. Bước hoàn thành ngay khi mọi nút cùng đang được chạm. Tối đa 5 nút, không lặp nút. | Tất cả nút, không có số |
| `ANY_ORDER` | Bấm đủ các nút, thứ tự bất kỳ. Nút lặp phải bấm đủ số lần. | Các nút chưa bấm, không có số. Nút còn phải bấm từ 2 lần trở lên thì hiện "×2", "×3". |

Không có chữ hướng dẫn nào trên màn hình: các nút sáng lên chính là hướng dẫn. Vì vậy, nhìn vào màn hình thì
`SIMULTANEOUS` và `ANY_ORDER` trông giống nhau.

Nếu một nút xuất hiện nhiều lần trong `SEQUENCE`, nút đó hiện số nhỏ nhất chưa bấm. Ví dụ
`["R2","CROSS","R2"]`: R2 hiện 1, bấm xong đổi thành 3.

Khi tutorial đang hiện, chỉ nút đang sáng rõ mới bấm được. Các nút còn lại, kể cả các nút sáng mờ đang chờ
tới lượt trong `SEQUENCE`, đều không nhận chạm, nên user không thể bấm sai. Nút đã được giữ từ trước khi
tutorial hiện sẽ không được tính: user phải nhả ra rồi bấm lại.

Lưu ý khi chọn tổ hợp `SIMULTANEOUS`: user cầm ngang và bấm bằng hai ngón cái, nên từ 3 nút trở lên đã khó thao tác. Nên chọn nút nằm ở hai bên màn hình, ví dụ `L1` + `R1`.

## ID các nút

| Nhóm | ID |
| --- | --- |
| D-pad | `DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT` |
| Nút mặt | `CROSS`, `CIRCLE`, `SQUARE`, `TRIANGLE` |
| Vai | `L1`, `L2`, `R1`, `R2` |
| Nhấn cần analog | `L3`, `R3` |
| Giữa | `OPTIONS`, `CREATE`, `PS`, `TOUCHPAD` |

## Quy tắc giữa các bước

- Các bước được sắp theo `step_sequence`. Thứ tự các bước trong file không quan trọng.
- `triggerTimeMs` của một bước phải lớn hơn mốc dừng của bước ngay trước nó.
- Mốc dừng của mọi bước phải nhỏ hơn thời lượng video.
- Mốc dừng phải nằm trước cảnh hành động tương ứng. App không nhìn thấy nội dung video, nên BA tự bảo đảm quy tắc này.

## Ví dụ

```json
[
  {"step_sequence": 1, "triggerTimeMs": 34000, "targetButtonIds": ["DPAD_UP", "CROSS", "R1"], "inputMode": "SEQUENCE", "playbackSpeed": 0.25, "slowDurationMs": 3000},
  {"step_sequence": 2, "triggerTimeMs": 45000, "targetButtonIds": ["L1", "R1"], "inputMode": "SIMULTANEOUS", "playbackSpeed": 0.5, "slowDurationMs": 2000},
  {"step_sequence": 3, "triggerTimeMs": 60000, "targetButtonIds": ["CROSS"], "playbackSpeed": 0, "slowDurationMs": 0}
]
```

Kịch bản đầy đủ của demo mẫu là `custom_fields.json` của mục `Sample` trên catalogue. Nó dùng cả 3 chế độ, có một bước với nút lặp và một bước dừng ngay.

## Khi kịch bản sai

App không phát demo, mà hiện một màn liệt kê toàn bộ lỗi cùng lúc. Mỗi lỗi ghi rõ bước nào, trường nào và giá trị hợp lệ là gì, ví dụ:

- `Bước thứ 2 trong file: "targetButtonId" đã đổi tên thành "targetButtonIds".`
- `Bước 3: triggerTimeMs (14000) phải sau mốc dừng của bước 2 (14500 ms).`

Lỗi về cấu trúc (thiếu trường, sai kiểu dữ liệu) đánh số theo vị trí của bước trong file, tính từ 1, vì bước đó có thể chưa có `step_sequence` hợp lệ. Các lỗi còn lại đánh số theo `step_sequence`.
