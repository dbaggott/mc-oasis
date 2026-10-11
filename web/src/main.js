// Every page's styles, and what the shared navigation bar and the copy
// buttons do. Fonts are bundled into the release: the site loads nothing
// from any other origin.
import "@fontsource-variable/karla";
import "./styles.css";
import { otherSkies } from "./sky.js";

// On a narrow screen the links fold behind a menu button. The stylesheet hides
// them only once this script has run and marked the page, so without it they
// stay in sight.
document.documentElement.classList.add("js");

// The sky the page loaded with, as mark-sky.mjs's script marked it.
const sky = document.documentElement.dataset.sky;

// The stylesheet fetches a sky's picture only once that sky is marked, and
// until it arrives the backdrop is bare. So the picture is fetched and decoded
// first, in the format the stylesheet would pick, read from the stylesheet's
// own rule for that sky; a picture that fails to load still gets its sky. Each
// sky's picture is loaded once and held, so going back to it is immediate.
const avif = CSS.supports("background-image", 'image-set("" type("image/avif"))');
const loaded = new Map();
function loadSky(sky) {
  if (!loaded.has(sky)) {
    const probe = document.createElement("i");
    probe.dataset.sky = sky;
    probe.hidden = true;
    document.body.append(probe);
    const url = getComputedStyle(probe).getPropertyValue(avif ? "--sky-avif" : "--sky-webp");
    probe.remove();
    const picture = new Image();
    picture.src = url.match(/url\(\s*["']?([^"')]+)/)?.[1] ?? "";
    const held = () => picture;
    loaded.set(sky, picture.decode().then(held, held));
  }
  return loaded.get(sky);
}

// A hidden extra on the home page's logo: each letter of "OASIS" shows one of
// the other skies, and "SMP" the page's own again. logoTargets() in logo.mjs
// lays the targets out in that order. Only the latest click's sky is shown.
const skies = [...otherSkies(sky), sky];
let wanted = sky;
document.querySelectorAll('.logo-targets [data-egg="sky"]').forEach((target, i) => {
  target.addEventListener("click", async () => {
    wanted = skies[i];
    await loadSky(wanted);
    if (wanted === skies[i]) document.documentElement.dataset.sky = wanted;
  });
});

// Another: the centre of the M in "SMP" lets out a creeper, which climbs out
// of it, hisses, and blows up. One creeper at a time.
const reducedMotion = matchMedia("(prefers-reduced-motion: reduce)");
let creeperOut = false;
document.querySelector('.logo-targets [data-egg="creeper"]')?.addEventListener("click", async (event) => {
  if (creeperOut) return;
  creeperOut = true;
  try {
    await releaseCreeper(event.currentTarget);
  } finally {
    creeperOut = false;
  }
});

// Places an element over the logo in percentages of the logo's box.
function placeOnLogo(element, logo, { left, top, width, height }) {
  const box = logo.getBoundingClientRect();
  const percent = (value, of) => `${(value / of) * 100}%`;
  element.setAttribute("aria-hidden", "true");
  element.style.left = percent(left - box.left, box.width);
  element.style.top = percent(top - box.top, box.height);
  element.style.width = percent(width, box.width);
  if (height !== undefined) element.style.height = percent(height, box.height);
  logo.append(element);
}

async function releaseCreeper(target) {
  const logo = target.closest(".logo");
  const targets = target.ownerSVGElement;
  // Screen pixels per logo unit, the units the creeper's art is sized in.
  const unit = targets.getBoundingClientRect().width / targets.viewBox.baseVal.width;
  const block = target.getBoundingClientRect();
  const still = reducedMotion.matches;

  const creeper = new Image();
  creeper.className = "creeper";
  creeper.alt = "";
  const loaded = new Promise((resolve) => {
    creeper.onload = () => resolve(true);
    creeper.onerror = () => resolve(false);
  });
  creeper.src = "/creeper.svg";
  if (!(await loaded)) return;
  const width = creeper.naturalWidth * unit;
  const height = creeper.naturalHeight * unit;
  // Standing in front of the block it came out of, its feet at the block's foot.
  placeOnLogo(creeper, logo, {
    left: block.left + block.width / 2 - width / 2,
    top: block.bottom - height,
    width,
  });

  await creeper.animate(
    still
      ? [{ opacity: 0 }, { opacity: 1 }]
      : [
          { transform: `translateY(${-block.height / 2}px) scale(0.1)`, opacity: 0 },
          { transform: "none", opacity: 1 },
        ],
    { duration: 500, easing: "cubic-bezier(0.3, 1.5, 0.6, 1)" },
  ).finished;

  // The fuse: flashing white and swelling, as a creeper does before it blows,
  // each step held until the next. It ends on a flash, held to the end.
  const steps = 8;
  const fuse = Array.from({ length: steps }, (_, i) => ({
    offset: i / steps,
    easing: "steps(1, end)",
    filter: i % 2 ? "brightness(2.6) saturate(0)" : "none",
    transform: still ? "none" : `scale(${1 + 0.04 * i}, ${1 + 0.025 * i})`,
  }));
  fuse.push({ ...fuse.at(-1), offset: 1 });
  await creeper.animate(fuse, { duration: 1500, delay: 300, fill: "forwards" }).finished;

  const centre = creeper.getBoundingClientRect();
  const cx = centre.left + centre.width / 2;
  const cy = centre.top + centre.height / 2;
  creeper.remove();

  const blast = height * 1.6;
  const flash = document.createElement("i");
  flash.className = "creeper-flash";
  placeOnLogo(flash, logo, { left: cx - blast / 2, top: cy - blast / 2, width: blast, height: blast });

  const bursts = [
    flash.animate(
      still
        ? [{ opacity: 0.9 }, { opacity: 0 }]
        : [
            { transform: "scale(0.2)", opacity: 1 },
            { transform: "scale(1)", opacity: 0 },
          ],
      { duration: 350, easing: "ease-out", fill: "forwards" },
    ),
  ];

  // Smoke: grey and white puffs of the logo's own pixel size, thrown outward.
  for (let i = 0; i < 28; i++) {
    const size = (4 + Math.random() * 6) * unit;
    const angle = Math.random() * 2 * Math.PI;
    const reach = blast * (0.25 + Math.random() * 0.5);
    const dx = Math.cos(angle) * reach;
    const dy = Math.sin(angle) * reach;
    const puff = document.createElement("i");
    puff.className = "creeper-smoke";
    puff.style.backgroundColor = `hsl(0 0% ${45 + Math.round(Math.random() * 55)}%)`;
    if (still) {
      placeOnLogo(puff, logo, { left: cx + dx - size / 2, top: cy + dy - size / 2, width: size, height: size });
      bursts.push(puff.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 900, fill: "forwards" }));
    } else {
      placeOnLogo(puff, logo, { left: cx - size / 2, top: cy - size / 2, width: size, height: size });
      bursts.push(
        puff.animate(
          [
            { transform: "translate(0, 0) scale(0.5)", opacity: 1 },
            { transform: `translate(${dx}px, ${dy}px) scale(1.4)`, opacity: 0 },
          ],
          { duration: 600 + Math.random() * 600, easing: "cubic-bezier(0.1, 0.8, 0.3, 1)", fill: "forwards" },
        ),
      );
    }
  }

  if (!still) {
    const jolt = unit * 2;
    bursts.push(
      logo.animate(
        [0, 1, -1, 1, -1, 0].map((k, i) => ({ transform: `translate(${k * jolt}px, ${(i % 2 ? -1 : 1) * k * jolt}px)` })),
        { duration: 400, easing: "linear" },
      ),
    );
  }

  await Promise.all(bursts.map((animation) => animation.finished));
  logo.querySelectorAll(".creeper-flash, .creeper-smoke").forEach((element) => element.remove());
}

const toggle = document.querySelector(".nav-toggle");
toggle?.addEventListener("click", () => {
  const open = toggle.getAttribute("aria-expanded") !== "true";
  toggle.setAttribute("aria-expanded", String(open));
  toggle.closest(".nav").classList.toggle("nav-open", open);
});

// A button that copies its data-copy value: a server address on the Play page,
// the calendar's address on the Schedule page. Where the asynchronous clipboard
// is missing or refused, the address beside it, shown if it was hidden, is
// selected and copied the older way; where that fails too, it stays selected
// for the visitor to copy themselves.
async function copy(button) {
  try {
    await navigator.clipboard.writeText(button.dataset.copy);
    return "Copied!";
  } catch {
    const address = button.parentElement.querySelector("code");
    address.closest("[hidden]")?.removeAttribute("hidden");
    const range = document.createRange();
    range.selectNodeContents(address);
    getSelection().removeAllRanges();
    getSelection().addRange(range);
    return document.execCommand("copy") ? "Copied!" : "Selected";
  }
}

for (const button of document.querySelectorAll("[data-copy]")) {
  const label = button.textContent;
  let reset;
  button.addEventListener("click", async () => {
    // Held at its width so a shorter "Copied!" doesn't reflow the buttons beside it.
    button.style.minWidth = `${button.offsetWidth}px`;
    button.textContent = await copy(button);
    clearTimeout(reset);
    reset = setTimeout(() => (button.textContent = label), 1800);
  });
}
