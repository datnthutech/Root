const TEXT: Record<string, string> = {
  BAD_CREDENTIALS: 'Sai tên đăng nhập hoặc mật khẩu',
  ACCOUNT_PENDING: 'Tài khoản đang chờ quản trị viên duyệt',
  ACCOUNT_LOCKED: 'Tài khoản đang bị khoá, vui lòng thử lại sau',
  USERNAME_TAKEN: 'Tên đăng nhập đã tồn tại',
  VALIDATION_FAILED: 'Dữ liệu không hợp lệ',
  FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này',
  UNAUTHENTICATED: 'Phiên đăng nhập đã hết hạn',
  VERSION_CONFLICT: 'Dữ liệu đã được người khác sửa, vui lòng tải lại',
  RELATION_CYCLE: 'Quan hệ này tạo vòng lặp trong gia phả',
  RELATION_INVALID: 'Quan hệ không hợp lệ',
  NOT_FOUND: 'Không tìm thấy dữ liệu',
  NOT_REVERTIBLE: 'Mục này không khôi phục được',
  EDIT_WINDOW_EXPIRED: 'Đã quá 24 giờ, chỉ quản trị viên được sửa',
}
export function errorText(code: string | undefined): string {
  return (code && TEXT[code]) || 'Đã có lỗi xảy ra, vui lòng thử lại'
}
