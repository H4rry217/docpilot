let fallbackCounter = 0

export function createClientMutationId(): string {
  const cryptoProvider = globalThis.crypto
  if (cryptoProvider && typeof cryptoProvider.randomUUID === 'function') {
    return cryptoProvider.randomUUID()
  }

  if (cryptoProvider && typeof cryptoProvider.getRandomValues === 'function') {
    return uuidFromRandomValues(cryptoProvider)
  }

  fallbackCounter = (fallbackCounter + 1) % Number.MAX_SAFE_INTEGER
  return `mutation-${Date.now().toString(36)}-${fallbackCounter.toString(36)}`
}

function uuidFromRandomValues(cryptoProvider: Crypto): string {
  const bytes = new Uint8Array(16)
  cryptoProvider.getRandomValues(bytes)
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80

  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0'))
  return [
    hex.slice(0, 4).join(''),
    hex.slice(4, 6).join(''),
    hex.slice(6, 8).join(''),
    hex.slice(8, 10).join(''),
    hex.slice(10, 16).join('')
  ].join('-')
}
