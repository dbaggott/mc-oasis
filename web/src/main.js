// Every page's styles, and what the shared navigation bar and the copy
// buttons do. Fonts are bundled into the release: the site loads nothing
// from any other origin.
import "@fontsource-variable/karla";
import "./styles.css";

// On a narrow screen the links fold behind a menu button. The stylesheet hides
// them only once this script has run and marked the page, so without it they
// stay in sight.
document.documentElement.classList.add("js");

const toggle = document.querySelector(".nav-toggle");
toggle?.addEventListener("click", () => {
  const open = toggle.getAttribute("aria-expanded") !== "true";
  toggle.setAttribute("aria-expanded", String(open));
  toggle.closest(".nav").classList.toggle("nav-open", open);
});

// A button that copies its data-copy value: a server address on the Play page,
// the calendar's address on the Schedule page. Where the asynchronous clipboard
// is missing or refused, the text is selected and copied the older way: the
// address shown beside the button if there is one, so that where this fails
// too it stays selected for the visitor to copy themselves.
async function copy(button) {
  const text = button.dataset.copy;
  try {
    await navigator.clipboard.writeText(text);
    return "Copied!";
  } catch {
    const shown = button.parentElement.querySelector("code");
    const field = shown ? null : document.body.appendChild(offscreenField(text));
    const range = document.createRange();
    range.selectNodeContents(shown ?? field);
    getSelection().removeAllRanges();
    getSelection().addRange(range);
    const copied = document.execCommand("copy");
    field?.remove();
    if (copied) return "Copied!";
    return shown ? "Selected" : "Couldn't copy";
  }
}

function offscreenField(text) {
  const field = document.createElement("pre");
  field.textContent = text;
  field.style.cssText = "position: fixed; left: -9999px;";
  return field;
}

for (const button of document.querySelectorAll("[data-copy]")) {
  const label = button.textContent;
  let reset;
  button.addEventListener("click", async () => {
    button.textContent = await copy(button);
    clearTimeout(reset);
    reset = setTimeout(() => (button.textContent = label), 1800);
  });
}
