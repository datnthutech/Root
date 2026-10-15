// Khi bố cục đổi (thu gọn/mở rộng nhánh) mà camera được giữ nguyên, tọa độ thế giới của nút bị bấm có thể dịch chuyển.
// Trả về độ dời cần cộng vào tâm camera để nút đó đứng yên trên màn hình.
export interface Box { x: number; y: number; w: number; h: number }

export function anchorShift(before: Box | undefined, after: Box | undefined): { dx: number; dy: number } {
  if (!before || !after) return { dx: 0, dy: 0 }
  return { dx: after.x + after.w / 2 - (before.x + before.w / 2), dy: after.y + after.h / 2 - (before.y + before.h / 2) }
}
