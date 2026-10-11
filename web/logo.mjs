// The site's pixel art, drawn at build time: the logo and the favicon.
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

// Lay a word out as block coordinates, one blank column between letters, each
// block marked with the letter it belongs to (its index and its character) and
// its column within that letter's glyph.
function layout(word) {
  const blocks = [];
  let x = 0;
  [...word].forEach((letter, i) => {
    const glyph = GLYPHS[letter];
    glyph.forEach((row, y) => {
      [...row].forEach((cell, dx) => {
        if (cell === "#") blocks.push({ x: x + dx, y, index: i, letter, column: dx });
      });
    });
    x += glyph[0].length + 1;
  });
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
// face. Alongside the art, its blocks, each with the rectangle of each pass it
// is drawn in: together a letter's outlines cover exactly the letter, its black
// border included.
function word(text, texture, ox, oy, salt) {
  const { blocks, width, height } = layout(text);
  const t = TEXTURES[texture];
  const depth = 3;
  const under = [];
  const face = [];
  const outlined = blocks.map((block) => ({
    ...block,
    outline: [ox + block.x * SUB - 1, oy + block.y * SUB - 1, SUB + 2, SUB + depth + 2],
    side: [ox + block.x * SUB, oy + block.y * SUB + SUB, SUB, depth],
    face: [ox + block.x * SUB, oy + block.y * SUB, SUB, SUB],
  }));

  for (const { outline } of outlined) under.push([...outline, OUTLINE]);
  for (const { side } of outlined) under.push([...side, t.side]);
  // The bevel runs along the letter's own edges, not each block's, so a letter
  // reads as one carved piece: lit where it faces up or left, shaded where it
  // faces down or right.
  const filled = new Set(blocks.map(({ x, y }) => `${x},${y}`));
  const open = (x, y) => !filled.has(`${x},${y}`);
  for (const { x: bx, y: by } of blocks) {
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
  return {
    svg: paths(under) + paths(face),
    blocks: outlined,
    texture,
    rows: height,
    width: width * SUB,
    height: height * SUB + depth,
  };
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
  // Where each word is placed in the logo, and at what scale.
  top.place = { x: pad, y: pad, scale: big };
  bottom.place = { x: (width - bottom.width) / 2, y: pad + top.height * big + gap, scale: 1 };
  const transform = ({ x, y, scale }) => `translate(${x} ${y})${scale === 1 ? "" : ` scale(${scale})`}`;
  const topPlace = transform(top.place);
  const bottomPlace = transform(bottom.place);
  const letters = ({ blocks }) => Object.values(Object.groupBy(blocks, ({ index }) => index)).map((group) => group.map((block) => block.outline));
  // The block in the middle of the M, where its two strokes meet.
  const mCentre = bottom.blocks.find(({ letter, column, y }) => letter === "M" && column === 2 && y === 2);
  if (!mCentre) throw new Error("The logo's M has no centre block for the creeper to come out of.");
  logo = {
    width,
    height,
    words: [top, bottom],
    mCentre,
    // What each target does when clicked, the rectangles it covers, in its
    // word's units, and where that word is placed in the logo: each letter of
    // "OASIS" and "SMP" as one change the sky, and the M's centre block, over
    // "SMP", lets out the creeper.
    targets: [
      ...letters(top).map((rects) => ({ egg: "sky", place: topPlace, rects })),
      { egg: "sky", place: bottomPlace, rects: letters(bottom).flat() },
      { egg: "creeper", place: bottomPlace, rects: [mCentre.outline] },
    ],
    svg: [
      `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" shape-rendering="crispEdges">`,
      `<g transform="${topPlace}">${top.svg}</g>`,
      `<g transform="${bottomPlace}">${bottom.svg}</g>`,
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

// Invisible targets over the logo, in logoSvg's order, each marked with what
// it does and shaped like what it covers, so a click in a letter's hole or
// between letters misses. Later targets lie over earlier ones. Drawn in the
// logo's units so they scale with its <img>; laid on top of it by the
// stylesheet; main.js gives them their clicks.
export function logoTargets() {
  const { width, height, targets } = logoSvg();
  const shapes = targets.map(
    ({ egg, place, rects }) =>
      `<path data-egg="${egg}" transform="${place}" d="${rects.map(([x, y, w, h]) => `M${x} ${y}h${w}v${h}h${-w}z`).join("")}"/>`,
  );
  return `<svg class="logo-targets" viewBox="0 0 ${width} ${height}" aria-hidden="true">${shapes.join("")}</svg>`;
}

// The logo as blocks, served at /logo-blocks.json for src/creeper.js to draw
// the logo from a block at a time, so the creeper can blow blocks away: which
// block the creeper starts behind (the one whose target lets it out), each
// block's word, the block it is made of, its place in its word's grid, and,
// in the logo's units, the rectangle of each pass it is drawn in; the colours
// of the outline and the sides; and the line along the foot of "SMP", where
// the ground is.
export function logoBlocks() {
  const { width, height, words, mCentre } = logoSvg();
  const blocks = words.flatMap(({ blocks, texture, place }, word) => {
    const placed = ([x, y, w, h]) => [place.x + x * place.scale, place.y + y * place.scale, w * place.scale, h * place.scale];
    return blocks.map((block) => ({
      ...(block === mCentre && { creeperStart: true }),
      word,
      block: texture,
      column: block.x,
      row: block.y,
      letter: block.letter,
      letterColumn: block.column,
      outline: placed(block.outline),
      side: placed(block.side),
      face: placed(block.face),
    }));
  });
  const bottom = words.at(-1);
  const colours = { outline: OUTLINE, ...Object.fromEntries(words.map(({ texture }) => [texture, TEXTURES[texture].side])) };
  return { width, height, ground: bottom.place.y + bottom.rows * SUB, colours, blocks };
}

// Dirt, darkened the way Minecraft darkens it behind its menus, for the
// favicon's ground.
const DIRT = ["#3b2a1e", "#34261b", "#43301f", "#2d2017", "#4a3626", "#382819"];

// The favicon: the logo's sandstone "O", outlined in black, on a dirt tile, as
// 16 by 16 colours, row by row. Each block of the glyph becomes a cell of
// pixels, the side columns wider than the rest so the strokes still read at 16
// pixels, a browser tab's size.
const ICON = 16;
const O_COLUMNS = [3, 2, 2, 2, 3];
const O_ROWS = [2, 2, 2, 2, 2, 2, 2];

// Which of `sizes` the pixel at `offset` falls in, or -1 outside them all.
function cellAt(sizes, offset) {
  for (let i = 0; i < sizes.length; i++) {
    if (offset >= 0 && offset < sizes[i]) return i;
    offset -= sizes[i];
  }
  return -1;
}

function inO(x, y) {
  const sum = (sizes) => sizes.reduce((a, b) => a + b);
  const column = cellAt(O_COLUMNS, x - (ICON - sum(O_COLUMNS)) / 2);
  const row = cellAt(O_ROWS, y - (ICON - sum(O_ROWS)) / 2);
  return column >= 0 && row >= 0 && GLYPHS.O[row][column] === "#";
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
