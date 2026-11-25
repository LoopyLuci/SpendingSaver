// Generates the PWA icons (PNG) and public/icon.svg without any image library.
// Design: the app's launcher colours (teal #006A60) with a white coin and an upward arrow ("saving").
// Run: npm run icons   (the generated files are committed; this only needs re-running if the design changes)
import { deflateSync } from 'node:zlib'
import { mkdirSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const out = join(here, '..', 'public')
mkdirSync(join(out, 'icons'), { recursive: true })

const BG = [0x00, 0x6a, 0x60]
const WHITE = [255, 255, 255]

// Arrow polygon in units of the coin radius, centred on the coin.
const ARROW = [[0, -0.62], [0.56, -0.02], [0.22, -0.02], [0.22, 0.58], [-0.22, 0.58], [-0.22, -0.02], [-0.56, -0.02]]

function inPoly(x, y, poly) {
  let inside = false
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [xi, yi] = poly[i], [xj, yj] = poly[j]
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside
  }
  return inside
}

/** kind: 'any' = rounded square with transparent corners; 'maskable' / 'apple' = full-bleed square. */
function render(size, kind) {
  const px = Buffer.alloc(size * size * 4)
  const coinR = kind === 'any' ? 0.3 : 0.25 // maskable keeps everything inside the 80% safe circle
  const corner = kind === 'any' ? 0.22 : 0
  const SS = 4
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    let r = 0, g = 0, b = 0, a = 0
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
      const u = (x + (sx + 0.5) / SS) / size, v = (y + (sy + 0.5) / SS) / size
      let inShape = true
      if (corner > 0) {
        const dx = Math.max(Math.abs(u - 0.5) - (0.5 - corner), 0), dy = Math.max(Math.abs(v - 0.5) - (0.5 - corner), 0)
        inShape = dx * dx + dy * dy <= corner * corner
      }
      if (!inShape) continue
      const inCoin = (u - 0.5) ** 2 + (v - 0.5) ** 2 <= coinR * coinR
      let c = BG
      if (inCoin) {
        const ax = (u - 0.5) / coinR, ay = (v - 0.5) / coinR
        c = inPoly(ax, ay, ARROW) ? BG : WHITE
      }
      r += c[0]; g += c[1]; b += c[2]; a += 255
    }
    const n = SS * SS, i = (y * size + x) * 4
    const alpha = a / n
    px[i] = alpha ? r / (a / 255) : 0
    px[i + 1] = alpha ? g / (a / 255) : 0
    px[i + 2] = alpha ? b / (a / 255) : 0
    px[i + 3] = alpha
  }
  return png(size, px)
}

const crcTable = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0 })
const crc = (buf) => { let c = 0xffffffff; for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0 }
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length)
  const td = Buffer.concat([Buffer.from(type), data])
  const c = Buffer.alloc(4); c.writeUInt32BE(crc(td))
  return Buffer.concat([len, td, c])
}
function png(size, rgba) {
  const raw = Buffer.alloc((size * 4 + 1) * size)
  for (let y = 0; y < size; y++) { raw[y * (size * 4 + 1)] = 0; rgba.copy(raw, y * (size * 4 + 1) + 1, y * size * 4, (y + 1) * size * 4) }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(size, 0); ihdr.writeUInt32BE(size, 4); ihdr[8] = 8; ihdr[9] = 6
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))])
}

const files = { 'icon-192.png': [192, 'any'], 'icon-512.png': [512, 'any'], 'icon-maskable-192.png': [192, 'maskable'], 'icon-maskable-512.png': [512, 'maskable'], 'apple-touch-icon.png': [180, 'apple'] }
for (const [name, [size, kind]] of Object.entries(files)) writeFileSync(join(out, 'icons', name), render(size, kind))

const arrow = ARROW.map(([x, y]) => `${(50 + x * 30).toFixed(1)},${(50 + y * 30).toFixed(1)}`).join(' ')
writeFileSync(join(out, 'icon.svg'), `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" role="img" aria-label="SpendingSaver">
  <rect width="100" height="100" rx="22" fill="#006A60"/>
  <circle cx="50" cy="50" r="30" fill="#fff"/>
  <polygon points="${arrow}" fill="#006A60"/>
</svg>
`)
console.log('icons written to', out)
