import { errorText } from './errorText'

test('RELATION_INVALID, NOT_REVERTIBLE, VALIDATION_FAILED ưu tiên message của server', () => {
  for (const code of ['RELATION_INVALID', 'NOT_REVERTIBLE', 'VALIDATION_FAILED']) {
    expect(errorText({ code, message: 'Đã có cha ruột' })).toBe('Đã có cha ruột')
  }
})
test('không có message thì dùng câu chung', () => {
  expect(errorText({ code: 'RELATION_INVALID' })).toBe('Quan hệ không hợp lệ')
  expect(errorText({ code: 'VALIDATION_FAILED', message: '  ' })).toBe('Dữ liệu không hợp lệ')
  expect(errorText('NOT_REVERTIBLE')).toBe('Mục này không khôi phục được')
})
test('mã khác giữ câu chung dù có message', () => {
  expect(errorText({ code: 'FORBIDDEN', message: 'Không đủ quyền' })).toBe('Bạn không có quyền thực hiện thao tác này')
})
test('PAYLOAD_TOO_LARGE và mã lạ', () => {
  expect(errorText({ code: 'PAYLOAD_TOO_LARGE' })).toBe('Tệp quá lớn')
  expect(errorText(undefined)).toBe('Đã có lỗi xảy ra, vui lòng thử lại')
})
test('TOO_MANY_REQUESTS', () => {
  expect(errorText({ code: 'TOO_MANY_REQUESTS' })).toBe('Thao tác quá nhiều lần, vui lòng thử lại sau')
})
