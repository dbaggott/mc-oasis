// The creeper's numbers against the game's: each expected value is worked from
// the Minecraft 26.1 class src/creeper.js names beside it.
import assert from "node:assert/strict";
import { test } from "node:test";
import {
  BLAST_RESISTANCE,
  blastedPlaces,
  brightness,
  creeperWorld,
  explosionParticle,
  legSwing,
  poofParticle,
  smokeParticle,
  spriteIndex,
  swellScale,
  whiteness,
} from "../src/creeper.js";

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
    { x: 0, z: -3 },
    { x: 0, z: 3 },
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

test("without effects, the blast still breaks blocks but throws out nothing", () => {
  const broken = [];
  const world = creeperWorld(level(broken), { walk: false, effects: false });
  while (world.tick()) assert.equal(world.particles.length, 0);
  assert.ok(broken.length > 0);
});
