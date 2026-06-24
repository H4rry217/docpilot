import '@testing-library/jest-dom/vitest'

Object.defineProperty(window, 'scrollBy', {
  configurable: true,
  value: () => {}
})

Object.defineProperty(document, 'elementFromPoint', {
  configurable: true,
  value: () => null
})

Object.defineProperty(document, 'elementsFromPoint', {
  configurable: true,
  value: () => []
})

Object.defineProperty(HTMLCanvasElement.prototype, 'getContext', {
  configurable: true,
  value(this: HTMLCanvasElement) {
    return {
      canvas: this,
      clearRect: () => {},
      fillText: () => {},
      fillStyle: '',
      font: '',
      getImageData: () => ({ data: new Uint8ClampedArray(4) }),
      measureText: () => ({ width: 0 }),
      textBaseline: ''
    } as unknown as CanvasRenderingContext2D
  }
})
