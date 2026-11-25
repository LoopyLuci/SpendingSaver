// Runs before first paint (loaded as a blocking external script, so no inline script is needed under a strict CSP).
// Mirrors the theme and accent saved in Settings, which live in IndexedDB and are not readable synchronously.
(function () {
  var theme = 'system';
  var accent = 'teal';
  try {
    theme = localStorage.getItem('ss-theme') || theme;
    accent = localStorage.getItem('ss-accent') || accent;
  } catch (e) { /* storage blocked */ }
  var dark = theme === 'dark' || (theme !== 'light' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  var root = document.documentElement;
  root.setAttribute('data-theme', dark ? 'dark' : 'light');
  root.setAttribute('data-accent', accent);
})();
