-- Thời điểm token bị xoay vòng (refresh). Dùng để phân biệt dùng lại sau khi xoay với thu hồi do logout/đổi mật khẩu.
ALTER TABLE refresh_token ADD COLUMN rotated_at timestamptz NULL;
