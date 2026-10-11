// Minecraft's creeper, let out of the home page logo's M. It is the game's own
// model and texture, blown up with the game's own explosion sprites (public/
// minecraft holds them, unchanged from the Minecraft 26.1 client), and it moves
// the way the game moves it: a tick at a time, by the game's numbers, each named
// for the class it comes from.
//
// It is drawn in CSS 3D, in the logo's own 3D space, where the letters are the
// plane z = 0: it walks out from behind them, and the browser hides whatever of
// it is still behind a letter. The space is CSS's — x right, y down, z toward
// the viewer, who looks at the middle of the logo — measured in blocks until
// drawn.

const TICK_MS = 50;
const TEXTURE_PIXELS_PER_BLOCK = 16;

// Logo units per texture pixel of the creeper.
const LOGO_UNITS_PER_PIXEL = 1.5;

// How far in front of the logo the viewer stands, in blocks.
const VIEWER_DISTANCE = 12;

// Where the creeper sets out, behind the logo, and where it stops to blow up,
// in front of it: blocks across from the M's centre block, and toward the viewer.
const START = { x: -1.5, z: -3 };
const STOP = { x: 0, z: 1 };

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

// The middle of the head, from the feet, in blocks.
const HEAD_HEIGHT = (MODEL_FLOOR - MODEL.head.pivot[1] - MODEL.head.size[1] / 2) / TEXTURE_PIXELS_PER_BLOCK;

// Creeper's maxSwell: ticks from the hiss to the bang.
const FUSE_TICKS = 30;

// The forward push a walking creeper gets each tick, in blocks per tick:
// Creeper.createAttributes' movement speed of 0.25 is both its input (Mob.setSpeed)
// and, on a block of the usual friction, its scale (getFrictionInfluencedSpeed),
// and LivingEntity.aiStep keeps 0.98 of the input.
const PUSH = 0.25 * 0.98 * 0.25;
// The share of its speed it keeps after each tick: LivingEntity.travelInAir's
// block friction, 0.6 for most blocks, times 0.91.
const GLIDE = 0.6 * 0.91;
// Close enough to STOP to stop pushing; it glides the rest of the way.
const ARRIVED = 0.3;

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

// How brightly a face is lit whose normal is `n`, in the game's world with y up:
// Lighting's DIFFUSE_LIGHT_0 and DIFFUSE_LIGHT_1, mixed as light.glsl mixes them.
const LIGHTS = [
  [0.2, 1, -0.7],
  [-0.2, 1, 0.7],
].map((l) => l.map((c) => c / Math.hypot(...l)));
export function brightness(n) {
  const light = LIGHTS.reduce((sum, l) => sum + Math.max(0, l[0] * n[0] + l[1] * n[1] + l[2] * n[2]), 0);
  return Math.min(1, light * 0.6 + 0.4);
}

// CSS's rotateX and rotateY, on a vector.
const rotateX = (angle, [x, y, z]) => [x, y * Math.cos(angle) - z * Math.sin(angle), y * Math.sin(angle) + z * Math.cos(angle)];
const rotateY = (angle, [x, y, z]) => [x * Math.cos(angle) + z * Math.sin(angle), y, -x * Math.sin(angle) + z * Math.cos(angle)];

// A yaw faces (sin yaw, cos yaw) across x and z: 0 faces the viewer.
const yawToward = (dx, dz) => Math.atan2(dx, dz);

// The creeper and its explosion, a tick at a time, in blocks. `viewer` is where
// the viewer's eye is. Each tick returns false once there is nothing left to draw.
export function creeperWorld(viewer, { walk = true } = {}) {
  const at = walk ? START : STOP;
  const creeper = {
    alive: true,
    x: at.x,
    z: at.z,
    vx: 0,
    vz: 0,
    walking: walk,
    yBodyRot: walk ? yawToward(STOP.x - START.x, STOP.z - START.z) : 0,
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
  let before = { ...creeper };

  function look() {
    const dx = viewer.x - creeper.x;
    const dz = viewer.z - creeper.z;
    const up = -HEAD_HEIGHT - viewer.y;
    // MeleeAttackGoal and SwellGoal: the creeper's eyes are on its target, turning
    // at most 30 degrees a tick each way.
    creeper.yHeadRot = turnToward(creeper.yHeadRot, yawToward(dx, dz), radians(30));
    creeper.xHeadRot = turnToward(creeper.xHeadRot, Math.atan2(up, Math.hypot(dx, dz)), radians(30));
  }
  look();
  creeper.lastStableYHeadRot = creeper.yHeadRot;
  if (!walk) creeper.yBodyRot = creeper.yHeadRot;

  function tickCreeper() {
    const toStop = Math.hypot(STOP.x - creeper.x, STOP.z - creeper.z);
    if (creeper.walking && toStop < ARRIVED) {
      // SwellGoal stops the creeper's path and lights the fuse.
      creeper.walking = false;
      creeper.fusing = true;
    }
    if (creeper.walking) {
      const heading = yawToward(STOP.x - creeper.x, STOP.z - creeper.z);
      creeper.vx += Math.sin(heading) * PUSH;
      creeper.vz += Math.cos(heading) * PUSH;
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
      creeper.yBodyRot = yawToward(creeper.vx, creeper.vz);
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
    // is gone and ServerLevel.explode sets off a large explosion at its feet.
    if (creeper.fusing && ++creeper.swell >= FUSE_TICKS) {
      creeper.alive = false;
      emitter = { x: creeper.x, y: 0, z: creeper.z, age: 0, fresh: true };
    }
  }

  // HugeExplosionSeedParticle.tick: for 8 ticks, 6 explosions a tick, scattered
  // up to 4 blocks each way, each smaller than the last tick's.
  function tickEmitter() {
    const spread = () => (Math.random() - Math.random()) * 4;
    for (let i = 0; i < 6; i++) {
      particles.push({
        x: emitter.x + spread(),
        y: emitter.y + spread(),
        z: emitter.z + spread(),
        // HugeExplosionParticle.
        age: 0,
        lifetime: 6 + Math.floor(Math.random() * 4),
        shade: Math.random() * 0.6 + 0.4,
        halfSize: 2 * (1 - (emitter.age / 8) * 0.5),
        fresh: true,
      });
    }
    if (++emitter.age === 8) emitter = null;
  }

  return {
    creeper,
    particles,
    get before() {
      return before;
    },
    // ParticleEngine ticks a particle first on the tick after it was added.
    tick() {
      before = { ...creeper };
      if (creeper.alive) tickCreeper();
      for (let i = particles.length - 1; i >= 0; i--) {
        const particle = particles[i];
        if (particle.fresh) particle.fresh = false;
        else if (particle.age++ >= particle.lifetime) particles.splice(i, 1);
      }
      if (emitter?.fresh) emitter.fresh = false;
      else if (emitter) tickEmitter();
      return creeper.alive || emitter !== null || particles.length > 0;
    },
  };
}

// SpriteSet.get through ParticleResources: which of the 16 explosion frames a
// particle shows.
export const explosionFrame = ({ age, lifetime }) => Math.trunc((age * 15) / lifetime);

// The faces of a part's box, each with its normal, its size, where it begins on
// the texture, and the CSS that puts it in place, given the box's corner and
// size with the model's front turned toward the viewer, in texture pixels, and
// the drawn size of one (`px`).
function boxFaces([x0, y0, z0], [w, h, d], [u, v], px) {
  const at = (x, y, z, turn = "") => `translate3d(${x * px}px, ${y * px}px, ${z * px}px) ${turn}`;
  return [
    { normal: [0, 0, 1], size: [w, h], uv: [u + d, v + d], transform: at(x0, y0, z0 + d) },
    { normal: [0, 0, -1], size: [w, h], uv: [u + 2 * d + w, v + d], transform: at(x0 + w, y0, z0, "rotateY(180deg)") },
    { normal: [-1, 0, 0], size: [d, h], uv: [u, v + d], transform: at(x0, y0, z0, "rotateY(-90deg)") },
    { normal: [1, 0, 0], size: [d, h], uv: [u + d + w, v + d], transform: at(x0 + w, y0, z0 + d, "rotateY(90deg)") },
    { normal: [0, -1, 0], size: [w, d], uv: [u + d, v], transform: at(x0, y0, z0, "rotateX(90deg)") },
    { normal: [0, 1, 0], size: [w, d], uv: [u + d + w, v], transform: at(x0, y0 + h, z0 + d, "rotateX(-90deg)") },
  ];
}

function element(className, parent) {
  const made = document.createElement("i");
  made.className = className;
  parent.append(made);
  return made;
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

const SKIN = "/minecraft/creeper.png";
const explosionSprite = (frame) => `/minecraft/explosion_${frame}.png`;

// Lets a creeper out of `target`, the M's centre block in the logo's targets,
// and resolves once its explosion has cleared. With `walk` false it is already
// standing in front of the logo, and only fuses and blows up.
export async function releaseCreeper(target, { walk }) {
  const sprites = Array.from({ length: 16 }, (_, frame) => explosionSprite(frame));
  if (!(await load(SKIN))) return;
  await Promise.all(sprites.map(load));

  const logo = target.closest(".logo");
  const targets = target.ownerSVGElement;
  const box = logo.getBoundingClientRect();
  const block = target.getBoundingClientRect();
  const px = (targets.getBoundingClientRect().width / targets.viewBox.baseVal.width) * LOGO_UNITS_PER_PIXEL;
  const blockPx = px * TEXTURE_PIXELS_PER_BLOCK;

  // Blocks are measured from the foot of the M's centre block, on the logo's plane.
  const origin = { x: block.left + block.width / 2 - box.left, y: block.bottom - box.top };
  const viewer = {
    x: (box.width / 2 - origin.x) / blockPx,
    y: (box.height / 2 - origin.y) / blockPx,
    z: VIEWER_DISTANCE,
  };
  const toPx = (x, y, z) => `${origin.x + x * blockPx}px, ${origin.y + y * blockPx}px, ${z * blockPx}px`;

  logo.classList.add("creeper-stage");
  logo.style.perspective = `${VIEWER_DISTANCE * blockPx}px`;
  const stage = element("creeper-world", logo);
  stage.setAttribute("aria-hidden", "true");

  const root = element("creeper", stage);
  const parts = [];
  const faces = [];
  for (const [name, { pivot, from, size, uv }] of Object.entries(MODEL)) {
    // Turned to face the viewer: the model's z, negated.
    const place = `translate3d(${pivot[0] * px}px, ${(pivot[1] - MODEL_FLOOR) * px}px, ${-pivot[2] * px}px)`;
    const part = element("creeper-part", root);
    parts.push({ name, part, place });
    const corner = [from[0], from[1], -(from[2] + size[2])];
    for (const face of boxFaces(corner, size, uv, px)) {
      const drawn = element("creeper-face", part);
      drawn.style.width = `${face.size[0] * px}px`;
      drawn.style.height = `${face.size[1] * px}px`;
      drawn.style.transform = face.transform;
      drawn.style.backgroundSize = `${TEXTURE_SIZE[0] * px}px ${TEXTURE_SIZE[1] * px}px`;
      drawn.style.backgroundPosition = `${-face.uv[0] * px}px ${-face.uv[1] * px}px`;
      faces.push({ drawn, part: name, normal: face.normal });
    }
  }

  const drawnParticles = new Map();
  const world = creeperWorld(viewer, { walk });

  function draw(t) {
    const now = world.creeper;
    const was = world.before;
    if (!now.alive) {
      root.hidden = true;
    } else {
      const x = lerp(t, was.x, now.x);
      const z = lerp(t, was.z, now.z);
      const bodyYaw = lerpAngle(t, was.yBodyRot, now.yBodyRot);
      const headYaw = wrap(lerpAngle(t, was.yHeadRot, now.yHeadRot) - bodyYaw);
      const headPitch = lerp(t, was.xHeadRot, now.xHeadRot);
      // WalkAnimationState.speed(partialTicks) and position(partialTicks).
      const walkSpeed = Math.min(lerp(t, was.walkSpeed, now.walkSpeed), 1);
      const walkPosition = now.walkPosition - now.walkSpeed * (1 - t);
      // Creeper.getSwelling.
      const swelling = lerp(t, was.swell, now.swell) / (FUSE_TICKS - 2);
      const { wide, tall } = swellScale(swelling);

      root.style.transform = `translate3d(${toPx(x, 0, z)}) rotateY(${bodyYaw}rad) scale3d(${wide}, ${tall}, ${wide})`;
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

    for (const [particle, drawn] of drawnParticles) {
      if (!world.particles.includes(particle)) {
        drawn.remove();
        drawnParticles.delete(particle);
      }
    }
    for (const particle of world.particles) {
      let drawn = drawnParticles.get(particle);
      if (!drawn) {
        drawn = element("explosion-particle", stage);
        const side = 2 * particle.halfSize * blockPx;
        drawn.style.width = drawn.style.height = `${side}px`;
        drawn.style.margin = `${-side / 2}px 0 0 ${-side / 2}px`;
        drawn.style.filter = `brightness(${particle.shade})`;
        // SingleQuadParticle's LOOKAT_XYZ: turned to face the viewer's eye.
        const dx = viewer.x - particle.x;
        const dz = viewer.z - particle.z;
        const yaw = yawToward(dx, dz);
        const pitch = Math.atan2(-particle.y - viewer.y, Math.hypot(dx, dz));
        drawn.style.transform = `translate3d(${toPx(particle.x, -particle.y, particle.z)}) rotateY(${yaw}rad) rotateX(${pitch}rad)`;
        drawnParticles.set(particle, drawn);
      }
      drawn.style.backgroundImage = `url(${explosionSprite(explosionFrame(particle))})`;
    }
  }

  await new Promise((done) => {
    let last = performance.now();
    let behind = 0;
    draw(1);
    function frame(time) {
      behind += time - last;
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

  stage.remove();
  logo.classList.remove("creeper-stage");
  logo.style.perspective = "";
}
