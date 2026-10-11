// The creeper's numbers against the game's: each expected value is worked from
// the Minecraft 26.1 class src/creeper.js names beside it.
import assert from "node:assert/strict";
import { test } from "node:test";
import { brightness, creeperWorld, explosionFrame, legSwing, swellScale, whiteness } from "../src/creeper.js";

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

const viewer = { x: 0, y: -3, z: 12 };

test("the creeper walks at its full speed of about 2.7 blocks a second", () => {
  const world = creeperWorld(viewer);
  for (let i = 0; i < 12; i++) world.tick();
  const was = { ...world.creeper };
  world.tick();
  close(Math.hypot(world.creeper.x - was.x, world.creeper.z - was.z), 0.25 * 0.98 * 0.25 / (1 - 0.6 * 0.91), "stride");
});

test("it stops, burns a 30-tick fuse, and its explosion sets off 48 particles over 8 ticks", () => {
  const world = creeperWorld(viewer);
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

  const seen = new Set();
  const sizes = new Set();
  while (world.tick()) {
    for (const particle of world.particles) {
      seen.add(particle);
      sizes.add(particle.halfSize);
    }
    assert.ok(++ticks < 400, "the explosion never cleared");
  }
  assert.equal(seen.size, 48);
  assert.deepEqual([...sizes].sort(), [1.125, 1.25, 1.375, 1.5, 1.625, 1.75, 1.875, 2]);
});

test("an explosion particle runs through all 16 of its frames", () => {
  assert.equal(explosionFrame({ age: 0, lifetime: 7 }), 0);
  assert.equal(explosionFrame({ age: 7, lifetime: 7 }), 15);
});
