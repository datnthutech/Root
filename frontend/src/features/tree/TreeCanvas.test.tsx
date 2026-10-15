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
  expect(container.querySelector('canvas')).toBe(instances[instances.length - 1].canvas)
})
