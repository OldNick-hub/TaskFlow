(() => {
  'use strict';

  const TOKEN_STORAGE_KEY = 'taskflow.accessToken';
  const USER_STORAGE_KEY = 'taskflow.user';
  const DEFAULT_LOCAL_BACKEND_ORIGIN = 'http://localhost:8080';
  const PLACEHOLDER_API_VALUES = new Set(['', 'BACKEND_URL', 'REPLACE_WITH_RENDER_BACKEND_URL']);

  class ApiError extends Error {
    constructor(message, { status = 0, validationErrors = null, payload = null } = {}) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
      this.validationErrors = validationErrors;
      this.payload = payload;
    }
  }

  function isLocalOrigin() {
    const { protocol, hostname } = window.location;
    return (
      protocol === 'file:' ||
      hostname === 'localhost' ||
      hostname === '127.0.0.1' ||
      hostname === '::1' ||
      hostname === '[::1]'
    );
  }

  function resolveBaseUrl() {
    const meta = document.querySelector('meta[name="taskflow-api-base"]');
    const rawMeta = meta && typeof meta.content === 'string' ? meta.content.trim() : '';

    if (rawMeta && !PLACEHOLDER_API_VALUES.has(rawMeta)) {
      let parsed;
      try {
        parsed = new URL(rawMeta);
      } catch {
        throw new ApiError(
          'Frontend configuration error: <meta name="taskflow-api-base"> must be a valid http(s) URL.',
          { status: 0 }
        );
      }
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
        throw new ApiError(
          'Frontend configuration error: <meta name="taskflow-api-base"> must use http or https.',
          { status: 0 }
        );
      }
      return rawMeta.replace(/\/+$/, '');
    }

    if (isLocalOrigin()) {
      const { protocol, port } = window.location;
      if ((protocol === 'http:' || protocol === 'https:') && port === '8080') {
        return '';
      }
      return DEFAULT_LOCAL_BACKEND_ORIGIN;
    }

    throw new ApiError(
      'Frontend configuration error: <meta name="taskflow-api-base"> is not configured for production.',
      { status: 0 }
    );
  }

  function getAccessToken() {
    try {
      return window.sessionStorage.getItem(TOKEN_STORAGE_KEY);
    } catch {
      return null;
    }
  }

  function getStoredUser() {
    try {
      const raw = window.sessionStorage.getItem(USER_STORAGE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  function isAuthenticated() {
    return Boolean(getAccessToken());
  }

  function setSession(authResponse) {
    if (!authResponse || !authResponse.accessToken) {
      return;
    }
    try {
      window.sessionStorage.setItem(TOKEN_STORAGE_KEY, authResponse.accessToken);
      if (authResponse.user) {
        window.sessionStorage.setItem(USER_STORAGE_KEY, JSON.stringify(authResponse.user));
      }
    } catch {
      // Storage may be unavailable in private/locked environments.
    }
  }

  function clearSession() {
    try {
      window.sessionStorage.removeItem(TOKEN_STORAGE_KEY);
      window.sessionStorage.removeItem(USER_STORAGE_KEY);
      window.localStorage.removeItem(TOKEN_STORAGE_KEY);
      window.localStorage.removeItem(USER_STORAGE_KEY);
    } catch {
      // Ignore storage cleanup errors.
    }
  }

  async function parseBody(response) {
    if (response.status === 204) {
      return null;
    }
    const contentType = response.headers.get('content-type') || '';
    const text = await response.text();
    if (!text) {
      return null;
    }
    if (contentType.includes('application/json')) {
      try {
        return JSON.parse(text);
      } catch {
        return null;
      }
    }
    return text;
  }

  async function request(path, options = {}) {
    const {
      method = 'GET',
      body = undefined,
      headers = {},
      auth = true,
      query = null
    } = options;

    const baseUrl = resolveBaseUrl();
    const url = new URL(`${baseUrl}${path}`, window.location.href);
    if (query && typeof query === 'object') {
      Object.entries(query).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
          url.searchParams.set(key, String(value));
        }
      });
    }

    const requestHeaders = new Headers({
      Accept: 'application/json',
      ...headers
    });

    if (body !== undefined && !requestHeaders.has('Content-Type')) {
      requestHeaders.set('Content-Type', 'application/json');
    }

    if (auth) {
      const token = getAccessToken();
      if (token) {
        requestHeaders.set('Authorization', `Bearer ${token}`);
      }
    }

    let response;
    try {
      response = await fetch(url.toString(), {
        method,
        headers: requestHeaders,
        body: body !== undefined ? JSON.stringify(body) : undefined
      });
    } catch {
      throw new ApiError('Unable to reach the TaskFlow server. Verify the backend is running.', {
        status: 0
      });
    }

    const payload = await parseBody(response);

    if (!response.ok) {
      const message =
        (payload && typeof payload === 'object' && payload.message) ||
        `Request failed with status ${response.status}`;
      const validationErrors =
        payload && typeof payload === 'object' && payload.validationErrors
          ? payload.validationErrors
          : null;

      if (response.status === 401 && auth) {
        clearSession();
        window.dispatchEvent(new CustomEvent('taskflow:unauthorized', { detail: { message } }));
      }

      throw new ApiError(message, {
        status: response.status,
        validationErrors,
        payload
      });
    }

    return payload;
  }

  window.TaskFlowApi = {
    ApiError,
    resolveBaseUrl,
    getAccessToken,
    getStoredUser,
    isAuthenticated,
    setSession,
    clearSession,
    request,

    auth: {
      register(data) {
        return request('/api/auth/register', { method: 'POST', body: data, auth: false });
      },
      login(data) {
        return request('/api/auth/login', { method: 'POST', body: data, auth: false });
      },
      logout() {
        return request('/api/auth/logout', { method: 'POST', auth: true }).finally(() => {
          clearSession();
        });
      },
      me() {
        return request('/api/auth/me', { method: 'GET', auth: true });
      }
    },

    projects: {
      list() {
        return request('/api/projects', { method: 'GET', auth: true });
      },
      get(id) {
        return request(`/api/projects/${encodeURIComponent(id)}`, { method: 'GET', auth: true });
      },
      create(data) {
        return request('/api/projects', { method: 'POST', body: data, auth: true });
      },
      update(id, data) {
        return request(`/api/projects/${encodeURIComponent(id)}`, { method: 'PUT', body: data, auth: true });
      },
      remove(id) {
        return request(`/api/projects/${encodeURIComponent(id)}`, { method: 'DELETE', auth: true });
      }
    },

    tasks: {
      listByProject(projectId, filters = {}) {
        return request(`/api/projects/${encodeURIComponent(projectId)}/tasks`, {
          method: 'GET',
          auth: true,
          query: filters
        });
      },
      create(projectId, data) {
        return request(`/api/projects/${encodeURIComponent(projectId)}/tasks`, {
          method: 'POST',
          body: data,
          auth: true
        });
      },
      get(taskId) {
        return request(`/api/tasks/${encodeURIComponent(taskId)}`, { method: 'GET', auth: true });
      },
      update(taskId, data) {
        return request(`/api/tasks/${encodeURIComponent(taskId)}`, { method: 'PUT', body: data, auth: true });
      },
      updateStatus(taskId, status) {
        return request(`/api/tasks/${encodeURIComponent(taskId)}/status`, {
          method: 'PATCH',
          body: { status },
          auth: true
        });
      },
      updatePriority(taskId, priority) {
        return request(`/api/tasks/${encodeURIComponent(taskId)}/priority`, {
          method: 'PATCH',
          body: { priority },
          auth: true
        });
      },
      remove(taskId) {
        return request(`/api/tasks/${encodeURIComponent(taskId)}`, { method: 'DELETE', auth: true });
      }
    },

    dashboard: {
      getAnalytics() {
        return request('/api/dashboard', { method: 'GET', auth: true });
      }
    },

    health: {
      checkDb() {
        return request('/api/health/db', { method: 'GET', auth: false });
      }
    }
  };
})();
