/*
 * V001 – Chỉ mục cho trang nhật ký hệ thống (review giai đoạn 8a, mục P3).
 *
 * Bộ lọc của /admin/audit-logs lọc theo hành động và loại đối tượng, sắp theo id giảm dần; bảng audit_logs tăng
 * nhanh nhất (mỗi lần đăng nhập sai thêm một dòng). Chỉ thêm chỉ mục: không đổi bảng, cột hay dữ liệu.
 *
 * Chạy tay trên database dev (ví dụ trong SSMS) sau script gốc database/fpt_contact_book.sql. Chạy lại nhiều lần
 * cũng an toàn (có kiểm tra tồn tại). Test tự chạy file này trên FptContactBook_test.
 *
 * Rollback:
 *   DROP INDEX [ix_audit_action] ON [dbo].[audit_logs];
 *   DROP INDEX [ix_audit_entity] ON [dbo].[audit_logs];
 */
USE [FptContactBook]
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = N'ix_audit_action' AND object_id = OBJECT_ID(N'[dbo].[audit_logs]'))
    CREATE NONCLUSTERED INDEX [ix_audit_action] ON [dbo].[audit_logs] ([action] ASC, [id] DESC);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = N'ix_audit_entity' AND object_id = OBJECT_ID(N'[dbo].[audit_logs]'))
    CREATE NONCLUSTERED INDEX [ix_audit_entity] ON [dbo].[audit_logs] ([entity_type] ASC, [entity_id] ASC);
GO
