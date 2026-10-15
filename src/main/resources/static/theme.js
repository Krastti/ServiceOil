'use strict';

// Apply the saved palette before styles load, including when storage is unavailable.
(() => {
  const key = 'serviceoil-theme';
  const root = document.documentElement;
  const systemTheme = window.matchMedia('(prefers-color-scheme: dark)');
  let explicitChoice = false;

  function preferredTheme() {
    let saved;
    try { saved = localStorage.getItem(key); } catch { /* Use the system preference. */ }
    explicitChoice = saved === 'light' || saved === 'dark';
    return explicitChoice ? saved : (systemTheme.matches ? 'dark' : 'light');
  }

  function syncButton() {
    const button = document.getElementById('theme-toggle');
    if (!button) return;
    const dark = root.dataset.theme === 'dark';
    const label = dark ? 'Включить светлую тему' : 'Включить тёмную тему';
    button.setAttribute('aria-label', label);
    button.setAttribute('aria-pressed', String(dark));
    button.title = label;
    button.querySelector('i').className = 'fas ' + (dark ? 'fa-sun' : 'fa-moon');
    button.querySelector('.theme-toggle-label').textContent = dark ? 'Светлая тема' : 'Тёмная тема';
  }

  function apply(theme) {
    root.dataset.theme = theme;
    const browserColor = document.querySelector('meta[name="theme-color"]');
    if (browserColor) browserColor.content = theme === 'dark' ? '#171e28' : '#4e73df';
    syncButton();
  }

  apply(preferredTheme());
  document.addEventListener('DOMContentLoaded', () => {
    syncButton();
    document.getElementById('theme-toggle').addEventListener('click', () => {
      const next = root.dataset.theme === 'dark' ? 'light' : 'dark';
      explicitChoice = true;
      try { localStorage.setItem(key, next); } catch { /* Keep the choice for this page. */ }
      apply(next);
    });
  }, { once: true });

  systemTheme.addEventListener('change', () => {
    if (!explicitChoice) apply(systemTheme.matches ? 'dark' : 'light');
  });
  window.addEventListener('storage', event => {
    if (event.key === key || event.key === null) apply(preferredTheme());
  });
})();
