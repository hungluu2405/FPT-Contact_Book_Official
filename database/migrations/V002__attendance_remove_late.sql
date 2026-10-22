/*
 * V002 – Điểm danh còn ba trạng thái: PRESENT (có mặt), ABSENT_EXCUSED (vắng có phép), ABSENT_UNEXCUSED (vắng
 * không phép). Bỏ LATE (đi muộn): giáo viên chọn "Có mặt" và ghi vào ghi chú của học sinh.
 *
 * 1. Dòng LATE cũ thành PRESENT, ghi chú giữ nguyên (ví dụ "Muộn 10 phút do xe hỏng"). Cách tính cũ vốn coi đi
 *    muộn là có mặt, nên tỷ lệ chuyên cần và tỷ lệ vắng không đổi.
 * 2. Thay ràng buộc ck_att_status bằng bản chỉ gồm ba giá trị, khớp enum AttendanceStatus.
 *
 * PHẢI chạy trên database dev TRƯỚC khi chạy bản ứng dụng mới: enum không còn LATE nên đọc dòng LATE sẽ lỗi.
 * Chạy tay (ví dụ trong SSMS) sau script gốc và V001; chạy lại nhiều lần vẫn an toàn. Test tự chạy file này trên
 * FptContactBook_test.
 *
 * Rollback (chỉ khôi phục ràng buộc; dòng đi muộn đã thành có mặt không tách lại được, ngoài phần còn trong ghi chú):
 *   ALTER TABLE [dbo].[attendance] DROP CONSTRAINT [ck_att_status];
 *   ALTER TABLE [dbo].[attendance] WITH CHECK ADD CONSTRAINT [ck_att_status]
 *       CHECK ([status] = N'ABSENT_UNEXCUSED' OR [status] = N'ABSENT_EXCUSED' OR [status] = N'LATE'
 *              OR [status] = N'PRESENT');
 */
USE [FptContactBook]
GO
UPDATE [dbo].[attendance] SET [status] = N'PRESENT' WHERE [status] = N'LATE';
GO
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE name = N'ck_att_status' AND parent_object_id = OBJECT_ID(N'[dbo].[attendance]')
             AND definition LIKE N'%LATE%')
    ALTER TABLE [dbo].[attendance] DROP CONSTRAINT [ck_att_status];
GO
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE name = N'ck_att_status' AND parent_object_id = OBJECT_ID(N'[dbo].[attendance]'))
    ALTER TABLE [dbo].[attendance] WITH CHECK ADD CONSTRAINT [ck_att_status]
        CHECK ([status] = N'ABSENT_UNEXCUSED' OR [status] = N'ABSENT_EXCUSED' OR [status] = N'PRESENT');
GO
