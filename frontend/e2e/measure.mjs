// Đo hiệu năng FE. Env: BASE_URL, E2E_USER/E2E_PASS (bắt buộc khi BASE_URL không phải localhost),
// TREE_MS, POPUP_MS, MIN_NODES, EXPAND_ALL=1, SEARCH_TEXT.
// ok = coldTreeMs < TREE_MS && popupMs < POPUP_MS && treeNodes >= MIN_NODES. warmTreeMs chỉ để tham khảo, KHÔNG ảnh hưởng ok.
import { chromium } from 'playwright'

const BASE = process.env.BASE_URL ?? 'http://localhost:7000'
const isLocal = /^https?:\/\/(localhost|127\.0\.0\.1)(:|\/|$)/.test(BASE)
if (!isLocal && !(process.env.E2E_USER && process.env.E2E_PASS)) {
  console.error('E2E_USER và E2E_PASS là bắt buộc khi BASE_URL không phải localhost')
  process.exit(2)
}
const USER = process.env.E2E_USER ?? 'admin'
const PASS = process.env.E2E_PASS ?? 'password123'
const TREE_MS = Number(process.env.TREE_MS ?? 2000)
const POPUP_MS = Number(process.env.POPUP_MS ?? 300)
const MIN_NODES = Number(process.env.MIN_NODES ?? 1)
const EXPAND_ALL = process.env.EXPAND_ALL === '1'
const SEARCH_TEXT = process.env.SEARCH_TEXT ?? 'Người 15'
const VIEWPORT = { width: 1366, height: 768 }

const median = (a) => [...a].sort((x, y) => x - y)[Math.floor(a.length / 2)]
const readyNodes = (p) => p.evaluate(() => Number(document.documentElement.dataset.treeReady ?? 0))

// Đăng nhập bằng API (trước khi bấm giờ) để cookie refresh nằm trong context nhưng IndexedDB và HTTP cache vẫn trống.
async function freshContext(browser) {
  const ctx = await browser.newContext({ viewport: VIEWPORT })
  const res = await ctx.request.post(`${BASE}/api/v1/auth/login`, { data: { username: USER, password: PASS } })
  if (!res.ok()) throw new Error(`đăng nhập thất bại: ${res.status()}`)
  return ctx
}

async function timeTree(page, url) {
  const t0 = Date.now()
  await page.goto(url)
  await page.waitForFunction(() => document.documentElement.dataset.treeReady, null, { timeout: 30000 })
  return Date.now() - t0
}

// chờ canvas đứng yên (camera hết hoạt ảnh): 3 ảnh chụp liên tiếp giống nhau
async function waitCanvasIdle(page) {
  const canvas = page.locator('canvas').first()
  let prev = null, same = 0
  const deadline = Date.now() + 10000
  while (Date.now() < deadline) {
    const buf = (await canvas.screenshot()).toString('base64')
    same = buf === prev ? same + 1 : 0
    if (same >= 2) return
    prev = buf
  }
  throw new Error('canvas không ổn định sau 10 giây')
}

async function popupRun(ctx, url) {
  const page = await ctx.newPage()
  try {
    await page.goto(url)
    await page.waitForFunction(() => document.documentElement.dataset.treeReady, null, { timeout: 30000 })
    await page.getByPlaceholder('Tìm theo tên...').fill(SEARCH_TEXT)
    const option = page.locator('.ant-select-item-option', { hasText: new RegExp(`^${SEARCH_TEXT} \\(`) }).first()
    await option.waitFor({ timeout: 10000 })
    await option.click()
    await waitCanvasIdle(page)
    const box = await page.locator('canvas').first().boundingBox()
    const t = Date.now()
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2)
    await page.waitForSelector('[data-person-ready="1"]', { timeout: 10000 })
    return Date.now() - t
  } finally {
    await page.close()
  }
}

const browser = await chromium.launch()
let result
try {
  // lạnh: context mới tinh (IndexedDB trống, cache HTTP lạnh)
  const cold = await freshContext(browser)
  const coldPage = await cold.newPage()
  const coldMs = await timeTree(coldPage, `${BASE}/tree`)
  const clanUrl = coldPage.url()
  const treeNodes = await readyNodes(coldPage)
  // nóng: tải lại trong chính context đã tải cây
  const warmTreeMs = await timeTree(coldPage, clanUrl)

  // popup: context nóng, 3 lần, lấy trung vị
  const popups = []
  for (let i = 0; i < 3; i++) popups.push(await popupRun(cold, clanUrl))
  const popupMs = median(popups)

  // EXPAND_ALL: giao diện không có nút "mở rộng tất cả"; nhánh chỉ thu/mở từng nút bằng chấm trên canvas
  // (không có điểm chạm DOM ổn định) và BE trả đồ thị đầy đủ cho chế độ Toàn cảnh. Chưa đo được.
  const expanded = EXPAND_ALL ? { expandedTreeMs: null, expandedNodes: null, note: 'không có điều khiển mở rộng tất cả trong UI' } : {}
  await cold.close()

  result = {
    coldTreeMs: coldMs, warmTreeMs, popupMs, popupRuns: popups, treeNodes, ...expanded,
    thresholds: { TREE_MS, POPUP_MS, MIN_NODES },
    ok: coldMs < TREE_MS && popupMs < POPUP_MS && treeNodes >= MIN_NODES,
  }
} finally {
  await browser.close()
}
console.log(JSON.stringify(result, null, 2))
process.exit(result.ok ? 0 : 1)
