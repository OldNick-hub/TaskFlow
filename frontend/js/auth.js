(() => {
  'use strict';

  const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  const supportsUserInvalid = typeof CSS !== 'undefined' && CSS.supports('selector(:user-invalid)');

  // Polite live region for announcing errors on blur/change without premature load-time announcements
  const announcer = document.createElement('div');
  announcer.setAttribute('aria-live', 'polite');
  announcer.classList.add('visually-hidden');
  document.body.append(announcer);

  let announceTimer = null;
  let announcedField = null;
  const flagged = new WeakSet();
  const announced = new WeakSet();
  const interactionState = new WeakMap();

  function linkError(el, shown) {
    const errorId = el.dataset.error;
    if (!errorId) return;
    const ids = (el.getAttribute('aria-describedby') || '')
      .split(/\s+/)
      .filter((id) => id && id !== errorId);
    if (shown) {
      ids.push(errorId);
    }
    if (ids.length) {
      el.setAttribute('aria-describedby', ids.join(' '));
    } else {
      el.removeAttribute('aria-describedby');
    }
  }

  function announceError(el) {
    const errorEl = document.getElementById(el.dataset.error || '');
    if (!errorEl) return;
    const labelText = el.labels?.[0]?.textContent?.replace(/\*/g, '').replace(/\s+/g, ' ').trim() || '';
    const prefix = labelText ? `${labelText}: ` : '';
    clearTimeout(announceTimer);
    announcer.textContent = '';
    announcedField = el;
    announceTimer = setTimeout(() => {
      announcer.textContent = `${prefix}${errorEl.textContent.replace(/\s+/g, ' ').trim()}`;
    }, 450);
  }

  function applyCustomFieldRules(el) {
    el.setCustomValidity('');

    if (el.name === 'name') {
      const trimmed = el.value.trim();
      if (trimmed.length === 0) {
        el.setCustomValidity('Full name is required.');
      } else if (trimmed.length < 2 || trimmed.length > 100) {
        el.setCustomValidity('Full name must be between 2 and 100 characters.');
      }
    } else if (el.type === 'email') {
      const trimmed = el.value.trim();
      if (trimmed.length === 0) {
        el.setCustomValidity('Email address is required.');
      } else if (!EMAIL_REGEX.test(trimmed)) {
        el.setCustomValidity('Enter a valid email address such as you@example.com.');
      }
    } else if (el.name === 'confirmPassword') {
      const passwordInput = el.form?.elements.namedItem('password');
      if (passwordInput && el.value !== passwordInput.value) {
        el.setCustomValidity('Passwords do not match.');
      }
    }

    const errorEl = document.getElementById(el.dataset.error || '');
    if (errorEl && el.validationMessage) {
      errorEl.textContent = el.validationMessage;
    }
  }

  function setFieldError(el, message, { announce = false } = {}) {
    const errorEl = document.getElementById(el.dataset.error || '');
    if (errorEl && message) {
      errorEl.textContent = message;
    }
    el.classList.add('user-invalid-fallback');
    el.classList.remove('user-valid-fallback');
    el.setAttribute('aria-invalid', 'true');
    linkError(el, true);
    flagged.add(el);
    if (announce && !announced.has(el)) {
      announceError(el);
      announced.add(el);
    }
  }

  function clearFieldError(el) {
    el.setAttribute('aria-invalid', 'false');
    el.classList.remove('user-invalid-fallback');
    if (el.value.trim() !== '' && el.validity.valid) {
      el.classList.add('user-valid-fallback');
    } else {
      el.classList.remove('user-valid-fallback');
    }
    linkError(el, false);
    flagged.delete(el);
    announced.delete(el);
    if (announcedField === el) {
      clearTimeout(announceTimer);
      announcer.textContent = '';
    }
  }

  function syncAria(el, { announce = false, forceValidate = false } = {}) {
    if (!el.matches?.('input, select, textarea')) return;

    applyCustomFieldRules(el);
    const isValid = el.validity.valid;
    const state = interactionState.get(el) || { interacted: false, blurred: false };

    const visualInvalid = forceValidate
      ? !isValid
      : supportsUserInvalid
        ? el.matches(':user-invalid') || (state.blurred && state.interacted && !isValid)
        : state.blurred && state.interacted && !isValid;

    if (visualInvalid) {
      setFieldError(el, el.validationMessage, { announce });
    } else if (isValid || flagged.has(el) || el.getAttribute('aria-invalid') !== 'true') {
      clearFieldError(el);
    }
  }

  function wireFormAccessibility(form) {
    form.querySelectorAll('input, select, textarea').forEach((el) => {
      el.setAttribute('aria-invalid', 'false');
    });

    form.addEventListener(
      'blur',
      (event) => {
        const el = event.target;
        if (!el.matches?.('input, select, textarea')) return;
        const state = interactionState.get(el) || { interacted: false, blurred: false };
        state.blurred = true;
        interactionState.set(el, state);
        if (state.interacted) {
          syncAria(el, { announce: true });
        }
      },
      true
    );

    form.addEventListener('input', (event) => {
      const el = event.target;
      if (!el.matches?.('input, select, textarea')) return;
      const state = interactionState.get(el) || { interacted: false, blurred: false };
      state.interacted = true;
      interactionState.set(el, state);

      el.setCustomValidity('');
      if (el.getAttribute('aria-invalid') === 'true') {
        syncAria(el, { announce: false, forceValidate: true });
      }
      if (el.name === 'password') {
        const confirmInput = form.elements.namedItem('confirmPassword');
        if (confirmInput && confirmInput.getAttribute('aria-invalid') === 'true') {
          syncAria(confirmInput, { announce: false, forceValidate: true });
        }
      }
    });
  }

  function wirePasswordToggles() {
    document.querySelectorAll('[data-toggle-password]').forEach((button) => {
      button.addEventListener('click', () => {
        const inputId = button.getAttribute('data-toggle-password');
        const input = document.getElementById(inputId || '');
        if (!input) return;

        const isMasked = input.type === 'password';
        input.type = isMasked ? 'text' : 'password';
        button.setAttribute('aria-pressed', String(isMasked));
        button.setAttribute('aria-label', isMasked ? 'Hide password' : 'Show password');
        button.textContent = isMasked ? 'Hide' : 'Show';
      });
    });
  }

  function showFeedback(type, message) {
    const banner = document.getElementById('auth-feedback');
    if (!banner) return;
    banner.className = `alert alert-${type}`;
    banner.textContent = message;
    banner.hidden = false;
  }

  function clearFeedback() {
    const banner = document.getElementById('auth-feedback');
    if (!banner) return;
    banner.hidden = true;
    banner.textContent = '';
  }

  function validateFormOnSubmit(form) {
    const controls = Array.from(form.querySelectorAll('input, select, textarea'));
    let firstInvalid = null;

    controls.forEach((el) => {
      interactionState.set(el, { interacted: true, blurred: true });
      announced.add(el); // Focus moving to first invalid field announces its error
      syncAria(el, { announce: false, forceValidate: true });
      if (!el.validity.valid && !firstInvalid) {
        firstInvalid = el;
      }
    });

    if (firstInvalid) {
      firstInvalid.focus();
      return false;
    }
    return true;
  }

  function applyServerValidationErrors(form, validationErrors) {
    if (!validationErrors || typeof validationErrors !== 'object') {
      return;
    }
    let firstInvalid = null;
    Object.entries(validationErrors).forEach(([fieldName, message]) => {
      const control = form.elements.namedItem(fieldName);
      if (control && control instanceof HTMLElement) {
        setFieldError(control, String(message), { announce: false });
        if (!firstInvalid) {
          firstInvalid = control;
        }
      }
    });
    if (firstInvalid) {
      firstInvalid.focus();
    }
  }

  function setSubmitting(button, isSubmitting, busyLabel, idleLabel) {
    if (!button) return;
    button.disabled = isSubmitting;
    button.setAttribute('aria-busy', String(isSubmitting));
    button.textContent = isSubmitting ? busyLabel : idleLabel;
  }

  async function redirectIfAlreadyAuthenticated() {
    if (!window.TaskFlowApi.isAuthenticated()) {
      return;
    }
    try {
      await window.TaskFlowApi.auth.me();
      window.location.replace('dashboard.html');
    } catch {
      window.TaskFlowApi.clearSession();
    }
  }

  function initLoginForm(form) {
    const submitBtn = document.getElementById('login-submit');
    const params = new URLSearchParams(window.location.search);
    if (params.get('registered') === '1') {
      showFeedback('success', 'Account created successfully. Sign in to continue.');
    } else if (params.get('logged_out') === '1') {
      showFeedback('success', 'You have been signed out.');
    } else if (params.get('reason') === 'session_expired') {
      showFeedback('error', 'Your session has expired or is no longer valid. Please sign in again.');
    } else if (params.get('reason') === 'auth_required') {
      showFeedback('info', 'Please sign in to access your workspace.');
    }

    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      clearFeedback();

      if (!validateFormOnSubmit(form)) {
        return;
      }

      const payload = {
        email: form.elements.namedItem('email').value.trim(),
        password: form.elements.namedItem('password').value
      };

      setSubmitting(submitBtn, true, 'Signing in…', 'Sign in');
      try {
        const authResponse = await window.TaskFlowApi.auth.login(payload);
        window.TaskFlowApi.setSession(authResponse);
        window.location.assign('dashboard.html');
      } catch (err) {
        if (err.validationErrors) {
          applyServerValidationErrors(form, err.validationErrors);
        }
        showFeedback('error', err.message || 'Unable to sign in. Please check your credentials.');
        setSubmitting(submitBtn, false, 'Signing in…', 'Sign in');
      }
    });
  }

  function initRegisterForm(form) {
    const submitBtn = document.getElementById('register-submit');

    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      clearFeedback();

      if (!validateFormOnSubmit(form)) {
        return;
      }

      const payload = {
        name: form.elements.namedItem('name').value.trim(),
        email: form.elements.namedItem('email').value.trim(),
        password: form.elements.namedItem('password').value,
        confirmPassword: form.elements.namedItem('confirmPassword').value
      };

      setSubmitting(submitBtn, true, 'Creating account…', 'Create account');
      try {
        await window.TaskFlowApi.auth.register(payload);
        window.location.assign('login.html?registered=1');
      } catch (err) {
        if (err.validationErrors) {
          applyServerValidationErrors(form, err.validationErrors);
        } else if (err.status === 409) {
          const emailInput = form.elements.namedItem('email');
          if (emailInput) {
            setFieldError(emailInput, err.message, { announce: false });
            emailInput.focus();
          }
        }
        showFeedback('error', err.message || 'Unable to create account. Please try again.');
        setSubmitting(submitBtn, false, 'Creating account…', 'Create account');
      }
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    wirePasswordToggles();

    const loginForm = document.getElementById('login-form');
    if (loginForm) {
      wireFormAccessibility(loginForm);
      initLoginForm(loginForm);
      redirectIfAlreadyAuthenticated();
    }

    const registerForm = document.getElementById('register-form');
    if (registerForm) {
      wireFormAccessibility(registerForm);
      initRegisterForm(registerForm);
      redirectIfAlreadyAuthenticated();
    }
  });
})();
