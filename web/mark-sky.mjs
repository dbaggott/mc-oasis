// The script every page's <head> runs to mark the sky for the stylesheet
// (vite.config.js publishes it). A classic script, not a module like main.js:
// a module runs only once the page is parsed, after its first paint, which
// would show that paint without a backdrop. The skies come from sky.js; the
// choice between them is skyFor's, restated, since the script must stand alone.
import { SKIES, SKY_BY_HOUR } from "./src/sky.js";

export const markSkyScript = () => `{
const skies = ${JSON.stringify([...SKIES])};
const byHour = ${JSON.stringify(SKY_BY_HOUR)};
const asked = new URLSearchParams(location.search).get("sky");
document.documentElement.dataset.sky = skies.includes(asked) ? asked : byHour[new Date().getHours()];
}
`;
