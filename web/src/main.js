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
// own rule for that sky; a picture that fails to load still gets its sky.
const avif = CSS.supports("background-image", 'image-set("" type("image/avif"))');
async function loadSky(sky) {
  const probe = document.createElement("i");
  probe.dataset.sky = sky;
  probe.hidden = true;
  document.body.append(probe);
  const url = getComputedStyle(probe).getPropertyValue(avif ? "--sky-avif" : "--sky-webp");
  probe.remove();
  const picture = new Image();
  picture.src = url.match(/url\(\s*["']?([^"')]+)/)[1];
  await picture.decode().catch(() => {});
}

// A hidden extra on the home page's logo: each letter of "OASIS" shows one of
// the other skies, and "SMP" the page's own again. logoTargets() in logo.mjs
// lays the targets out in that order. Only the latest click's sky is shown.
const skies = [...otherSkies(sky), sky];
let wanted = sky;
document.querySelectorAll(".logo-targets path").forEach((target, i) => {
  target.addEventListener("click", async () => {
    wanted = skies[i];
    await loadSky(wanted);
    if (wanted === skies[i]) document.documentElement.dataset.sky = wanted;
  });
});

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
