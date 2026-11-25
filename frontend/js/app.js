(() => {
  'use strict';

  function isProtectedPage() {
    return document.body?.dataset?.requiresAuth === 'true';
  }

  function redirectToLogin(reason) {
    const target = reason ? `login.html?reason=${encodeURIComponent(reason)}` : 'login.html';
    window.location.replace(target);
  }

  function initNavigation() {
    const toggle = document.getElementById('nav-toggle');
    const menu = document.getElementById('primary-nav');
    if (!toggle || !menu) return;

    const closeMenu = () => {
      menu.classList.remove('is-open');
      toggle.setAttribute('aria-expanded', 'false');
    };

    toggle.addEventListener('click', () => {
      const open = menu.classList.toggle('is-open');
      toggle.setAttribute('aria-expanded', String(open));
    });

    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && menu.classList.contains('is-open')) {
        closeMenu();
        toggle.focus();
      }
    });

    menu.querySelectorAll('a').forEach((link) => {
      link.addEventListener('click', closeMenu);
    });
  }

  function renderLandingState(user) {
    const guestActions = document.getElementById('nav-guest-actions');
    const authActions = document.getElementById('nav-auth-actions');
    const heroGuestCta = document.getElementById('hero-guest-cta');
    const heroAuthCta = document.getElementById('hero-auth-cta');
    const sessionLabel = document.getElementById('session-user-label');
    const welcomeText = document.getElementById('welcome-user-text');
    const authBadge = document.getElementById('auth-status-badge');

    if (user) {
      if (guestActions) guestActions.hidden = true;
      if (authActions) authActions.hidden = false;
      if (heroGuestCta) heroGuestCta.hidden = true;
      if (heroAuthCta) heroAuthCta.hidden = false;
      if (sessionLabel) sessionLabel.textContent = user.name || user.email;
      if (welcomeText) welcomeText.textContent = `Signed in as ${user.name} (${user.email}).`;
      if (authBadge) authBadge.textContent = 'Authenticated';
    } else {
      if (guestActions) guestActions.hidden = false;
      if (authActions) authActions.hidden = true;
      if (heroGuestCta) heroGuestCta.hidden = false;
      if (heroAuthCta) heroAuthCta.hidden = true;
      if (authBadge) authBadge.textContent = 'Sign in required';
    }
  }

  function renderProtectedWorkspace(user) {
    const guardStatus = document.getElementById('auth-guard-status');
    const workspaceSection = document.getElementById('protected-workspace');
    const authActions = document.getElementById('nav-auth-actions');
    const sessionLabel = document.getElementById('session-user-label');
    const summary = document.getElementById('workspace-user-summary');
    const nameEl = document.getElementById('profile-name');
    const emailEl = document.getElementById('profile-email');
    const idEl = document.getElementById('profile-id');

    if (guardStatus) guardStatus.hidden = true;
    if (workspaceSection) workspaceSection.hidden = false;
    if (authActions) authActions.hidden = false;
    if (sessionLabel) sessionLabel.textContent = user.name || user.email;
    if (summary) summary.textContent = `Signed in as ${user.name} (${user.email}).`;
    if (nameEl) nameEl.textContent = user.name || '—';
    if (emailEl) emailEl.textContent = user.email || '—';
    if (idEl) idEl.textContent = user.id || '—';
  }

  async function verifyAuthenticatedUser() {
    const protectedRoute = isProtectedPage();
    const token = window.TaskFlowApi.getAccessToken();

    if (!token) {
      if (protectedRoute) {
        redirectToLogin('auth_required');
      } else {
        renderLandingState(null);
      }
      return;
    }

    try {
      const user = await window.TaskFlowApi.auth.me();
      if (protectedRoute) {
        renderProtectedWorkspace(user);
      } else {
        renderLandingState(user);
      }
    } catch {
      window.TaskFlowApi.clearSession();
      if (protectedRoute) {
        redirectToLogin('session_expired');
      } else {
        renderLandingState(null);
      }
    }
  }

  async function checkDatabaseStatus() {
    const dbBadge = document.getElementById('db-status-badge');
    if (!dbBadge) return;

    try {
      const health = await window.TaskFlowApi.health.checkDb();
      dbBadge.textContent = health && health.reachable ? 'Connected' : 'Unavailable';
    } catch {
      dbBadge.textContent = 'Backend offline';
    }
  }

  function initLogout() {
    const logoutBtn = document.getElementById('logout-btn');
    if (!logoutBtn) return;

    logoutBtn.addEventListener('click', async () => {
      logoutBtn.disabled = true;
      try {
        await window.TaskFlowApi.auth.logout();
      } catch {
        window.TaskFlowApi.clearSession();
      } finally {
        logoutBtn.disabled = false;
        if (isProtectedPage()) {
          window.location.replace('login.html?logged_out=1');
        } else {
          renderLandingState(null);
        }
      }
    });
  }

  window.addEventListener('taskflow:unauthorized', () => {
    if (isProtectedPage()) {
      redirectToLogin('session_expired');
    } else {
      renderLandingState(null);
    }
  });

  document.addEventListener('DOMContentLoaded', () => {
    initNavigation();
    initLogout();
    verifyAuthenticatedUser();
    checkDatabaseStatus();
  });
})();
