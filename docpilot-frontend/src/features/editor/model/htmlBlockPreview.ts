export type HtmlBlockDisplayMode = 'fixed' | 'auto' | 'fit'

export const DEFAULT_HTML_BLOCK_HEIGHT = 320

export function normalizeDisplayMode(value: unknown): Exclude<HtmlBlockDisplayMode, 'fit'> {
  return value === 'auto' ? 'auto' : 'fixed'
}

export function numberAttr(value: unknown, fallback: number): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) return fallback
  return Math.max(120, Math.min(1600, Math.round(value)))
}

export function booleanAttr(value: unknown): boolean {
  return value === true || value === 'true'
}

export function sandboxForHtmlBlock(allowScripts: boolean): string {
  return allowScripts ? 'allow-scripts' : 'allow-same-origin'
}

export function createHtmlPreviewDocument(source: string, blockId: string, enableHeightReporter: boolean): string {
  const reporter = enableHeightReporter ? heightReporterScript(blockId) : ''
  if (isFullHtmlDocument(source)) {
    return injectIntoFullDocument(source, reporter)
  }

  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <base target="_blank" />
  <style>
    html, body { margin: 0; padding: 0; }
    body { font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
  </style>
</head>
<body>
${source}
${reporter}
</body>
</html>`
}

function isFullHtmlDocument(source: string): boolean {
  return /<!doctype\s+html/i.test(source) || /<html[\s>]/i.test(source)
}

function injectIntoFullDocument(source: string, script: string): string {
  if (!script) return source
  if (/<\/body>/i.test(source)) {
    return source.replace(/<\/body>/i, `${script}</body>`)
  }
  return `${source}${script}`
}

function heightReporterScript(blockId: string): string {
  return `<script>
(function () {
  var blockId = ${JSON.stringify(blockId)};
  function height() {
    var body = document.body || document.documentElement;
    var root = document.documentElement || body;
    return Math.max(
      body ? body.scrollHeight : 0,
      body ? body.offsetHeight : 0,
      root ? root.scrollHeight : 0,
      root ? root.offsetHeight : 0
    );
  }
  function report() {
    parent.postMessage({ type: 'docpilot-html-block-height', id: blockId, height: height() }, '*');
  }
  window.addEventListener('load', report);
  window.addEventListener('resize', report);
  if (window.ResizeObserver) {
    new ResizeObserver(report).observe(document.documentElement);
  }
  setTimeout(report, 0);
  setTimeout(report, 250);
})();
</script>`
}
