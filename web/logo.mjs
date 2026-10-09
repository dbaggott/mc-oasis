// The site's pixel art, drawn at build time: the logo and the dirt background.
//
// The Oasis SMP logo is drawn as an SVG of blocks, in the manner of
// Minecraft's edition logos: chunky pixel letters, each pixel a textured block
// with a bevel, extruded downward and outlined in black. No font is involved, so
// nothing here is anyone else's artwork.
//
// "OASIS" is a large sandstone line; "SMP" a smaller prismarine one beneath it.

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

// One word's rects, in texture pixels of size `px`, at (ox, oy). Drawn in three
// passes so each sits under the next: outline, extruded side, face.
function word(text, texture, px, ox, oy, salt) {
  const { blocks, width, height } = layout(text);
  const t = TEXTURES[texture];
  const cell = SUB * px;
  const depth = Math.round(cell * 0.6);
  const edge = px;
  const out = [];
  const rect = (x, y, w, h, fill) =>
    out.push(`<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${fill}"/>`);

  for (const [bx, by] of blocks) {
    rect(ox + bx * cell - edge, oy + by * cell - edge, cell + 2 * edge, cell + depth + 2 * edge, OUTLINE);
  }
  for (const [bx, by] of blocks) {
    rect(ox + bx * cell, oy + by * cell + cell, cell, depth, t.side);
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
        rect(ox + bx * cell + sx * px, oy + by * cell + sy * px, px, px, fill);
      }
    }
  }
  return { svg: out.join(""), width: width * cell, height: height * cell + depth };
}

// The whole logo as one <svg>, sized by its viewBox so CSS sets its width.
export function logoSvg({ label = "Oasis SMP" } = {}) {
  const pad = 6;
  const top = word("OASIS", "sandstone", 6, 0, 0, 1);
  const bottom = word("SMP", "prismarine", 4, 0, 0, 2);
  const width = Math.max(top.width, bottom.width) + 2 * pad;
  const gap = 18;
  const height = top.height + gap + bottom.height + 2 * pad;
  const shift = (w) => Math.round((width - w) / 2);
  return [
    `<svg class="logo-art" viewBox="0 0 ${width} ${height}" role="img" aria-label="${label}" xmlns="http://www.w3.org/2000/svg" shape-rendering="crispEdges">`,
    `<g transform="translate(${shift(top.width)} ${pad})">${top.svg}</g>`,
    `<g transform="translate(${shift(bottom.width)} ${pad + top.height + gap})">${bottom.svg}</g>`,
    "</svg>",
  ].join("");
}

// A dirt tile, 16 by 16 texture pixels, darkened the way Minecraft darkens dirt
// behind its menus, for the page background to repeat.
const DIRT = ["#3b2a1e", "#34261b", "#43301f", "#2d2017", "#4a3626", "#382819"];

export function dirtSvg() {
  const size = 16;
  const rects = [];
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const fill = DIRT[Math.floor(noise(x, y, 3) * DIRT.length)];
      rects.push(`<rect x="${x}" y="${y}" width="1" height="1" fill="${fill}"/>`);
    }
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" shape-rendering="crispEdges">${rects.join("")}</svg>`;
}
