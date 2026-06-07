const SVG_STYLE_PROPERTIES = [
  'color',
  'dominant-baseline',
  'fill',
  'font-family',
  'font-size',
  'font-style',
  'font-weight',
  'letter-spacing',
  'stroke',
  'stroke-linecap',
  'stroke-width',
  'text-anchor'
]

function copyComputedSvgStyles(sourceSvg: SVGSVGElement, targetSvg: SVGSVGElement) {
  const sourceElements = [sourceSvg, ...sourceSvg.querySelectorAll('*')]
  const targetElements = [targetSvg, ...targetSvg.querySelectorAll('*')]

  for (let index = 0; index < sourceElements.length; index += 1) {
    const sourceElement = sourceElements[index]
    const targetElement = targetElements[index]
    if (!targetElement) continue

    const computedStyle = window.getComputedStyle(sourceElement)
    const declarations = SVG_STYLE_PROPERTIES
      .map((property) => {
        const value = computedStyle.getPropertyValue(property)
        return value ? `${property}: ${value}` : ''
      })
      .filter(Boolean)

    if (declarations.length) {
      targetElement.setAttribute('style', declarations.join('; '))
    }
  }
}

export function canvasToPngBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) {
        resolve(blob)
        return
      }
      reject(new Error('Failed to create diagram image'))
    }, 'image/png')
  })
}

function loadImage(source: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('Failed to load diagram image'))
    image.src = source
  })
}

export async function svgToPngBlob(svg: SVGSVGElement): Promise<Blob> {
  const clonedSvg = svg.cloneNode(true) as SVGSVGElement
  copyComputedSvgStyles(svg, clonedSvg)

  const rect = svg.getBoundingClientRect()
  const viewBox = svg.viewBox.baseVal
  const width = Math.max(1, Math.round(rect.width || viewBox.width || 640))
  const height = Math.max(1, Math.round(rect.height || viewBox.height || 360))
  clonedSvg.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
  clonedSvg.setAttribute('width', String(width))
  clonedSvg.setAttribute('height', String(height))

  if (!clonedSvg.getAttribute('viewBox') && viewBox.width > 0 && viewBox.height > 0) {
    clonedSvg.setAttribute('viewBox', `${viewBox.x} ${viewBox.y} ${viewBox.width} ${viewBox.height}`)
  }

  const serializedSvg = new XMLSerializer().serializeToString(clonedSvg)
  const svgBlob = new Blob([serializedSvg], { type: 'image/svg+xml;charset=utf-8' })
  const imageUrl = URL.createObjectURL(svgBlob)

  try {
    const image = await loadImage(imageUrl)
    const scale = Math.max(1, Math.min(3, window.devicePixelRatio || 1))
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(width * scale)
    canvas.height = Math.round(height * scale)

    const context = canvas.getContext('2d')
    if (!context) {
      throw new Error('Canvas is not available')
    }

    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.drawImage(image, 0, 0, canvas.width, canvas.height)
    return await canvasToPngBlob(canvas)
  } finally {
    URL.revokeObjectURL(imageUrl)
  }
}
