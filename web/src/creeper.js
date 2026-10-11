// Minecraft's creeper, let out of the home page logo's M. The logo becomes the
// blocks it is drawn as, and the creeper — the game's own model and texture —
// walks out from behind its letters, stops at the foot of the P and blows up,
// with the game's own explosion sprites and smoke (public/minecraft holds them,
// unchanged from the Minecraft 26.1 client). Every step is the game's: a tick at
// a time, by its numbers, each named for the class it comes from. Whatever the
// explosion breaks stays broken until the page is loaded again.
//
// The world is measured in blocks — x right, y up, z toward the viewer, the
// ground at y = 0 along the foot of "SMP", and the letters' faces at z = 0. The
// letters are drawn flat, block by block, exactly as the logo draws them; the
// creeper and the explosion are drawn in CSS 3D as a player standing in front
// of the logo sees them, behind the letters or in front of them as they are.
// All of it is drawn in the logo's own units, scaled by --u, so it keeps its
// place on the logo however the page is resized.

const TICK_MS = 50;

export const SKIN = "/minecraft/creeper.png";
export const explosionSprite = (frame) => `/minecraft/explosion_${frame}.png`;
export const smokeSprite = (frame) => `/minecraft/generic_${frame}.png`;
export const PICTURES = [
  SKIN,
  ...Array.from({ length: 16 }, (_, frame) => explosionSprite(frame)),
  ...Array.from({ length: 8 }, (_, frame) => smokeSprite(frame)),
];
export const BLOCKS_FILE = "/logo-blocks.json";

// A block of "SMP" is one of the game's blocks.
const LOGO_UNITS_PER_BLOCK = 4;
const TEXTURE_PIXELS_PER_BLOCK = 16;

// The viewer: a player standing in front of the middle of the logo, with a
// player's eyes (EntityType.PLAYER's eye height). The creeper is after them,
// and the scene is drawn as they see it.
const PLAYER = { x: 0, y: 1.62, z: 24 };

// How much bigger than the game's the creeper is drawn, beside the letters'
// blocks. It still fits between the M and the P.
const CREEPER_SCALE = 1.5;

// Blocks.SANDSTONE and Blocks.PRISMARINE, the logo's two blocks, and under the
// ground the logo stands on, Blocks.GRASS_BLOCK over Blocks.DIRT.
export const BLAST_RESISTANCE = { sandstone: 0.8, prismarine: 6, grass_block: 0.6, dirt: 0.5 };

// CreeperModel.createBodyLayer: each part's pivot, its box's corner and size
// from that pivot, and where the box's faces begin on the texture, in texture
// pixels with y down and the model's front toward -z. The model stands on y = 24.
const LEG = { from: [-2, 0, -2], size: [4, 6, 4], uv: [0, 16] };
const MODEL = {
  head: { pivot: [0, 6, 0], from: [-4, -8, -4], size: [8, 8, 8], uv: [0, 0] },
  body: { pivot: [0, 6, 0], from: [-4, 0, -2], size: [8, 12, 4], uv: [16, 16] },
  rightHindLeg: { pivot: [-2, 18, 4], ...LEG },
  leftHindLeg: { pivot: [2, 18, 4], ...LEG },
  rightFrontLeg: { pivot: [-2, 18, -4], ...LEG },
  leftFrontLeg: { pivot: [2, 18, -4], ...LEG },
};
const MODEL_FLOOR = 24;
const TEXTURE_SIZE = [64, 32];

// The middle of the head, above the feet, in blocks, as drawn.
const HEAD_HEIGHT = ((MODEL_FLOOR - MODEL.head.pivot[1] + MODEL.head.from[1] / 2) / TEXTURE_PIXELS_PER_BLOCK) * CREEPER_SCALE;

// Creeper's maxSwell and explosionRadius.
const FUSE_TICKS = 30;
const EXPLOSION_RADIUS = 3;

// The forward push a walking creeper gets each tick, in blocks per tick:
// Creeper.createAttributes' movement speed of 0.25 is both its input (Mob.setSpeed)
// and, on a block of the usual friction, its scale (getFrictionInfluencedSpeed),
// and LivingEntity.aiStep keeps 0.98 of the input.
const PUSH = 0.25 * 0.98 * 0.25;
// The share of its speed it keeps after each tick: LivingEntity.travelInAir's
// block friction, 0.6 for most blocks, times 0.91.
const GLIDE = 0.6 * 0.91;
// PathNavigation's reach for a waypoint, for a mob as narrow as a creeper.
const WAYPOINT_REACH = 0.75 - 0.6 / 2;

const degrees = (radians) => (radians * 180) / Math.PI;
const radians = (degrees) => (degrees * Math.PI) / 180;
const clamp = (value, min, max) => Math.min(max, Math.max(min, value));
const lerp = (t, a, b) => a + (b - a) * t;
// Mth.wrapDegrees, in radians.
const wrap = (angle) => angle - 2 * Math.PI * Math.round(angle / (2 * Math.PI));
const lerpAngle = (t, a, b) => a + wrap(b - a) * t;
// Mth.rotateIfNecessary: `current`, turned no further than needed to be within
// `max` of `target`.
const rotateIfNecessary = (current, target, max) => target - clamp(wrap(target - current), -max, max);
// MoveControl.rotlerp and LookControl's turns: toward `target`, by at most `max`.
const turnToward = (current, target, max) => current + clamp(wrap(target - current), -max, max);
const normalize = (v) => v.map((c) => c / Math.hypot(...v));

// A yaw faces (sin yaw, cos yaw) across x and z: 0 faces the viewer.
const yawToward = (dx, dz) => Math.atan2(dx, dz);

// CreeperModel.setupAnim, by the name of each leg's part (the model's fields
// for its legs are crossed over, left for right). Radians about x.
export function legSwing(position, speed) {
  const swing = (phase) => Math.cos(position * 0.6662 + phase) * 1.4 * speed;
  return {
    rightHindLeg: swing(Math.PI),
    leftHindLeg: swing(0),
    rightFrontLeg: swing(0),
    leftFrontLeg: swing(Math.PI),
  };
}

// CreeperRenderer.scale: how much wider and how much taller the creeper is
// drawn, about its feet, `swelling` of the way through its fuse.
export function swellScale(swelling) {
  const wobble = 1 + Math.sin(swelling * 100) * swelling * 0.01;
  const g = clamp(swelling, 0, 1) ** 4;
  return { wide: (1 + g * 0.4) * wobble, tall: (1 + g * 0.1) / wobble };
}

// How white the creeper is drawn, as the share of white over its lit texture:
// CreeperRenderer.getWhiteOverlayProgress picks a column of OverlayTexture,
// whose alpha entity.fsh mixes the texture by.
export function whiteness(swelling) {
  const progress = Math.trunc(swelling * 10) % 2 === 0 ? 0 : clamp(swelling, 0.5, 1);
  const column = Math.trunc(progress * 15);
  return 1 - Math.trunc((1 - (column / 15) * 0.75) * 255) / 255;
}

// How brightly a face is lit whose normal is `n`, y up: Lighting's
// DIFFUSE_LIGHT_0 and DIFFUSE_LIGHT_1, mixed as light.glsl mixes them.
const LIGHTS = [normalize([0.2, 1, -0.7]), normalize([-0.2, 1, 0.7])];
export function brightness(n) {
  const light = LIGHTS.reduce((sum, l) => sum + Math.max(0, l[0] * n[0] + l[1] * n[1] + l[2] * n[2]), 0);
  return Math.min(1, light * 0.6 + 0.4);
}

// SpriteSet.get, through ParticleResources: which of `count` frames a particle
// of `lifetime` ticks shows at `age`.
export const spriteIndex = (age, lifetime, count) => Math.trunc((age * (count - 1)) / lifetime);

// ServerExplosion.calculateExplodedPositions: every place the blast reaches with
// power to spare, keyed by what is there. Rays go out toward each point on the
// surface of a 16-point cube, losing power with each 0.3-block step and more for
// what each step is inside. `resistanceAt` gives what a point is inside and its
// blast resistance.
export function blastedPlaces(center, radius, resistanceAt, random = Math.random) {
  const places = new Map();
  for (let xx = 0; xx < 16; xx++) {
    for (let yy = 0; yy < 16; yy++) {
      for (let zz = 0; zz < 16; zz++) {
        if (xx !== 0 && xx !== 15 && yy !== 0 && yy !== 15 && zz !== 0 && zz !== 15) continue;
        const [dx, dy, dz] = normalize([(xx / 15) * 2 - 1, (yy / 15) * 2 - 1, (zz / 15) * 2 - 1]);
        let power = radius * (0.7 + random() * 0.6);
        let [x, y, z] = center;
        while (power > 0) {
          const { key, place, resistance } = resistanceAt(x, y, z);
          power -= (resistance + 0.3) * 0.3;
          if (power > 0) places.set(key, place);
          x += dx * 0.3;
          y += dy * 0.3;
          z += dz * 0.3;
          power -= 0.22500001;
        }
      }
    }
  }
  return places;
}

// Particle.tick: ages a particle and moves it, with the ground at y = 0
// stopping it. False once it is gone.
function tickParticle(p) {
  p.before = { x: p.x, y: p.y, z: p.z };
  if (p.age++ >= p.lifetime) return false;
  if (!p.moves) return true;
  p.yd -= 0.04 * p.gravity;
  let ya = p.yd;
  p.onGround = false;
  if (p.y + ya < 0) {
    ya = -p.y;
    p.yd = 0;
    p.onGround = true;
  }
  p.x += p.xd;
  p.y += ya;
  p.z += p.zd;
  if (p.speedUpWhenYMotionIsBlocked && p.y === p.before.y) {
    p.xd *= 1.1;
    p.zd *= 1.1;
  }
  p.xd *= p.friction;
  p.yd *= p.friction;
  p.zd *= p.friction;
  if (p.onGround) {
    p.xd *= 0.7;
    p.zd *= 0.7;
  }
  return true;
}

// The particles of an explosion, as their classes make them.
const particle = (kind, [x, y, z], fields) => ({ kind, x, y, z, age: 0, before: { x, y, z }, ...fields });

// HugeExplosionParticle, from HugeExplosionSeedParticle: `size` grows over the
// seed's 8 ticks.
export function explosionParticle(at, size, random = Math.random) {
  return particle("explosion", at, {
    moves: false,
    lifetime: 6 + Math.floor(random() * 4),
    shade: random() * 0.6 + 0.4,
    halfSize: 2 * (1 - size * 0.5),
    frames: 16,
  });
}

// ExplodeParticle, the explosion's ParticleTypes.POOF.
export function poofParticle(at, [xa, ya, za], random = Math.random) {
  return particle("poof", at, {
    moves: true,
    xd: xa + (random() * 2 - 1) * 0.05,
    yd: ya + (random() * 2 - 1) * 0.05,
    zd: za + (random() * 2 - 1) * 0.05,
    gravity: -0.1,
    friction: 0.9,
    shade: random() * 0.3 + 0.7,
    halfSize: 0.1 * (random() * random() * 6 + 1),
    lifetime: Math.trunc(16 / (random() * 0.8 + 0.2)) + 2,
    frames: 8,
  });
}

// SmokeParticle through BaseAshSmokeParticle, the explosion's ParticleTypes.SMOKE,
// starting with Particle's own random drift, a tenth of it kept.
export function smokeParticle(at, [xa, ya, za], random = Math.random) {
  let drift = [0, 0, 0].map(() => (random() * 2 - 1) * 0.4);
  const speed = (random() + random() + 1) * 0.15;
  drift = normalize(drift).map((c) => c * speed * 0.4);
  drift[1] += 0.1;
  const halfSize = 0.1 * (random() * 0.5 + 0.5) * 2 * 0.75;
  return particle("smoke", at, {
    moves: true,
    xd: drift[0] * 0.1 + xa,
    yd: drift[1] * 0.1 + ya,
    zd: drift[2] * 0.1 + za,
    gravity: -0.1,
    friction: 0.96,
    speedUpWhenYMotionIsBlocked: true,
    shade: random() * 0.3,
    halfSize,
    lifetime: Math.max(1, Math.trunc(8 / (random() * 0.8 + 0.2))),
    frames: 8,
  });
}

// Level's DEFAULT_EXPLOSION_BLOCK_PARTICLES, picked between evenly: how far out
// each starts, as a share of where it was aimed, and its speed.
const BLOCK_PARTICLES = [
  { make: poofParticle, scaling: 0.5, speed: 1 },
  { make: smokeParticle, scaling: 1, speed: 1 },
];

// The creeper and its explosion in the logo's world, a tick at a time. `level`
// has the eyes of the player it is after, its route (its first point where it starts,
// its last where it stops), what is at a point, and what to do with the logo's
// blocks the blast breaks. Without `walk` it starts at the end of its route;
// without `effects` its explosion breaks blocks but throws out no particles.
// Each tick returns false once there is nothing left to draw.
export function creeperWorld(level, { walk = true, effects = true, random = Math.random } = {}) {
  const { player, route, resistanceAt, breakBlocks } = level;
  const start = walk ? route[0] : route.at(-1);
  const creeper = {
    alive: true,
    x: start.x,
    z: start.z,
    vx: 0,
    vz: 0,
    waypoint: walk ? 1 : route.length,
    yRot: 0,
    yBodyRot: 0,
    yHeadRot: 0,
    xHeadRot: 0,
    lastStableYHeadRot: 0,
    headStableTime: 0,
    // WalkAnimationState.
    walkSpeed: 0,
    walkPosition: 0,
    swell: 0,
    fusing: !walk,
  };
  const particles = [];
  let emitter = null;
  let blockParticles = null;

  function look() {
    const dx = player.x - creeper.x;
    const dz = player.z - creeper.z;
    // MeleeAttackGoal and SwellGoal: the creeper's eyes are on its target, turning
    // at most 30 degrees a tick each way.
    creeper.yHeadRot = turnToward(creeper.yHeadRot, yawToward(dx, dz), radians(30));
    creeper.xHeadRot = turnToward(creeper.xHeadRot, Math.atan2(player.y - HEAD_HEIGHT, Math.hypot(dx, dz)), radians(30));
  }
  const next = route[creeper.waypoint];
  creeper.yRot = next ? yawToward(next.x - creeper.x, next.z - creeper.z) : 0;
  creeper.yBodyRot = creeper.yRot;
  for (let i = 0; i < 4; i++) look();
  creeper.lastStableYHeadRot = creeper.yHeadRot;
  if (!walk) creeper.yBodyRot = creeper.yHeadRot;
  let before = { ...creeper };

  function tickCreeper() {
    // PathNavigation, on to the next waypoint once within reach of this one.
    let target = route[creeper.waypoint];
    while (target && Math.hypot(target.x - creeper.x, target.z - creeper.z) < WAYPOINT_REACH) target = route[++creeper.waypoint];
    if (!target && !creeper.fusing) {
      // At the end of its route SwellGoal stops it and lights the fuse.
      creeper.fusing = true;
    }
    if (target) {
      // MoveControl turns it toward the waypoint, 90 degrees a tick at most,
      // and it walks the way it faces.
      creeper.yRot = turnToward(creeper.yRot, yawToward(target.x - creeper.x, target.z - creeper.z), radians(90));
      creeper.vx += Math.sin(creeper.yRot) * PUSH;
      creeper.vz += Math.cos(creeper.yRot) * PUSH;
    }
    creeper.x += creeper.vx;
    creeper.z += creeper.vz;
    const moved = Math.hypot(creeper.vx, creeper.vz);
    creeper.vx *= GLIDE;
    creeper.vz *= GLIDE;

    // LivingEntity.updateWalkAnimation and WalkAnimationState.update.
    creeper.walkSpeed += (Math.min(moved * 4, 1) - creeper.walkSpeed) * 0.4;
    creeper.walkPosition += creeper.walkSpeed;

    look();
    // BodyRotationControl.clientTick, with Mob.getMaxHeadYRot's 75 degrees.
    const maxHead = radians(75);
    if (moved * moved > 2.5000003e-7) {
      creeper.yBodyRot = creeper.yRot;
      creeper.yHeadRot = rotateIfNecessary(creeper.yHeadRot, creeper.yBodyRot, maxHead);
      creeper.lastStableYHeadRot = creeper.yHeadRot;
      creeper.headStableTime = 0;
    } else if (Math.abs(degrees(creeper.yHeadRot - creeper.lastStableYHeadRot)) > 15) {
      creeper.headStableTime = 0;
      creeper.lastStableYHeadRot = creeper.yHeadRot;
      creeper.yBodyRot = rotateIfNecessary(creeper.yBodyRot, creeper.yHeadRot, maxHead);
    } else if (++creeper.headStableTime > 10) {
      const forward = clamp((creeper.headStableTime - 10) / 10, 0, 1);
      creeper.yBodyRot = rotateIfNecessary(creeper.yBodyRot, creeper.yHeadRot, maxHead * (1 - forward));
    }

    // Creeper.tick: the fuse burns a tick at a time, and at its end the creeper
    // is gone and ServerLevel.explode sets off a large explosion at its feet:
    // the blocks it reaches break, a HugeExplosionSeedParticle starts, and
    // ClientExplosionTracker has a particle thrown out for each place it reached.
    if (creeper.fusing && ++creeper.swell >= FUSE_TICKS) {
      creeper.alive = false;
      const center = [creeper.x, 0, creeper.z];
      const places = blastedPlaces(center, EXPLOSION_RADIUS, resistanceAt, random);
      breakBlocks([...places.values()]);
      if (effects) {
        emitter = { center, age: 0 };
        blockParticles = { center, count: Math.min(places.size, 512) };
      }
      return true;
    }
    return false;
  }

  // HugeExplosionSeedParticle.tick: for 8 ticks, 6 explosions a tick, scattered
  // up to 4 blocks each way, each smaller than the last tick's.
  function tickEmitter() {
    const spread = () => (random() - random()) * 4;
    const [x, y, z] = emitter.center;
    for (let i = 0; i < 6; i++) particles.push(explosionParticle([x + spread(), y + spread(), z + spread()], emitter.age / 8, random));
    if (++emitter.age === 8) emitter = null;
  }

  // ClientExplosionTracker.addParticle, for each of the places the blast reached.
  function throwBlockParticles() {
    const { center, count } = blockParticles;
    for (let i = 0; i < count; i++) {
      const direction = normalize([random() * 2 - 1, random() * 2 - 1, random() * 2 - 1]);
      const reach = Math.cbrt(random()) * EXPLOSION_RADIUS;
      const aimed = direction.map((c) => c * reach);
      if (resistanceAt(...center.map((c, axis) => c + aimed[axis])).place !== "air") continue;
      const speed = (0.5 / (reach / EXPLOSION_RADIUS + 0.1)) * random() * random() + 0.3;
      const kind = BLOCK_PARTICLES[Math.floor(random() * BLOCK_PARTICLES.length)];
      const at = center.map((c, axis) => c + aimed[axis] * kind.scaling);
      particles.push(kind.make(at, direction.map((c) => c * speed * kind.speed), random));
    }
    blockParticles = null;
  }

  return {
    creeper,
    particles,
    get before() {
      return before;
    },
    tick() {
      before = { ...creeper };
      // ParticleEngine ticks the particles there are, then adds this tick's new ones.
      for (let i = particles.length - 1; i >= 0; i--) if (!tickParticle(particles[i])) particles.splice(i, 1);
      let exploded = false;
      if (creeper.alive) exploded = tickCreeper();
      if (!exploded) {
        if (emitter) tickEmitter();
        if (blockParticles) throwBlockParticles();
      }
      return creeper.alive || emitter !== null || blockParticles !== null || particles.length > 0;
    },
  };
}

// --- Drawing ---------------------------------------------------------------

// A length in the logo's units, as CSS.
const u = (n) => `calc(var(--u) * ${n})`;
const translate = (x, y, z) => `translate3d(${u(x)}, ${u(y)}, ${u(z)})`;

// CSS's rotateX and rotateY, on a vector.
const rotateX = (angle, [x, y, z]) => [x, y * Math.cos(angle) - z * Math.sin(angle), y * Math.sin(angle) + z * Math.cos(angle)];
const rotateY = (angle, [x, y, z]) => [x * Math.cos(angle) + z * Math.sin(angle), y, -x * Math.sin(angle) + z * Math.cos(angle)];

// The faces of a box, each with its normal (CSS's, y down), its size, where it
// begins on a texture, and the CSS that puts it in place, given the box's
// corner and size with its front toward the viewer, all in `unit`s.
function boxFaces([x0, y0, z0], [w, h, d], [u0, v0], unit) {
  const at = (x, y, z, turn = "") => `${translate(x * unit, y * unit, z * unit)} ${turn}`;
  return {
    front: { normal: [0, 0, 1], size: [w, h], uv: [u0 + d, v0 + d], transform: at(x0, y0, z0 + d) },
    back: { normal: [0, 0, -1], size: [w, h], uv: [u0 + 2 * d + w, v0 + d], transform: at(x0 + w, y0, z0, "rotateY(180deg)") },
    left: { normal: [-1, 0, 0], size: [d, h], uv: [u0, v0 + d], transform: at(x0, y0, z0, "rotateY(-90deg)") },
    right: { normal: [1, 0, 0], size: [d, h], uv: [u0 + d + w, v0 + d], transform: at(x0 + w, y0, z0 + d, "rotateY(90deg)") },
    top: { normal: [0, -1, 0], size: [w, d], uv: [u0 + d, v0], transform: at(x0, y0, z0, "rotateX(90deg)") },
    bottom: { normal: [0, 1, 0], size: [w, d], uv: [u0 + d + w, v0], transform: at(x0, y0 + h, z0 + d, "rotateX(-90deg)") },
  };
}

function element(className, parent) {
  const made = document.createElement("i");
  made.className = className;
  parent.append(made);
  return made;
}

function sized(drawn, [w, h], unit) {
  drawn.style.width = u(w * unit);
  drawn.style.height = u(h * unit);
}

// Loads a picture from the site; resolves false if it can't be had.
function load(src) {
  const picture = new Image();
  const loaded = new Promise((resolve) => {
    picture.onload = () => resolve(true);
    picture.onerror = () => resolve(false);
  });
  picture.src = src;
  return loaded;
}

// How far, in the logo's units, each block's side and face reach past their
// own edges into their neighbours', so that no seam shows between them.
const SEAM = 0.25;

// The logo, redrawn as its blocks in place of its picture, once per page:
// whatever is broken stays broken.
const built = new WeakMap();

function buildLogo(logo, data) {
  const { width, height, ground, colours, blocks } = data;
  const art = logo.querySelector(".logo-art");
  const resize = () => logo.style.setProperty("--u", `${art.clientWidth / width}px`);
  resize();
  new ResizeObserver(resize).observe(art);

  // Over the logo's picture: the scene behind the letters, the letters, and the
  // scene in front of them.
  const layer = (className) => {
    const made = document.createElement("div");
    made.className = className;
    made.setAttribute("aria-hidden", "true");
    return made;
  };
  const behind = layer("creeper-scene");
  const letters = layer("logo-blocks");
  const front = layer("creeper-scene");
  for (const scene of [behind, front]) {
    scene.style.perspective = u(PLAYER.z * LOGO_UNITS_PER_BLOCK);
    scene.style.perspectiveOrigin = `50% ${((ground - PLAYER.y * LOGO_UNITS_PER_BLOCK) / height) * 100}%`;
  }
  art.after(behind, letters, front);

  // The logo's three passes, with each block's piece of each: its outline, then
  // its side, then its face, the logo's own art for it.
  const passes = ["outline", "side", "face"].map(() => element("logo-pass", letters));
  const place = (piece, [x, y, w, h], seam = 0) => {
    piece.style.left = u(x);
    piece.style.top = u(y);
    piece.style.width = u(w + seam);
    piece.style.height = u(h + seam);
  };
  for (const block of blocks) {
    const outline = element("logo-piece", passes[0]);
    place(outline, block.outline);
    outline.style.backgroundColor = colours.outline;
    const side = element("logo-piece", passes[1]);
    place(side, block.side, SEAM);
    side.style.backgroundColor = colours[block.block];
    const face = element("logo-piece logo-face", passes[2]);
    place(face, block.face, SEAM);
    face.style.backgroundSize = `${u(width)} ${u(height)}`;
    face.style.backgroundPosition = `${u(-block.face[0])} ${u(-block.face[1])}`;
    block.pieces = [outline, side, face];
  }
  logo.classList.add("logo-built");

  // The blocks in the world, each as deep as it is wide.
  const solids = blocks.map((block) => {
    const [x, y, size] = block.face;
    return {
      block,
      min: [(x - width / 2) / LOGO_UNITS_PER_BLOCK, (ground - y - size) / LOGO_UNITS_PER_BLOCK, -size / LOGO_UNITS_PER_BLOCK],
      max: [(x + size - width / 2) / LOGO_UNITS_PER_BLOCK, (ground - y) / LOGO_UNITS_PER_BLOCK, 0],
    };
  });
  const standing = new Set(blocks);

  return {
    // Where a point in the world is drawn, in the logo's units.
    toLogo: (x, y, z) => [width / 2 + x * LOGO_UNITS_PER_BLOCK, ground - y * LOGO_UNITS_PER_BLOCK, z * LOGO_UNITS_PER_BLOCK],
    // The scene a point is drawn in: behind the letters or in front of them.
    sceneFor: (z) => (z < 0 ? behind : front),
    // What is at a point: one of the logo's blocks, the ground, or air.
    resistanceAt(x, y, z) {
      const solid = solids.find(
        ({ block, min, max }) => standing.has(block) && x >= min[0] && x < max[0] && y >= min[1] && y < max[1] && z >= min[2] && z < max[2],
      );
      if (solid) return { key: solid.block, place: solid.block, resistance: BLAST_RESISTANCE[solid.block.block] };
      const cell = [x, y, z].map(Math.floor);
      const key = cell.join();
      if (cell[1] < -1) return { key, place: "dirt", resistance: BLAST_RESISTANCE.dirt };
      if (cell[1] < 0) return { key, place: "grass_block", resistance: BLAST_RESISTANCE.grass_block };
      return { key, place: "air", resistance: 0 };
    },
    breakBlocks(places) {
      for (const block of places.filter((place) => standing.has(place))) {
        standing.delete(block);
        block.pieces.forEach((piece) => piece.remove());
      }
    },
    solidOf: (find) => solids.find(({ block }) => find(block)),
    solidsOf: (letter) => solids.filter(({ block }) => block.word === 1 && block.letter === letter),
  };
}

// The creeper's route: from behind the M's centre block, through the gap
// between the M and the P, to stand at the P's foot.
function routeFor(level) {
  const centre = level.solidOf(({ word, letter, letterColumn, row }) => word === 1 && letter === "M" && letterColumn === 2 && row === 2);
  const m = level.solidsOf("M");
  const p = level.solidsOf("P");
  const mRight = Math.max(...m.map(({ max }) => max[0]));
  const pLeft = Math.min(...p.map(({ min }) => min[0]));
  const gap = (mRight + pLeft) / 2;
  const behind = Math.min(...p.map(({ min }) => min[2])) - 1;
  return [
    { x: (centre.min[0] + centre.max[0]) / 2, z: behind },
    { x: gap, z: behind },
    { x: gap, z: 1 },
    { x: pLeft + 0.5, z: 0.6 },
  ];
}

// Lets a creeper out of `target`, the M's centre block in the logo's targets,
// and resolves once its explosion has cleared. With `walk` false it is already
// standing at the P, and makes no flash, swell or smoke on its way out.
export async function releaseCreeper(target, { walk }) {
  const logo = target.closest(".logo");
  const [data, loaded] = await Promise.all([
    built.has(logo) ? null : fetch(BLOCKS_FILE).then((response) => (response.ok ? response.json() : null), () => null),
    Promise.all(PICTURES.map(load)),
  ]);
  if (!loaded[0] || (!built.has(logo) && !data)) return;
  if (!built.has(logo)) built.set(logo, buildLogo(logo, data));
  const level = built.get(logo);
  const { toLogo, sceneFor } = level;
  const texel = (LOGO_UNITS_PER_BLOCK / TEXTURE_PIXELS_PER_BLOCK) * CREEPER_SCALE;

  const root = document.createElement("i");
  root.className = "creeper";
  const parts = [];
  const faces = [];
  for (const [name, { pivot, from, size, uv }] of Object.entries(MODEL)) {
    // Turned to face the viewer: the model's z, negated.
    const place = translate(pivot[0] * texel, (pivot[1] - MODEL_FLOOR) * texel, -pivot[2] * texel);
    const part = element("creeper-part", root);
    parts.push({ name, part, place });
    const corner = [from[0], from[1], -(from[2] + size[2])];
    for (const face of Object.values(boxFaces(corner, size, uv, texel))) {
      const drawn = element("creeper-face", part);
      sized(drawn, face.size, texel);
      drawn.style.transform = face.transform;
      drawn.style.backgroundSize = `${u(TEXTURE_SIZE[0] * texel)} ${u(TEXTURE_SIZE[1] * texel)}`;
      drawn.style.backgroundPosition = `${u(-face.uv[0] * texel)} ${u(-face.uv[1] * texel)}`;
      faces.push({ drawn, part: name, normal: face.normal });
    }
  }

  const world = creeperWorld({ ...level, player: PLAYER, route: routeFor(level) }, { walk, effects: walk });
  const drawnParticles = new Map();

  function drawCreeper(t) {
    const now = world.creeper;
    const was = world.before;
    if (!now.alive) {
      root.remove();
      return;
    }
    const bodyYaw = lerpAngle(t, was.yBodyRot, now.yBodyRot);
    const headYaw = wrap(lerpAngle(t, was.yHeadRot, now.yHeadRot) - bodyYaw);
    // CSS turns a face up for a positive turn about x.
    const headPitch = lerp(t, was.xHeadRot, now.xHeadRot);
    // WalkAnimationState.speed(partialTicks) and position(partialTicks).
    const walkSpeed = Math.min(lerp(t, was.walkSpeed, now.walkSpeed), 1);
    const walkPosition = now.walkPosition - now.walkSpeed * (1 - t);
    // Creeper.getSwelling.
    const swelling = walk ? lerp(t, was.swell, now.swell) / (FUSE_TICKS - 2) : 0;
    const { wide, tall } = swellScale(swelling);

    const z = lerp(t, was.z, now.z);
    if (root.parentElement !== sceneFor(z)) sceneFor(z).append(root);
    root.style.transform = `${translate(...toLogo(lerp(t, was.x, now.x), 0, z))} rotateY(${bodyYaw}rad) scale3d(${wide}, ${tall}, ${wide})`;
    root.style.setProperty("--white", whiteness(swelling));
    // Each part's turn, [about y, about x].
    const turns = { head: [headYaw, headPitch], body: [0, 0] };
    for (const [leg, swing] of Object.entries(legSwing(walkPosition, walkSpeed))) turns[leg] = [0, swing];
    for (const { name, part, place } of parts) {
      const [yaw, pitch] = turns[name];
      part.style.transform = `${place} rotateY(${yaw}rad) rotateX(${pitch}rad)`;
    }
    for (const { drawn, part, normal } of faces) {
      const [yaw, pitch] = turns[part];
      const [nx, ny, nz] = rotateY(bodyYaw, rotateY(yaw, rotateX(pitch, normal)));
      drawn.style.setProperty("--shade", 1 - brightness([nx, -ny, nz]));
    }
  }

  function drawParticles(t) {
    const live = new Set(world.particles);
    for (const [p, drawn] of drawnParticles) {
      if (!live.has(p)) {
        drawn.element.remove();
        drawnParticles.delete(p);
      }
    }
    for (const p of world.particles) {
      let drawn = drawnParticles.get(p);
      if (!drawn) {
        const made = element("explosion-particle", sceneFor(p.z));
        const side = 2 * p.halfSize * LOGO_UNITS_PER_BLOCK;
        made.style.width = made.style.height = u(side);
        made.style.margin = `${u(-side / 2)} 0 0 ${u(-side / 2)}`;
        made.style.filter = `brightness(${p.shade})`;
        drawn = { element: made, frame: -1 };
        drawnParticles.set(p, drawn);
      }
      const x = lerp(t, p.before.x, p.x);
      const y = lerp(t, p.before.y, p.y);
      const z = lerp(t, p.before.z, p.z);
      if (drawn.element.parentElement !== sceneFor(z)) sceneFor(z).append(drawn.element);
      // SingleQuadParticle's LOOKAT_XYZ: turned to face the viewer's eyes.
      const dx = PLAYER.x - x;
      const dz = PLAYER.z - z;
      const pitch = Math.atan2(PLAYER.y - y, Math.hypot(dx, dz));
      drawn.element.style.transform = `${translate(...toLogo(x, y, z))} rotateY(${yawToward(dx, dz)}rad) rotateX(${pitch}rad)`;
      const frame = spriteIndex(p.age, p.lifetime, p.frames);
      if (frame !== drawn.frame) {
        drawn.frame = frame;
        // The poof and smoke sprites run from the largest, generic_7, down.
        drawn.element.style.backgroundImage = `url(${p.kind === "explosion" ? explosionSprite(frame) : smokeSprite(7 - frame)})`;
      }
    }
  }

  const draw = (t) => {
    drawCreeper(t);
    drawParticles(t);
  };

  await new Promise((done) => {
    let last;
    let behind = 0;
    draw(1);
    function frame(time) {
      behind += time - (last ?? time);
      last = time;
      let going = true;
      while (behind >= TICK_MS && going) {
        behind -= TICK_MS;
        going = world.tick();
      }
      if (!going) return done();
      draw(behind / TICK_MS);
      requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
  });
  root.remove();
  for (const { element: drawn } of drawnParticles.values()) drawn.remove();
}
