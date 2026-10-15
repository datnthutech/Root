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
  IMPORT_INVALID: 'Tệp có lỗi, chưa nhập gì cả',
  PAYLOAD_TOO_LARGE: 'Tệp quá lớn',
  EDIT_WINDOW_EXPIRED: 'Đã quá 24 giờ, chỉ quản trị viên được sửa',
}
/** Các mã mà message của server cụ thể hơn câu chung (vd. "Đã có cha ruột"). */
const PREFER_SERVER_MESSAGE = new Set(['RELATION_INVALID', 'NOT_REVERTIBLE', 'VALIDATION_FAILED'])

/** Nhận mã lỗi (string) hoặc đối tượng lỗi `{ code, message }` từ API. */
export function errorText(src: unknown): string {
  const e = typeof src === 'string' ? { code: src } : (src as { code?: string; message?: string } | null | undefined) ?? {}
  const code = e.code
  if (code && PREFER_SERVER_MESSAGE.has(code) && typeof e.message === 'string' && e.message.trim()) return e.message
  return (code && TEXT[code]) || 'Đã có lỗi xảy ra, vui lòng thử lại'
}
