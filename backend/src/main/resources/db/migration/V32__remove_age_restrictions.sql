-- Migration V32: Loại bỏ toàn bộ ràng buộc và kiểm tra độ tuổi trên toàn hệ thống Comic Net
-- Đảm bảo tất cả dữ liệu truyện hợp lệ và không bị chặn đọc

-- 1. Chuẩn hóa tất cả truyện hiện có về AgeRating = 'ALL_AGE'
UPDATE dbo.Comic
SET AgeRating = 'ALL_AGE'
WHERE AgeRating IS NULL OR AgeRating <> 'ALL_AGE';

-- 2. Gỡ bỏ CHECK constraint trên cột AgeRating nếu có
DECLARE @ConstraintName nvarchar(200);
SELECT @ConstraintName = name
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID('dbo.Comic')
  AND parent_column_id = COLUMNPROPERTY(OBJECT_ID('dbo.Comic'), 'AgeRating', 'ColumnId');

IF @ConstraintName IS NOT NULL
BEGIN
    EXEC('ALTER TABLE dbo.Comic DROP CONSTRAINT ' + @ConstraintName);
END;

-- 3. Tạo default constraint cho AgeRating = 'ALL_AGE' nếu chưa có
IF NOT EXISTS (
    SELECT 1 FROM sys.default_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.Comic')
      AND parent_column_id = COLUMNPROPERTY(OBJECT_ID('dbo.Comic'), 'AgeRating', 'ColumnId')
)
BEGIN
    ALTER TABLE dbo.Comic ADD CONSTRAINT DF_Comic_AgeRating DEFAULT 'ALL_AGE' FOR AgeRating;
END;
