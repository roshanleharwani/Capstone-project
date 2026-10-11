export type Theme = "light" | "dark";

export const THEME_STORAGE_KEY = "nexus-theme";

export function applyTheme(theme: Theme) {
  const root = document.documentElement;
  root.classList.remove("light", "dark");
  root.classList.add(theme);
  root.style.colorScheme = theme;
  localStorage.setItem(THEME_STORAGE_KEY, theme);
  window.dispatchEvent(new Event("nexus-theme"));
}

export function readStoredTheme(): Theme {
  const stored = localStorage.getItem(THEME_STORAGE_KEY);
  if (stored === "light" || stored === "dark") return stored;
  return window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
}

export const THEME_BOOTSTRAP_SCRIPT = `(() => {
  try {
    const key = "${THEME_STORAGE_KEY}";
    const stored = localStorage.getItem(key);
    const theme = stored === "light" || stored === "dark"
      ? stored
      : (window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark");
    const root = document.documentElement;
    root.classList.remove("light", "dark");
    root.classList.add(theme);
    root.style.colorScheme = theme;
  } catch (_) {}
})();`;
