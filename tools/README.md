# Công cụ kiểm tra asset demo — hướng dẫn cho BA

Công cụ này giúp BA tự kiểm tra **video + kịch bản** của một game demo trên máy mình, rồi tự tạo video để upload lên
CMS, không cần nhờ dev.

| Lệnh | Làm gì | Mất bao lâu |
| --- | --- | --- |
| `check` | Kiểm tra `script.json` theo đúng luật của app. Câu báo lỗi giống hệt màn lỗi trong app. Kiểm tra video gốc. Cho biết video upload còn khớp với kịch bản hay không. Không sửa file nào. | Vài giây |
| `build` | Chạy `check`. Nếu không có lỗi thì tạo `video-interpolated.mp4`, là file để upload. File này làm các đoạn chạy chậm phát mượt. | Tuỳ độ dài video và số đoạn chạy chậm. Đo trên MacBook dùng chip Apple: `spiderman` (2 phút 19, 11 đoạn chậm) khoảng 1 phút; `spiderman2` (3 phút 5, 720p 60 fps, 22 đoạn chậm) khoảng 6,5 phút |

## 1. Cài đặt (làm một lần)

**macOS**

1. Mở **Terminal** (nhấn Cmd + Space, gõ `Terminal`).
2. Gõ `python3 --version` rồi Enter. Nếu macOS hỏi cài "Command Line Tools", bấm **Cài đặt** rồi chờ cài xong.
   Cần Python 3.9 trở lên.
3. Cài Homebrew nếu máy chưa có: làm theo hướng dẫn ở <https://brew.sh>.
4. Gõ `brew install ffmpeg` rồi Enter.

**Windows**

1. Cài Python từ <https://www.python.org/downloads/>. Khi cài, nhớ tick ô **Add python.exe to PATH**.
2. Mở **Command Prompt** rồi gõ `winget install Gyan.FFmpeg`. Cài xong thì đóng cửa sổ và mở lại.
3. Trên Windows, gõ `py` thay cho `python3` trong mọi lệnh bên dưới.

Nếu chưa cài ffmpeg, công cụ sẽ tự báo và in lại lệnh cài.

## 2. Chuẩn bị thư mục game

Mỗi game là một thư mục chứa đúng hai file. Tên hai file phải giữ nguyên như sau:

```
demo-sources/
└── spiderman2/
    ├── video.mp4      video gốc, quay ngang
    └── script.json    kịch bản: đúng nội dung sẽ dán vào custom_fields.json trên CMS
```

- Thư mục để ở đâu cũng được. Tên thư mục do BA tự đặt.
- Lệnh `build` tạo thêm file `video-interpolated.mp4` trong cùng thư mục.
- Định dạng kịch bản xem ở `docs/demo-script-format.md`. Tóm tắt: file là **một mảng** `[ ... ]` các bước, không bọc
  trong object kiểu `{"scriptId": ..., "steps": [...]}`.

## 3. Chạy

Trong Terminal, đi tới thư mục repo (thư mục chứa `tools/`), rồi gõ:

```
python3 tools/demo-assets.py check demo-sources/spiderman2
python3 tools/demo-assets.py build demo-sources/spiderman2
```

- Kiểm tra mọi game cùng lúc: `python3 tools/demo-assets.py check demo-sources`
- Không muốn gõ đường dẫn: gõ `python3 tools/demo-assets.py check ` (có dấu cách ở cuối), rồi **kéo thả thư mục game
  vào cửa sổ Terminal**. Terminal sẽ tự điền đường dẫn. Sau đó nhấn Enter.
- Đang chạy `build` thì đừng đóng cửa sổ. Muốn dừng thì nhấn Ctrl + C. Video upload cũ (nếu có) vẫn được giữ nguyên.
- Trong lúc `build` chạy, dòng cuối cùng tự cập nhật mỗi giây:

  ```
    ████████░░░░░░░░░░░░  42% · còn khoảng 3:40 · đã chạy 2:41 · làm mượt 12/22
  ```

  Phần trăm là phần việc đã xong. "Còn khoảng" là thời gian ước tính, tính theo tốc độ thật của máy đang chạy: vài
  giây đầu dòng này hiện "đang ước tính", sau đó con số sát dần khi chạy. Đôi khi thanh tiến độ đứng yên tới 15 giây
  trong khi đồng hồ vẫn chạy: máy vẫn đang làm việc, cứ chờ. Phần cuối dòng cho biết công cụ đang ở bước nào: "làm mượt"
  (từng đoạn chạy chậm), "nén video" (2 lượt), rồi "kiểm tra". Xong thì dòng này đổi thành `100% · xong sau …`.

## 4. Đọc kết quả

Ví dụ một lần `check`:

```
━━ spiderman2 ━━  demo-sources/spiderman2
Video gốc     video.mp4 · 1280×720 · 60 fps · 3:05.039 · 49.3 MB · bắt đầu ở 33 ms, video upload dời về 0
Kịch bản      script.json · 39 bước (22 chạy chậm, 17 dừng ngay)

  Bước  Hiện tutorial   Dừng chờ   Chạy chậm         Chế độ        Nút
     1       0:09.000   0:09.500   0.25× · 2000 ms   SEQUENCE      R2
     9       0:44.700   0:44.700   dừng ngay         SEQUENCE      TRIANGLE
   ...

LỖI — app sẽ không phát game này (1)
  ✗ Bước 3: triggerTimeMs (14000) phải sau mốc dừng của bước 2 (14500 ms).

CẢNH BÁO — app vẫn phát, nhưng nên xem lại (1)
  ! Bước thứ 5 trong file: app không đọc trường "inputmode" nên bỏ qua nó. Có phải ý bạn là "inputMode"?

Video upload  video-interpolated.mp4 · 53.8 MB · được tạo cho kịch bản cũ (đoạn chạy chậm đã đổi) — chạy build lại rồi upload lại

✗ CHƯA ĐẠT — 1 lỗi. Sửa các lỗi trên rồi chạy lại.
```

| Phần | Ý nghĩa |
| --- | --- |
| **Bảng các bước** | Mỗi bước hiện tutorial lúc nào, video dừng chờ ở đâu (theo thời gian trên video, dạng phút:giây). App không nhìn được nội dung video, nên BA phải tự mở video ra so: **mốc "Dừng chờ" phải nằm trước cảnh hành động** của bước đó. |
| **LỖI** | App sẽ hiện màn lỗi và không phát game. Phải sửa hết. "Bước thứ N trong file" là bước thứ N tính từ đầu file. "Bước N" là bước có `step_sequence` = N. |
| **CẢNH BÁO** | App vẫn phát được, nhưng có thể không như ý. Ví dụ: tên trường gõ sai chữ hoa/thường thì app bỏ qua trường đó, đoạn chạy chậm quá ngắn khiến user khó đạt Hoàn hảo, hoặc phải bấm cùng lúc 3 nút. |
| **Video upload** | Cho biết `video-interpolated.mp4` có khớp với `script.json` và `video.mp4` hiện tại không (xem bảng dưới). |
| **Dòng cuối** | `✓ ĐẠT`: sẵn sàng upload. `✓ Kịch bản và video gốc không có lỗi`: còn thiếu bước `build`. `✗ CHƯA ĐẠT`: còn lỗi. |

Trạng thái của video upload:

| Công cụ báo | Cần làm |
| --- | --- |
| khớp kịch bản và video gốc hiện tại | Không cần làm gì thêm. Upload file này nếu chưa upload. |
| chưa có | Chạy `build`. |
| được tạo cho kịch bản cũ | Đã đổi `triggerTimeMs`, `playbackSpeed` hoặc `slowDurationMs` của một bước chạy chậm. Chạy `build` rồi upload lại. |
| được tạo từ một video.mp4 khác | Đã thay video gốc. Chạy `build` rồi upload lại. |
| không rõ được tạo từ kịch bản nào | File được tạo trước khi có công cụ này. Chạy `build` một lần cho chắc. |

Chỉ sửa nút bấm (`targetButtonIds`) hoặc `inputMode` thì video upload vẫn khớp, không cần tạo lại video. Chỉ cần dán lại
kịch bản lên CMS.

## 5. Quy trình đề xuất

1. Viết hoặc sửa `script.json`, chạy `check`. Lặp lại tới khi không còn **LỖI**. Đọc cả phần **CẢNH BÁO**.
2. Mở video gốc, so với bảng các bước: mốc "Dừng chờ" của mỗi bước phải trước cảnh hành động.
3. Chạy `build`, chờ tới khi dòng cuối là `✓ ĐẠT`.
4. Upload `video-interpolated.mp4` lên CMS và dán link vào `source_vid`. **Luôn dùng link mới**, đừng ghi đè file cũ cùng
   link: điện thoại đã xem bản cũ sẽ tiếp tục phát bản cũ từ bộ nhớ đệm.
5. Dán nội dung `script.json` vào `custom_fields.json` của game trên CMS. Kịch bản trên CMS phải **giống hệt** file này.
6. Mở app trên điện thoại và chơi thử game đó.

## 6. Lỗi hay gặp

| Công cụ báo | Cách sửa |
| --- | --- |
| File đang là một object, các bước nằm trong trường "steps" | Xoá phần bao ngoài `{"scriptId": ..., "steps": ` và dấu `}` ở cuối file. Chỉ giữ mảng `[ ... ]`. |
| Kịch bản đang nằm trong dấu ngoặc kép | Kịch bản bị dán dưới dạng chuỗi. Bỏ cặp ngoặc kép bao ngoài và các dấu `\` đứng trước ngoặc kép. |
| Lỗi cú pháp JSON ở dòng X, cột Y | Thường là thiếu hoặc thừa dấu phẩy, ngoặc. Mở file, tìm đúng dòng đó. |
| Thiếu video.mp4 / script.json | Đổi tên file cho đúng. Công cụ liệt kê sẵn các file đang có trong thư mục. |
| Video có số khung hình/giây thay đổi (VFR), hoặc bắt đầu ở … ms thay vì 0 | Video cần được xuất lại. Gửi video cho người dựng, hoặc nhờ dev. |
| Tạo video thất bại, hoặc Lỗi không mong đợi | Gửi nguyên văn nội dung công cụ in ra cho dev. |

## 7. Công cụ không làm được gì

- Không nhìn được nội dung video. Mốc dừng nằm trước cảnh hành động là việc BA tự kiểm tra (bước 2 ở trên).
- Không đọc dữ liệu trên CMS. Công cụ chỉ kiểm tra file trên máy, nên kịch bản dán lên CMS phải giống hệt `script.json`.
- Không thay được việc chơi thử. Bước cuối cùng vẫn là mở game trên điện thoại.

---

*Dành cho dev:* `demo-assets.py` gọi `interpolate-slow-segments.py` để nội suy, và kiểm tra kịch bản bằng
`demo_script_rules.py` (bản Python của `DemoScriptParser` + `DemoScriptValidator`). Khi đổi luật kịch bản trong app,
cập nhật file Python đó: `test_demo_script_rules.py` sẽ fail cho tới khi hai bên khớp nhau. Chạy test:
`python3 -m unittest discover -s tools`. Xem thêm `LLM.md` §3, §10, §12.
