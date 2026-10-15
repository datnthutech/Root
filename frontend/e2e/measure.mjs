import { chromium } from 'playwright'

const BASE = process.env.BASE_URL ?? 'http://localhost:5173'
const USER = process.env.E2E_USER ?? 'admin'
const PASS = process.env.E2E_PASS ?? 'password123'
const TREE_MS = Number(process.env.TREE_MS ?? 2000)
const POPUP_MS = Number(process.env.POPUP_MS ?? 300)

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1366, height: 768 } })
const page = await ctx.newPage()

await page.goto(`${BASE}/login`)
await page.getByLabel('Tên đăng nhập').fill(USER)
await page.getByLabel('Mật khẩu').fill(PASS)
await page.getByRole('button', { name: 'Đăng nhập' }).click()
await page.waitForURL(/\/tree/)
await page.waitForFunction(() => document.documentElement.dataset.treeReady)
const clanUrl = page.url()

async function timeTree(p) {
  const t0 = Date.now()
  await p.goto(clanUrl)
  await p.waitForFunction(() => document.documentElement.dataset.treeReady, null, { timeout: 30000 })
  return Date.now() - t0
}

// lần đầu: xoá IndexedDB để mô phỏng máy mới
await page.evaluate(async () => {
  const dbs = await indexedDB.databases()
  await Promise.all(dbs.map((d) => new Promise((res) => { const r = indexedDB.deleteDatabase(d.name); r.onsuccess = r.onerror = r.onblocked = () => res() })))
})
const coldTreeMs = await timeTree(page)
const warmTreeMs = await timeTree(page)

// popup: tìm một người rồi bấm vào giữa canvas (nút được đưa về giữa sau khi tìm)
await page.getByPlaceholder('Tìm theo tên...').fill('Người 15')
await page.locator('.ant-select-item-option').first().click()
await page.waitForTimeout(800)
const box = await page.locator('canvas').first().boundingBox()
const t1 = Date.now()
await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2)
await page.waitForSelector('[data-person-ready="1"]', { timeout: 10000 })
const popupMs = Date.now() - t1

await browser.close()
const result = { coldTreeMs, warmTreeMs, popupMs, thresholds: { TREE_MS, POPUP_MS }, ok: coldTreeMs < TREE_MS && popupMs < POPUP_MS }
console.log(JSON.stringify(result, null, 2))
process.exit(result.ok ? 0 : 1)
