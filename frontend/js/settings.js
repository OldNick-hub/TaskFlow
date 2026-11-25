(() => {
  'use strict';

  const dateFormatter = new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  });

  function redirectToLogin(reason) {
    const target = reason ? `login.html?reason=${encodeURIComponent(reason)}` : 'login.html';
    window.location.replace(target);
  }

  function formatDate(isoString) {
    if (!isoString) return '—';
    const parsed = new Date(isoString);
    if (Number.isNaN(parsed.getTime())) return '—';
    return dateFormatter.format(parsed);
  }

  function initSidebarDrawer() {
    const sidebar = document.getElementById('dashboard-sidebar');
    const backdrop = document.getElementById('sidebar-backdrop');
    const openBtn = document.getElementById('sidebar-open-btn');
    const closeBtn = document.getElementById('sidebar-close-btn');
    if (!sidebar || !openBtn) return;

    const desktopMedia = window.matchMedia('(min-width: 1024px)');

    const setOpen = (isOpen) => {
      sidebar.classList.toggle('is-open', isOpen);
      openBtn.setAttribute('aria-expanded', String(isOpen));
      if (backdrop) backdrop.hidden = !isOpen;
      if (!desktopMedia.matches && isOpen && closeBtn) closeBtn.focus();
    };

    const closeDrawer = ({ restoreFocus = false } = {}) => {
      if (!sidebar.classList.contains('is-open')) return;
      setOpen(false);
      if (restoreFocus && !desktopMedia.matches) openBtn.focus();
    };

    openBtn.addEventListener('click', () => setOpen(true));
    if (closeBtn) closeBtn.addEventListener('click', () => closeDrawer({ restoreFocus: true }));
    if (backdrop) backdrop.addEventListener('click', () => closeDrawer({ restoreFocus: true }));

    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && sidebar.classList.contains('is-open') && !desktopMedia.matches) {
        closeDrawer({ restoreFocus: true });
      }
    });
  }

  function showFeedback(type, message) {
    const banner = document.getElementById('settings-feedback');
    if (!banner) return;
    banner.className = `alert alert-${type}`;
    banner.textContent = message;
    banner.hidden = false;
  }

  function renderUserProfile(user) {
    const sidebarName = document.getElementById('sidebar-user-name');
    const sidebarEmail = document.getElementById('sidebar-user-email');
    const nameEl = document.getElementById('profile-name');
    const emailEl = document.getElementById('profile-email');
    const idEl = document.getElementById('profile-id');
    const createdEl = document.getElementById('profile-created-at');

    if (sidebarName) sidebarName.textContent = user.name || user.email || 'Workspace User';
    if (sidebarEmail) sidebarEmail.textContent = user.email || '—';
    if (nameEl) nameEl.textContent = user.name || '—';
    if (emailEl) emailEl.textContent = user.email || '—';
    if (idEl) idEl.textContent = user.id || '—';
    if (createdEl) createdEl.textContent = formatDate(user.createdAt);
  }

  function updateSystemThemeLabel() {
    const descEl = document.getElementById('theme-system-desc');
    if (!descEl || typeof window.matchMedia !== 'function') return;
    const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    descEl.textContent = `Follow OS (currently ${isDark ? 'dark' : 'light'})`;
  }

  function syncPreferenceControls() {
    if (!window.TaskFlowPrefs) return;
    const currentTheme = window.TaskFlowPrefs.getTheme();
    const currentDensity = window.TaskFlowPrefs.getDensity();

    const themeInput = document.querySelector(`input[name="theme"][value="${currentTheme}"]`);
    if (themeInput) themeInput.checked = true;

    const densityInput = document.querySelector(`input[name="density"][value="${currentDensity}"]`);
    if (densityInput) densityInput.checked = true;
  }

  function initPreferencesControls() {
    if (!window.TaskFlowPrefs) return;

    updateSystemThemeLabel();
    syncPreferenceControls();

    if (typeof window.matchMedia === 'function') {
      const media = window.matchMedia('(prefers-color-scheme: dark)');
      if (typeof media.addEventListener === 'function') {
        media.addEventListener('change', updateSystemThemeLabel);
      }
    }

    document.querySelectorAll('input[name="theme"]').forEach((radio) => {
      radio.addEventListener('change', () => {
        if (!radio.checked) return;
        const applied = window.TaskFlowPrefs.setTheme(radio.value);
        showFeedback('success', `Color theme updated to "${applied}".`);
      });
    });

    document.querySelectorAll('input[name="density"]').forEach((radio) => {
      radio.addEventListener('change', () => {
        if (!radio.checked) return;
        const applied = window.TaskFlowPrefs.setDensity(radio.value);
        showFeedback('success', `Layout density updated to "${applied}".`);
      });
    });

    const resetBtn = document.getElementById('reset-preferences-btn');
    if (resetBtn) {
      resetBtn.addEventListener('click', () => {
        window.TaskFlowPrefs.resetPreferences();
        syncPreferenceControls();
        showFeedback('success', 'Display preferences reset to defaults.');
      });
    }

    window.addEventListener('storage', syncPreferenceControls);
  }

  function initLogoutButtons() {
    const buttons = [
      document.getElementById('sidebar-logout-btn'),
      document.getElementById('settings-logout-btn')
    ].filter(Boolean);

    const performLogout = async () => {
      buttons.forEach((btn) => {
        btn.disabled = true;
      });
      const settingsLogoutBtn = document.getElementById('settings-logout-btn');
      if (settingsLogoutBtn) {
        settingsLogoutBtn.textContent = 'Signing out…';
      }

      try {
        await window.TaskFlowApi.auth.logout();
        window.location.replace('login.html?logged_out=1');
      } catch (err) {
        window.TaskFlowApi.clearSession();
        if (err && err.status === 401) {
          redirectToLogin('session_expired');
          return;
        }
        showFeedback('error', err?.message || 'Logout encountered a server error; local session was cleared.');
        setTimeout(() => {
          window.location.replace('login.html?logged_out=1');
        }, 900);
      }
    };

    buttons.forEach((btn) => btn.addEventListener('click', performLogout));
  }

  async function loadVerifiedProfile() {
    const guardStatus = document.getElementById('auth-guard-status');
    const errorBanner = document.getElementById('settings-error-banner');
    const errorText = document.getElementById('settings-error-text');
    const content = document.getElementById('settings-content');

    const token = window.TaskFlowApi.getAccessToken();
    if (!token) {
      redirectToLogin('auth_required');
      return;
    }

    if (errorBanner) errorBanner.hidden = true;
    if (guardStatus) guardStatus.hidden = false;

    try {
      const user = await window.TaskFlowApi.auth.me();
      if (guardStatus) guardStatus.hidden = true;
      if (content) content.hidden = false;
      renderUserProfile(user);
    } catch (err) {
      if (err && err.status === 401) {
        window.TaskFlowApi.clearSession();
        redirectToLogin('session_expired');
        return;
      }
      if (guardStatus) guardStatus.hidden = true;
      if (errorText) {
        errorText.textContent = err?.message || 'Unable to verify your session with the server.';
      }
      if (errorBanner) errorBanner.hidden = false;
    }
  }

  window.addEventListener('taskflow:unauthorized', () => {
    redirectToLogin('session_expired');
  });

  document.addEventListener('DOMContentLoaded', () => {
    initSidebarDrawer();
    initPreferencesControls();
    initLogoutButtons();

    const retryBtn = document.getElementById('retry-settings-btn');
    if (retryBtn) {
      retryBtn.addEventListener('click', loadVerifiedProfile);
    }

    loadVerifiedProfile();
  });
})();
