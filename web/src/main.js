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
