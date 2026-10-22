import { StrictMode } from 'react'
import { render } from '@testing-library/react'
import { TreeCanvas } from './TreeCanvas'

const instances: { canvas: HTMLCanvasElement; destroy: ReturnType<typeof vi.fn> }[] = []
vi.mock('./TreeRenderer', () => ({
  TreeRenderer: class {
    destroy = vi.fn()
    constructor(public canvas: HTMLCanvasElement) { instances.push(this as never) }
    setPhotoStore() {} invalidate() {} setData() {} setSelected() {}
  },
}))

test('StrictMode: một canvas sống, renderer đầu bị destroy', () => {
  const noop = () => {}
  const { container } = render(
    <StrictMode>
      <TreeCanvas graph={null} layout={null} keepCamera={false} selectedId={null}
        onHover={noop} onClick={noop} onToggle={noop} onViewChange={noop} />
    </StrictMode>,
  )
  expect(container.querySelectorAll('canvas')).toHaveLength(1)
  expect(instances.length).toBeGreaterThanOrEqual(2)
  expect(instances[0].destroy).toHaveBeenCalled()
  expect(instances[0].canvas).not.toBe(instances[1].canvas)
  expect(instances[0].canvas.isConnected).toBe(false)
  expect(instances[instances.length - 1].canvas.isConnected).toBe(true)
  expect(container.querySelector('canvas')).toBe(instances[instances.length - 1].canvas)
})

test('đặt data-tree-ready = số nút sau setData và xoá khi unmount', () => {
  const noop = () => {}
  const graph = { nodes: [] } as never
  const layout = { nodes: [{}, {}, {}] } as never
  const { unmount } = render(
    <TreeCanvas graph={graph} layout={layout} keepCamera={false} selectedId={null}
      onHover={noop} onClick={noop} onToggle={noop} onViewChange={noop} />,
  )
  expect(document.documentElement.dataset.treeReady).toBe('3')
  unmount()
  expect(document.documentElement.dataset.treeReady).toBeUndefined()
})

test('đổi graph/layout: dấu sẵn sàng được cập nhật, và xoá khi mất dữ liệu', () => {
  const noop = () => {}
  const mk = (graph: unknown, layout: unknown) => (
    <TreeCanvas graph={graph as never} layout={layout as never} keepCamera={false} selectedId={null}
      onHover={noop} onClick={noop} onToggle={noop} onViewChange={noop} />
  )
  const { rerender } = render(mk({ a: 1 }, { nodes: [{}, {}] }))
  expect(document.documentElement.dataset.treeReady).toBe('2')
  rerender(mk({ a: 2 }, { nodes: [{}, {}, {}, {}, {}] }))
  expect(document.documentElement.dataset.treeReady).toBe('5')
  rerender(mk(null, null))
  expect(document.documentElement.dataset.treeReady).toBeUndefined()
})
