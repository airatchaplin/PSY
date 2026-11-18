const { writeFileSync, mkdirSync } = require("fs");
const { deflateSync } = require("zlib");
const path = require("path");

function crc32(buf) {
  let c = ~0;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf[i];
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return ~c >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const typeBuf = Buffer.from(type);
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])));
  return Buffer.concat([len, typeBuf, data, crcBuf]);
}

function png(size, draw) {
  const raw = Buffer.alloc((size * 4 + 1) * size);
  for (let y = 0; y < size; y++) {
    const row = y * (size * 4 + 1);
    raw[row] = 0;
    for (let x = 0; x < size; x++) {
      const [r, g, b, a] = draw(x, y, size);
      const i = row + 1 + x * 4;
      raw[i] = r; raw[i + 1] = g; raw[i + 2] = b; raw[i + 3] = a;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  return Buffer.concat([sig, chunk("IHDR", ihdr), chunk("IDAT", deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]);
}

function roundedRect(x, y, size, rr) {
  const cx1 = rr, cy1 = rr, cx2 = size - rr - 1, cy2 = size - rr - 1;
  if (x >= rr && x <= size - rr - 1 && y >= rr && y <= size - rr - 1) return true;
  if (x >= rr && x <= size - rr - 1 && y >= 0 && y < size) return true;
  if (y >= rr && y <= size - rr - 1 && x >= 0 && x < size) return true;
  const corners = [[cx1, cy1], [cx2, cy1], [cx1, cy2], [cx2, cy2]];
  for (const [cx, cy] of corners) {
    const dx = x - cx, dy = y - cy;
    if (dx * dx + dy * dy <= rr * rr) return true;
  }
  return false;
}

function letterMask(ch, x, y, size) {
  // Simple block-letter mask in normalized 0..1 space of the icon content box
  const pad = size * 0.18;
  const w = size - pad * 2;
  const h = size - pad * 2;
  const nx = (x - pad) / w;
  const ny = (y - pad) / h;
  if (nx < 0 || ny < 0 || nx > 1 || ny > 1) return false;
  const t = Math.max(0.08, 0.11 * (64 / Math.max(size, 64)));
  if (ch === "P") {
    if (nx < t || ny < t) return true;
    if (ny > 0.45 && ny < 0.45 + t && nx < 0.72) return true;
    if (nx > 0.72 - t && nx < 0.72 && ny < 0.45) return true;
    return false;
  }
  if (ch === "S") {
    if (ny < t && nx < 0.85) return true;
    if (ny > 0.45 - t / 2 && ny < 0.45 + t / 2) return true;
    if (ny > 1 - t && nx > 0.15) return true;
    if (nx < t && ny < 0.45) return true;
    if (nx > 1 - t && ny > 0.45) return true;
    return false;
  }
  if (ch === "Y") {
    if (nx > 0.5 - t / 2 && nx < 0.5 + t / 2 && ny > 0.42) return true;
    const d1 = Math.abs((ny) - (nx) * 0.9);
    const d2 = Math.abs((ny) - (1 - nx) * 0.9);
    if (ny < 0.48 && (d1 < t * 0.9 || d2 < t * 0.9)) return true;
    return false;
  }
  return false;
}

function drawIcon(x, y, size, maskable) {
  const bg = [142, 58, 116, 255]; // rose primary-ish
  const ink = [255, 248, 252, 255];
  const rr = maskable ? size * 0.08 : size * 0.22;
  if (!roundedRect(x, y, size, rr)) return [0, 0, 0, 0];
  // soft top highlight
  const glow = Math.max(0, 1 - (y / size) * 1.4) * 28;
  const base = [Math.min(255, bg[0] + glow), Math.min(255, bg[1] + glow * 0.5), Math.min(255, bg[2] + glow * 0.7), 255];
  // three letters across
  const slots = ["P", "S", "Y"];
  const cell = size / 3;
  const col = Math.min(2, Math.floor(x / cell));
  const lx = x - col * cell;
  const scaled = size * 0.92;
  // remap letter local coords into full-icon letterMask space for one letter cell
  const mx = (lx / cell) * size;
  const my = ((y - size * 0.08) / (size * 0.84)) * size;
  if (letterMask(slots[col], mx, my, size)) return ink;
  return base;
}

mkdirSync(path.join("public"), { recursive: true });
const outs = [
  ["pwa-192x192.png", 192, false],
  ["pwa-512x512.png", 512, false],
  ["maskable-icon-512x512.png", 512, true],
  ["apple-touch-icon.png", 180, false],
  ["pwa-64x64.png", 64, false],
];
for (const [name, size, maskable] of outs) {
  writeFileSync(path.join("public", name), png(size, (x, y, s) => drawIcon(x, y, s, maskable)));
  console.log("wrote", name);
}
