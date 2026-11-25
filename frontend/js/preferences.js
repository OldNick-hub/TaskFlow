(() => {
  'use strict';

  const THEME_KEY = 'taskflow.pref.theme';
  const DENSITY_KEY = 'taskflow.pref.density';
  const VALID_THEMES = new Set(['light', 'dark']);
  const VALID_DENSITIES = new Set(['comfortable', 'compact']);

  function readStorage(key) {
    try {
      return window.localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  function writeStorage(key, value) {
    try {
      if (value === null || value === undefined) {
        window.localStorage.removeItem(key);
      } else {
        window.localStorage.setItem(key, value);
      }
    } catch {
      // Ignore storage access restrictions.
    }
  }

  function getTheme() {
    const stored = readStorage(THEME_KEY);
    return VALID_THEMES.has(stored) ? stored : 'system';
  }

  function getDensity() {
    const stored = readStorage(DENSITY_KEY);
    return VALID_DENSITIES.has(stored) ? stored : 'comfortable';
  }

  function applyTheme(theme) {
    const meta = document.querySelector('meta[name="color-scheme"]');
    const contentValue = VALID_THEMES.has(theme) ? theme : 'light dark';
    if (meta) {
      meta.setAttribute('content', contentValue);
    }
    document.documentElement.dataset.theme = VALID_THEMES.has(theme) ? theme : 'system';
  }

  function applyDensity(density) {
    const resolved = VALID_DENSITIES.has(density) ? density : 'comfortable';
    document.documentElement.dataset.density = resolved;
  }

  function setTheme(theme) {
    const normalized = VALID_THEMES.has(theme) ? theme : 'system';
    writeStorage(THEME_KEY, normalized === 'system' ? null : normalized);
    applyTheme(normalized);
    return normalized;
  }

  function setDensity(density) {
    const normalized = VALID_DENSITIES.has(density) ? density : 'comfortable';
    writeStorage(DENSITY_KEY, normalized === 'comfortable' ? null : normalized);
    applyDensity(normalized);
    return normalized;
  }

  function resetPreferences() {
    writeStorage(THEME_KEY, null);
    writeStorage(DENSITY_KEY, null);
    applyTheme('system');
    applyDensity('comfortable');
  }

  // Apply immediately on parse to prevent flash of unthemed content
  applyTheme(getTheme());
  applyDensity(getDensity());

  window.addEventListener('storage', (event) => {
    if (event.key === THEME_KEY) {
      applyTheme(getTheme());
    } else if (event.key === DENSITY_KEY) {
      applyDensity(getDensity());
    }
  });

  window.TaskFlowPrefs = {
    getTheme,
    setTheme,
    getDensity,
    setDensity,
    resetPreferences
  };
})();
