// The creeper's numbers against the game's: each expected value is worked from
// the Minecraft 26.1 class src/creeper.js names beside it.
import assert from "node:assert/strict";
import { test } from "node:test";
import {
  BLAST_RESISTANCE,
  blastedPlaces,
  blocksInReach,
  brightness,
  creeperWorld,
  explosionParticle,
  firstRoute,
  legSwing,
  logoWorld,
  poofParticle,
  smokeParticle,
  spawnedRoute,
  spriteIndex,
  swellScale,
  whiteness,
} from "../src/creeper.js";
import { logoBlocks } from "../logo.mjs";

const close = (actual, expected, label) => assert.ok(Math.abs(actual - expected) < 1e-3, `${label}: ${actual} is not ${expected}`);

test("the fuse flashes white on every other tenth of its way, never under half strength", () => {
  assert.equal(whiteness(0.05), 0);
  close(whiteness(0.15), 1 - 165 / 255, "first flash");
  assert.equal(whiteness(0.25), 0);
  close(whiteness(0.95), 1 - 76 / 255, "last flash");
});

test("the fuse swells the creeper wider far more than taller", () => {
  assert.deepEqual(swellScale(0), { wide: 1, tall: 1 });
  const { wide, tall } = swellScale(1);
  const wobble = 1 + Math.sin(100) * 0.01;
  close(wide, 1.4 * wobble, "wide");
  close(tall, 1.1 / wobble, "tall");
});

test("faces are lit as the game lights a mob", () => {
  close(brightness([0, 1, 0]), 1, "top");
  close(brightness([0, -1, 0]), 0.4, "bottom");
  close(brightness([0, 0, 1]), 0.4 + 0.6 * (0.7 / Math.hypot(0.2, 1, 0.7)), "south");
  close(brightness([1, 0, 0]), 0.4 + 0.6 * (0.2 / Math.hypot(0.2, 1, 0.7)), "east");
});

test("the legs swing in diagonal pairs", () => {
  const legs = legSwing(1.3, 0.5);
  assert.equal(legs.rightHindLeg, legs.leftFrontLeg);
  assert.equal(legs.leftHindLeg, legs.rightFrontLeg);
  close(legs.rightHindLeg, -legs.leftHindLeg, "opposite pairs");
  close(legSwing(0, 0.5).leftHindLeg, 0.7, "full swing");
});

test("a particle runs through all its frames, first to last", () => {
  assert.equal(spriteIndex(0, 7, 16), 0);
  assert.equal(spriteIndex(7, 7, 16), 15);
  assert.equal(spriteIndex(9, 9, 8), 7);
});

// Open air over grass, with one block of `block` whose near face is `distance`
// blocks from the blast, level with it.
function wallAt(distance, block) {
  return (x, y, z) => {
    const cell = [x, y, z].map(Math.floor);
    if (x >= distance && x < distance + 1 && y >= 0 && y < 1 && z >= 0 && z < 1) {
      return { key: "wall", place: "wall", resistance: BLAST_RESISTANCE[block] };
    }
    if (y < 0) return { key: cell.join(), place: "grass_block", resistance: BLAST_RESISTANCE.grass_block };
    return { key: cell.join(), place: "air", resistance: null };
  };
}

test("at its average power, a creeper's blast breaks sandstone 3 blocks off, but prismarine only 1 block off", () => {
  const average = () => 0.5;
  const center = [0, 0.5, 0.5];
  assert.ok(blastedPlaces(center, 3, wallAt(3, "sandstone"), average).has("wall"));
  assert.ok(blastedPlaces(center, 3, wallAt(1, "prismarine"), average).has("wall"));
  assert.ok(!blastedPlaces(center, 3, wallAt(1.5, "prismarine"), average).has("wall"));
});

test("an explosion's particles start as their classes make them", () => {
  const midway = () => 0.5;
  const big = explosionParticle([0, 0, 0], 0, midway);
  assert.deepEqual([big.halfSize, big.lifetime, big.shade], [2, 8, 0.7]);
  close(explosionParticle([0, 0, 0], 7 / 8, midway).halfSize, 1.125, "last tick's explosions");
  const poof = poofParticle([0, 0, 0], [0.1, 0, 0], midway);
  assert.deepEqual([poof.xd, poof.lifetime, poof.friction, poof.gravity], [0.1, 28, 0.9, -0.1]);
  const smoke = smokeParticle([0, 0, 0], [0.1, 0, 0], midway);
  assert.deepEqual([smoke.lifetime, smoke.friction, smoke.speedUpWhenYMotionIsBlocked], [13, 0.96, true]);
  close(smoke.shade, 0.15, "smoke is dark");
});

// A level of open air over grass, with a route straight toward the viewer.
const level = (broken = []) => ({
  player: { x: 0, y: 1.62, z: 24 },
  route: [
    { x: 0, y: 0, z: -3 },
    { x: 0, y: 0, z: 3 },
  ],
  resistanceAt: wallAt(100, "sandstone"),
  breakBlocks: (places) => broken.push(...places),
});

test("the creeper walks at its full speed of about 2.7 blocks a second", () => {
  const world = creeperWorld(level());
  for (let i = 0; i < 12; i++) world.tick();
  const was = { ...world.creeper };
  world.tick();
  close(Math.hypot(world.creeper.x - was.x, world.creeper.z - was.z), (0.25 * 0.98 * 0.25) / (1 - 0.6 * 0.91), "stride");
});

test("it stops, burns a 30-tick fuse, and its explosion sets off 48 large explosions over 8 ticks", () => {
  const world = creeperWorld(level());
  let ticks = 0;
  while (!world.creeper.fusing) {
    world.tick();
    assert.ok(++ticks < 200, "it never stopped to fuse");
  }
  const lit = ticks;
  while (world.creeper.alive) {
    world.tick();
    ticks++;
  }
  // The tick that lights the fuse burns its first tick of it.
  assert.equal(ticks - lit, 29);

  const explosions = new Set();
  const sizes = new Set();
  const smoke = new Set();
  while (world.tick()) {
    for (const p of world.particles) {
      if (p.kind === "explosion") {
        explosions.add(p);
        sizes.add(p.halfSize);
      } else smoke.add(p);
    }
    assert.ok(++ticks < 400, "the explosion never cleared");
  }
  assert.equal(explosions.size, 48);
  assert.deepEqual([...sizes].sort(), [1.125, 1.25, 1.375, 1.5, 1.625, 1.75, 1.875, 2]);
  assert.ok(smoke.size > 0, "no poof or smoke thrown out");
});

test("the blast's first explosions show on the tick its blocks break", () => {
  const broken = [];
  const world = creeperWorld(level(broken));
  for (let ticks = 0; world.creeper.alive; ticks++) {
    world.tick();
    assert.ok(ticks < 400, "it never blew up");
  }
  assert.ok(broken.length > 0, "it broke nothing");
  assert.equal(world.particles.filter(({ kind }) => kind === "explosion").length, 6);
});

test("its blast is over once its large explosions are, before its smoke clears", () => {
  const world = creeperWorld(level());
  let ticks = 0;
  while (!world.blastOver) {
    assert.ok(world.tick(), "it cleared before its blast was over");
    assert.ok(++ticks < 400, "its blast was never over");
  }
  assert.ok(!world.creeper.alive);
  assert.ok(world.particles.length > 0, "no smoke left once the blast was over");
  assert.ok(world.particles.every(({ kind }) => kind !== "explosion"));
});

test("without effects, the blast still breaks blocks but throws out nothing", () => {
  const broken = [];
  const world = creeperWorld(level(broken), { walk: false, effects: false });
  while (world.tick()) assert.equal(world.particles.length, 0);
  assert.ok(broken.length > 0);
});

// The logo's world, from the blocks the build serves, fresh each time.
const logo = () => logoWorld(structuredClone(logoBlocks()));
const player = { x: 0, y: 1.62, z: 24 };
const blockAt = (world, find) => world.solidOf(find).block;
const topOf = (world, word) => Math.max(...world.solidsOf().filter(({ block }) => block.word === word).map(({ max }) => max[1]));

// Runs a creeper along `route` in `world` until its explosion clears, without
// the particles, and returns what it broke.
function explodeOn(world, route, random = () => 0.5) {
  const broken = [];
  const run = creeperWorld(
    { ...world, player, route, breakBlocks: (places) => broken.push(...world.breakBlocks(places)) },
    { effects: false, random },
  );
  for (let ticks = 0; run.tick(); ticks++) assert.ok(ticks < 400, "it never blew up");
  return broken;
}

test("a creeper can stand on the ground and on the top of every letter", () => {
  const world = logo();
  const ledges = world.ledges();
  assert.equal(ledges[0].top, 0, "no ground");
  const heights = new Set(ledges.map(({ top }) => top));
  assert.equal(topOf(world, 1), 7, "SMP isn't 7 blocks tall");
  assert.ok(heights.has(7), "no ledge along the top of SMP");
  assert.ok(heights.has(topOf(world, 0)), "no ledge along the top of OASIS");
  // The O's top is one run, three of its blocks wide.
  const o = world.solidsOf().filter(({ block }) => block.letter === "O");
  assert.ok(ledges.some(({ top, from, to }) => top === topOf(world, 0) && to - from === 3 * 1.5 && from > Math.min(...o.map(({ min }) => min[0]))));
});

test("a block breaks to leave a ledge on the block beneath it", () => {
  const world = logo();
  const top = blockAt(world, ({ letter, word, row, letterColumn }) => word === 1 && letter === "P" && row === 0 && letterColumn === 0);
  const below = world.solidOf((block) => block.word === 1 && block.column === top.column && block.row === 1);
  const hasLedgeOn = (solid) => world.ledges().some(({ top, from, to }) => top === solid.max[1] && from <= solid.min[0] && to >= solid.max[0]);
  assert.ok(!hasLedgeOn(below));
  assert.deepEqual(world.breakBlocks([top]), [top]);
  assert.ok(!world.isStanding(top));
  assert.ok(hasLedgeOn(below));
});

test("the M's centre breaks to the bedrock under it, which no blast breaks", () => {
  const world = logo();
  const centre = blockAt(world, ({ creeperStart }) => creeperStart);
  assert.equal(centre.block, "prismarine");
  world.breakBlocks([centre]);
  assert.ok(world.isStanding(centre));
  assert.equal(centre.block, "bedrock");
  const solid = world.solidOf(({ creeperStart }) => creeperStart);
  const at = [(solid.min[0] + solid.max[0]) / 2, solid.min[1] - 0.5, 0.6];
  assert.ok(!blastedPlaces(at, 3, world.resistanceAt, () => 1).has(centre));
});

test("the first creeper walks out from behind the M to the P's foot", () => {
  const route = firstRoute(logo());
  assert.ok(route[0].z < 0, "it doesn't start behind the letters");
  assert.ok(route.at(-1).z > 0, "it doesn't stop in front of them");
  assert.ok(route.every(({ y }) => y === route[0].y), "it doesn't keep to the ground");
});

test("a spawned creeper strolls along its ledge, in front of the letters, its feet over the ledge", () => {
  const world = logo();
  const ledges = world.ledges();
  for (let seed = 0; seed < 200; seed++) {
    let n = seed;
    // A fixed run of numbers, so a failure repeats.
    const random = () => ((n = (n * 1103515245 + 12345) % 2147483648) / 2147483648);
    const route = spawnedRoute(world, random);
    // It stands where the player sees it on its ledge's front edge, within
    // half a block of the ledge's height; `seen` is where on the edge.
    const seen = (x, z) => (x * player.z) / (player.z - z);
    const ledge = ledges
      .filter(({ top }) => Math.abs(top - route[0].y) < 0.5)
      .find(({ from, to }) => route.every(({ x, z }) => seen(x, z) - 0.45 >= from - 1e-9 && seen(x, z) + 0.45 <= to + 1e-9));
    assert.ok(ledge, `route ${JSON.stringify(route)} leaves every ledge`);
    assert.ok(route.every(({ z }) => z > 0), "it is behind the letters");
    assert.ok(route.every(({ y }) => y === route[0].y), "it changes height");
    assert.ok(
      route.slice(1).every(({ x, z }, i) => Math.abs(seen(x, z) - seen(route[i].x, route[i].z)) <= 4 + 1e-9),
      "it strolls more than 4 blocks at a time",
    );
    if (ledge.to - ledge.from >= 3) assert.ok(Math.abs(seen(route[1].x, route[1].z) - seen(route[0].x, route[0].z)) >= 1 - 1e-9, "it doesn't stroll on a ledge with room to");
  }
});

test("a spawned creeper favours ledges with room to stroll", () => {
  const world = logo();
  const ledges = world.ledges();
  const room = ({ from, to }) => to - from - 0.9;
  const narrow = ledges.filter((ledge) => room(ledge) < 1);
  assert.ok(narrow.length > 0, "every ledge has room");
  let n = 3;
  const random = () => ((n = (n * 1103515245 + 12345) % 2147483648) / 2147483648);
  let onNarrow = 0;
  for (let i = 0; i < 1000; i++) {
    const [{ x, y, z }] = spawnedRoute(world, random);
    const seen = (x * player.z) / (player.z - z);
    if (narrow.some(({ top, from, to }) => Math.abs(top - y) < 0.5 && seen >= from && seen <= to)) onNarrow++;
  }
  assert.ok(onNarrow / 1000 < (narrow.length / ledges.length) / 2, `${onNarrow} of 1000 spawned on ledges with no room`);
});

test("a creeper blown up on top of OASIS breaks OASIS's blocks, not SMP's", () => {
  const world = logo();
  const ledge = world.ledges().find(({ top }) => top === topOf(world, 0));
  const broken = explodeOn(world, spawnedRoute({ ...world, ledges: () => [ledge] }, () => 0.5));
  assert.ok(broken.length > 0, "it broke nothing");
  assert.ok(broken.every(({ word }) => word === 0));
});

test("a spawned creeper stops where its blast has something to break", () => {
  const world = logo();
  // Everything but the P is broken, down to the M's bedrock.
  const p = new Set(world.solidsOf("P").map(({ block }) => block));
  const rest = () => world.solidsOf().map(({ block }) => block).filter((block) => !p.has(block) && block.block !== "bedrock" && world.isStanding(block));
  while (rest().length > 0) world.breakBlocks(rest());
  let n = 5;
  const random = () => ((n = (n * 1103515245 + 12345) % 2147483648) / 2147483648);
  for (let i = 0; i < 200; i++) {
    const { x, y, z } = spawnedRoute(world, random).at(-1);
    const standing = world.solidsOf().filter(({ block }) => world.isStanding(block));
    assert.ok(blocksInReach(world, [x, y, z], standing) > 0, `it stopped at ${x}, ${y} with nothing in reach`);
  }
});

test("a blast's reach is sandstone 3 blocks off and prismarine 1 block off, never bedrock", () => {
  const world = logo();
  const reaches = (find, distance) => {
    const solid = world.solidOf(find);
    return blocksInReach(world, [(solid.min[0] + solid.max[0]) / 2, solid.min[1] + 0.5, distance], [solid]) > 0;
  };
  const sandstone = (block) => block.block === "sandstone";
  const prismarine = (block) => block.block === "prismarine";
  assert.ok(reaches(sandstone, 3) && !reaches(sandstone, 4));
  assert.ok(reaches(prismarine, 1) && !reaches(prismarine, 2));
  const centre = blockAt(world, ({ creeperStart }) => creeperStart);
  world.breakBlocks([centre]);
  assert.ok(!reaches(({ creeperStart }) => creeperStart, 0.1));
});

test("a blast's reach stops at the prismarine in its way", () => {
  // A row of sandstone, a block deep, with a block of prismarine or of air to
  // its left, and the blast half a block left of that.
  const sandstone = { block: { block: "sandstone" }, min: [1, 0, -1], max: [2, 1, 0] };
  const row = (between) => ({
    resistanceAt(x, y, z) {
      const inside = y >= 0 && y < 1 && z >= -1 && z < 0;
      if (inside && x >= 1 && x < 2) return { key: sandstone.block, place: sandstone.block, resistance: BLAST_RESISTANCE.sandstone };
      if (inside && x >= 0 && x < 1 && between) return { key: "between", place: "between", resistance: BLAST_RESISTANCE[between] };
      return { key: "air", place: "air", resistance: null };
    },
  });
  const blast = [-0.5, 0.5, -0.5];
  assert.equal(blocksInReach(row(null), blast, [sandstone]), 1);
  assert.equal(blocksInReach(row("prismarine"), blast, [sandstone]), 0);
});

test("enough creepers break most of the logo", () => {
  const world = logo();
  const total = logoBlocks().blocks.length;
  let n = 7;
  const random = () => ((n = (n * 1103515245 + 12345) % 2147483648) / 2147483648);
  for (let i = 0; i < 300; i++) explodeOn(world, spawnedRoute(world, random), random);
  const left = logoBlocks().blocks.filter((block) => world.isStanding(world.byPlace.get(`${block.word}:${block.column}:${block.row}`))).length;
  assert.ok(left < total / 4, `${left} of ${total} blocks still stand`);
});
