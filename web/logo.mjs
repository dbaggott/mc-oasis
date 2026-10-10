// The site's pixel art, drawn at build time: the logo, the dirt background and
// the favicon.
//
// The Oasis SMP logo is drawn as an SVG of blocks, in the manner of
// Minecraft's edition logos: chunky pixel letters, each pixel a textured block
// with a bevel, extruded downward and outlined in black. No font is involved, so
// nothing here is anyone else's artwork.
//
// "OASIS" is a large sandstone line; "SMP" a smaller prismarine one beneath it.
import { crc32, deflateSync } from "node:zlib";

// One glyph per letter, '#' a block. Every row of a glyph is the same width.
const GLYPHS = {
  O: [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
  A: [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
  S: [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
  I: ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
  M: ["#...#", "##.##", "#.#.#", "#...#", "#...#", "#...#", "#...#"],
  P: ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
};

// Each block is drawn as SUB x SUB texture pixels.
const SUB = 4;

const TEXTURES = {
  sandstone: {
    face: ["#e3d39a", "#dccb8c", "#d6c381", "#e8daa6", "#d0bc78"],
    light: "#f1e6bf",
    dark: "#b9a463",
    side: "#8f7a3e",
  },
  prismarine: {
    face: ["#63a99c", "#5a9e91", "#6db3a5", "#529486", "#76bcae"],
    light: "#9fd8cb",
    dark: "#3f7a6e",
    side: "#285148",
  },
};

const OUTLINE = "#1a1a1a";

// A fixed hash, so the texture is the same at every build and the logo's bytes
// change only when its design does.
function noise(x, y, salt) {
  let h = (x * 374761393 + y * 668265263 + salt * 2147483647) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

// Lay a word out as block coordinates, one blank column between letters.
function layout(word) {
  const blocks = [];
  let x = 0;
  for (const letter of word) {
    const glyph = GLYPHS[letter];
    glyph.forEach((row, y) => {
      [...row].forEach((cell, dx) => {
        if (cell === "#") blocks.push([x + dx, y]);
      });
    });
    x += glyph[0].length + 1;
  }
  return { blocks, width: x - 1, height: 7 };
}

// Rectangles collected by colour and written as one <path> per colour, so the
// art is a few dozen elements rather than one per texture pixel.
function paths(rects) {
  const byColour = new Map();
  for (const [x, y, w, h, fill] of rects) {
    if (!byColour.has(fill)) byColour.set(fill, []);
    byColour.get(fill).push(`M${x} ${y}h${w}v${h}h${-w}z`);
  }
  return [...byColour].map(([fill, d]) => `<path fill="${fill}" d="${d.join("")}"/>`).join("");
}

// One word in texture-pixel units, each block SUB of them across, at (ox, oy).
// Drawn in three passes so each sits under the next: outline, extruded side,
// face.
function word(text, texture, ox, oy, salt) {
  const { blocks, width, height } = layout(text);
  const t = TEXTURES[texture];
  const depth = 3;
  const under = [];
  const face = [];

  for (const [bx, by] of blocks) {
    under.push([ox + bx * SUB - 1, oy + by * SUB - 1, SUB + 2, SUB + depth + 2, OUTLINE]);
  }
  for (const [bx, by] of blocks) {
    under.push([ox + bx * SUB, oy + by * SUB + SUB, SUB, depth, t.side]);
  }
  // The bevel runs along the letter's own edges, not each block's, so a letter
  // reads as one carved piece: lit where it faces up or left, shaded where it
  // faces down or right.
  const filled = new Set(blocks.map(([x, y]) => `${x},${y}`));
  const open = (x, y) => !filled.has(`${x},${y}`);
  for (const [bx, by] of blocks) {
    for (let sy = 0; sy < SUB; sy++) {
      for (let sx = 0; sx < SUB; sx++) {
        let fill = t.face[Math.floor(noise(bx * SUB + sx, by * SUB + sy, salt) * t.face.length)];
        if ((sy === 0 && open(bx, by - 1)) || (sx === 0 && open(bx - 1, by))) fill = t.light;
        if ((sy === SUB - 1 && open(bx, by + 1)) || (sx === SUB - 1 && open(bx + 1, by))) fill = t.dark;
        face.push([ox + bx * SUB + sx, oy + by * SUB + sy, 1, 1, fill]);
      }
    }
  }
  // Outline and side first, as one layer, so no block's outline covers its
  // neighbour's face.
  return { svg: paths(under) + paths(face), width: width * SUB, height: height * SUB + depth };
}

let logo;

// The whole logo as a standalone SVG file, served at /logo.svg for every page's
// <img> to share, rather than inlined into each page. Drawn once per process: it
// never varies. "OASIS" is drawn at 1.5 times the scale of "SMP".
export function logoSvg() {
  if (logo) return logo;
  const big = 1.5;
  const top = word("OASIS", "sandstone", 0, 0, 1);
  const bottom = word("SMP", "prismarine", 0, 0, 2);
  const pad = 2;
  const gap = 4;
  const width = top.width * big + 2 * pad;
  const height = top.height * big + gap + bottom.height + 2 * pad;
  logo = {
    width,
    height,
    svg: [
      `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" shape-rendering="crispEdges">`,
      `<g transform="translate(${pad} ${pad}) scale(${big})">${top.svg}</g>`,
      `<g transform="translate(${(width - bottom.width) / 2} ${pad + top.height * big + gap})">${bottom.svg}</g>`,
      "</svg>",
    ].join(""),
  };
  return logo;
}

// An <img> of the logo. Its width and height are the drawing's own, so the
// browser reserves the right shape before the file arrives; CSS sets the size.
export function logoImg(className) {
  const { width, height } = logoSvg();
  return `<img class="${className}" src="/logo.svg" alt="Oasis SMP" width="${width}" height="${height}" />`;
}

// A dirt tile, 16 by 16 texture pixels, darkened the way Minecraft darkens dirt
// behind its menus, for the page background to repeat.
const DIRT = ["#3b2a1e", "#34261b", "#43301f", "#2d2017", "#4a3626", "#382819"];

export function dirtSvg() {
  const size = 16;
  const rects = [];
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      rects.push([x, y, 1, 1, DIRT[Math.floor(noise(x, y, 3) * DIRT.length)]]);
    }
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" shape-rendering="crispEdges">${paths(rects)}</svg>`;
}

// The favicon: a thick sandstone "O", outlined in black, on a dirt tile, as
// 16 by 16 colours, row by row. Strokes three and four pixels wide, so the
// letter still reads at 16 pixels, a browser tab's size.
const ICON = 16;

function inO(x, y) {
  const corner = (x === 2 || x === 13) && (y === 2 || y === 13);
  const outer = x >= 2 && x <= 13 && y >= 2 && y <= 13 && !corner;
  const hole = x >= 6 && x <= 9 && y >= 5 && y <= 10;
  return outer && !hole;
}

function faviconPixels() {
  const t = TEXTURES.sandstone;
  const pixels = [];
  for (let y = 0; y < ICON; y++) {
    for (let x = 0; x < ICON; x++) {
      let fill = DIRT[Math.floor(noise(x, y, 3) * DIRT.length)];
      if (inO(x, y)) {
        fill = t.face[Math.floor(noise(x, y, 1) * t.face.length)];
        // A light rim on every side rather than the logo's top-left light and
        // bottom-right shade: at tab size a shaded edge merges into the black
        // outline and the letter looks pushed up and to the left.
        const edge = !inO(x, y - 1) || !inO(x - 1, y) || !inO(x, y + 1) || !inO(x + 1, y);
        if (edge) fill = t.light;
      } else if (
        [-1, 0, 1].some((dx) => [-1, 0, 1].some((dy) => inO(x + dx, y + dy)))
      ) {
        fill = OUTLINE;
      }
      pixels.push(fill);
    }
  }
  return pixels;
}

export function faviconSvg() {
  const rects = faviconPixels().map((fill, i) => [i % ICON, Math.floor(i / ICON), 1, 1, fill]);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${ICON} ${ICON}" shape-rendering="crispEdges">${paths(rects)}</svg>`;
}

// The favicon as a PNG `size` pixels square, for browsers and home screens that
// take no SVG icon. Each output pixel takes the colour of the icon pixel it
// falls in, so the art stays hard-edged at any size.
export function faviconPng(size) {
  const pixels = faviconPixels();
  const raw = Buffer.alloc(size * (1 + size * 3));
  for (let y = 0; y < size; y++) {
    const row = y * (1 + size * 3);
    raw[row] = 0; // no filter
    for (let x = 0; x < size; x++) {
      const hex = pixels[Math.floor((y * ICON) / size) * ICON + Math.floor((x * ICON) / size)];
      raw.write(hex.slice(1), row + 1 + x * 3, "hex");
    }
  }
  const chunk = (type, data) => {
    const length = Buffer.alloc(4);
    length.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(body));
    return Buffer.concat([length, body, crc]);
  };
  const header = Buffer.alloc(13);
  header.writeUInt32BE(size, 0);
  header.writeUInt32BE(size, 4);
  header[8] = 8; // bits per channel
  header[9] = 2; // RGB
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}
