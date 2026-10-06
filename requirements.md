> **Yêu cầu gốc của BA, giữ nguyên văn.** Tới 2026-10-06 nội dung này là `README.md`. Code và tài liệu trích nó
> là `requirements.md §N`, theo số mục bên dưới.
>
> Đây không còn là spec đang dùng. Mọi chỗ đã đổi so với bản này nằm trong [`confirm.md`](confirm.md). Định dạng
> JSON đang dùng nằm trong `tools/demo-script-format.md`; ví dụ: bên dưới vẫn ghi `targetButtonId` và
> `pauseTimeMs`, còn app dùng `targetButtonIds`, bỏ `pauseTimeMs` và thêm `inputMode`.

---

**YÊU CẦU PHÁT TRIỂN TÍNH NĂNG DEMO – INTERACTIVE VIDEO GAMEPLAY**

**1. Mục tiêu**

Xây dựng chế độ Demo cho phép user trải nghiệm các nút điều khiển trên màn hình thông qua video gameplay quay sẵn, không cần kết nối máy chơi game.

Video phát theo kịch bản cố định. Tại các mốc được cấu hình, hệ thống hiển thị tutorial, giảm tốc độ và dừng chờ user thực hiện đúng thao tác để tiếp tục.

**2. Phạm vi triển khai**

- Phát video gameplay theo chiều ngang.
- Hiển thị bộ điều khiển và tutorial phía trên video.
- Đọc kịch bản tương tác từ JSON riêng của từng demo.
- Hỗ trợ một hoặc nhiều nút trong một bước, gồm:
  - Bấm theo thứ tự.
  - Nhấn giữ đồng thời.
  - Bấm đủ nút, không yêu cầu thứ tự..

**3. Cấu trúc giao diện:
[Link UI](https://www.figma.com/design/NeCET4LMwkS0tdWUAvsYtc/PS-Remote_UI?node-id=12167-8820&t=VmHNBZXLlKpirym8-4): [https://www.figma.com/design/NeCET4LMwkS0tdWUAvsYtc/PS-Remote_UI?node-id=12167-8820&t=VmHNBZXLlKpirym8-4\*\*](https://www.figma.com/design/NeCET4LMwkS0tdWUAvsYtc/PS-Remote_UI?node-id=12167-8820&t=VmHNBZXLlKpirym8-4</strong>)**

- ****Video Player:** Phát video nền, giữ đúng tỷ lệ hình ảnh.**
- ****Controller Overlay:** Hiển thị các nút điều khiển và nhận input.**
- ****Tutorial Overlay:** Làm tối nền, highlight nút mục tiêu và hiển thị hướng dẫn.**

**Vùng highlight phải trùng với vùng nhận chạm của nút trên mọi kích thước màn hình được hỗ trợ. Với tổ hợp đồng thời, cần bảo đảm các nút có thể chạm cùng lúc mà không bị lớp tutorial chặn.**

****4. Cấu hình JSON****

**Thay trường `targetButtonId` bằng `targetButtonIds` để thống nhất cho cả bước một nút và nhiều nút. Sử dụng `slowDurationMs` để xác định thời lượng chạy chậm; không dùng đồng thời `pauseTimeMs`.**

| Trường | Kiểu dữ liệu | Mô tả / Quy tắc |
| --- | --- | --- |
| `step_sequence` | Integer | Thứ tự xuất hiện của bước, bắt đầu từ `1`, tăng dần và không trùng trong cùng kịch bản. |
| `triggerTimeMs` | Integer | Thời điểm hiển thị tutorial trên timeline video, đơn vị mili giây. Giá trị `> 0` và nhỏ hơn thời lượng video. |
| `pauseTimeMs` | Integer | Thời điểm dừng video nếu user chưa hoàn thành thao tác, đơn vị mili giây trên timeline video. Giá trị `≥ triggerTimeMs`, nhỏ hơn thời lượng video và nằm trước cảnh hành động tương ứng. |
| `targetButtonId` | Array of String | Danh sách ID các nút user cần bấm để hoàn thành bước, có ít nhất một phần tử. Ví dụ: `["DPAD_UP", "CROSS", "R1"]`. User bấm lần lượt theo thứ tự trong mảng; hoàn thành đủ chuỗi thì video tiếp tục. |
| `playbackSpeed` | Number | Hệ số tốc độ phát khi tutorial xuất hiện. Giá trị `0 < playbackSpeed ≤ 1`; ví dụ `0.25` là một phần tư tốc độ bình thường. Riêng giá trị `0` quy định video dừng ngay và phải đi kèm `slowDurationMs = 0`. |
| `slowDurationMs` | Integer | Thời gian thực tế video phát ở tốc độ `playbackSpeed` trước khi dừng chờ, đơn vị mili giây. Giá trị `> 0` khi chạy chậm; bằng `0` khi dừng ngay. Không tính thời gian buffering, mở menu hoặc app xuống nền. |

****Ví dụ JSON:****

```
[{"step_sequence": 1,"triggerTimeMs": 34000,"pauseTimeMs": 34750,"targetButtonId": ["DPAD_UP", "CROSS", "R1"],"playbackSpeed": 0.25,"slowDurationMs": 3000},{"step_sequence": 2,"triggerTimeMs": 60000,"pauseTimeMs": 60000,"targetButtonId": ["CROSS"],"playbackSpeed": 0,"slowDurationMs": 0}]
```

****5. Quy tắc kiểm tra cấu hình****

- **Các trường bắt buộc không được thiếu hoặc sai kiểu dữ liệu.**
- **step_sequence vừa là thứ tự step vừa là ID và tồn tại duy nhất không được trùng**
- **`triggerTimeMs ≥ 0`; các bước được sắp xếp tăng dần.**
- **`slowDurationMs ≥ 0`; giá trị `0` nghĩa là hiện tutorial và dừng ngay.**
- **Mốc dừng dự kiến:`stopPositionMs = triggerTimeMs + playbackSpeed × slowDurationMs`**
- **Mốc dừng phải nằm trước cảnh hành động và trước khi video kết thúc.**
- **Mốc kích hoạt bước sau phải lớn hơn mốc dừng của bước trước.**
- **`targetButtonId` chỉ chứa ID được hỗ trợ và có trên giao diện.**
- **Với tổ hợp đồng thời, Dev xác nhận số điểm chạm hỗ trợ trên nhóm thiết bị mục tiêu; cấu hình không được vượt giới hạn đã thống nhất.**

****6. Luồng phát video****

- **Khi vào màn:Khi sẵn sàng → phát video từ đầu ở tốc độ `1.0`.**
- **Khi đến `triggerTimeMs`:Kích hoạt tutorial một lần cho bước hiện tại.Set tốc độ theo `playbackSpeed`.Hiển thị hướng dẫnBắt đầu tính `slowDurationMs`.**
- **Hết thời lượng nhưng user chưa hoàn thành:Pause video, giữ tutorial.Giữ các thao tác đã hoàn thành trong bước.Không tự bỏ qua hoặc tự kết thúc bước.**
- **User hoàn thành trong lúc chạy chậm hoặc đang dừng:Đánh dấu bước hoàn thành một lần.Hủy tác vụ dừng chờ.Ẩn tutorial.Khôi phục tốc độ `1.0`, tiếp tục từ vị trí video hiện tại.Chuyển sang theo dõi bước kế tiếp.**

**Ví dụ: Video đến giây 34 → chạy ở `0.25×` trong 3 giây thực tế → dừng tại khoảng giây 34,75 nếu user chưa hoàn thành.**

****7. Tiêu chí nghiệm thu****

- **Tutorial xuất hiện đúng mốc, đúng nút và đúng chế độ thao tác.**
- **Một bước hỗ trợ được một nút và tổ hợp từ ba nút theo cấu hình, trong giới hạn thiết bị đã thống nhất.**
- **Chuỗi có nút lặp yêu cầu các lần tap riêng.**
- **Tổ hợp đồng thời chỉ hoàn thành khi tất cả nút mục tiêu đang được giữ cùng lúc.**
- **Bấm sai, bấm sớm và bấm liên tục không bỏ qua bước.**
- **Chưa hoàn thành tổ hợp → video dừng đúng điểm chờ.**
- **Vùng chạm, highlight và bố cục hoạt động đúng**

****8. Đề nghị team Dev phản hồi****

- **Mức độ khả thi của tính năng**
- **Estimate thời gian hoàn thành**
- **Điều chỉnh cần thiết cho cấu trúc JSON.**
- **Thời gian bàn giao bản thử nghiệm**

**BA sẽ Cung cấp video và file json kịch bản**