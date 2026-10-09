// Every page's styles, and what the shared navigation bar and the Play page's
// copy buttons do. Fonts are bundled into the release: the site loads nothing
// from any other origin.
import "@fontsource-variable/karla";
import "./styles.css";

// On a narrow screen the links fold behind a menu button.
const toggle = document.querySelector(".nav-toggle");
toggle?.addEventListener("click", () => {
  const open = toggle.getAttribute("aria-expanded") !== "true";
  toggle.setAttribute("aria-expanded", String(open));
  toggle.closest(".nav").classList.toggle("nav-open", open);
});

// A button that copies its data-copy value, a server address, for pasting into
// Minecraft. Where the clipboard is refused, the address beside it is selected
// instead, ready for the visitor to copy themselves.
for (const button of document.querySelectorAll("[data-copy]")) {
  button.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(button.dataset.copy);
      button.textContent = "Copied!";
    } catch {
      const code = button.parentElement.querySelector("code");
      const range = document.createRange();
      range.selectNodeContents(code);
      getSelection().removeAllRanges();
      getSelection().addRange(range);
      button.textContent = "Selected";
    }
    setTimeout(() => (button.textContent = "Copy"), 1800);
  });
}
