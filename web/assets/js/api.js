const AppLogger = (() => {
  const key = 'nexus_ui_diagnostics';
  const maxEntries = 50;

  function write(level, message, context = {}) {
    const entry = {
      time: new Date().toISOString(),
      level,
      message: String(message).slice(0, 180),
      context
    };

    try {
      const entries = JSON.parse(sessionStorage.getItem(key) || '[]');
      entries.push(entry);
      sessionStorage.setItem(key, JSON.stringify(entries.slice(-maxEntries)));
    } catch {}

    const method = level === 'error' ? 'error' : level === 'warn' ? 'warn' : 'info';
    console[method](`[NEXUS ${level.toUpperCase()}] ${entry.message}`, context);
    return entry;
  }

  return {
    info: (message, context) => write('info', message, context),
    warn: (message, context) => write('warn', message, context),
    error: (message, context) => write('error', message, context),
    recent() {
      try {
        return JSON.parse(sessionStorage.getItem(key) || '[]');
      } catch {
        return [];
      }
    },
    clear() {
      sessionStorage.removeItem(key);
    }
  };
})();

const AppFeedback = (() => {
  let region;

  function getRegion() {
    if (region?.isConnected) return region;
    region = document.getElementById('app-toast-region');
    if (!region) {
      region = document.createElement('div');
      region.id = 'app-toast-region';
      region.className = 'app-toast-region';
      region.setAttribute('aria-live', 'polite');
      region.setAttribute('aria-relevant', 'additions');
      document.body.appendChild(region);
    }
    return region;
  }

  function show(message, type = 'info', duration = 5500) {
    const toast = document.createElement('div');
    toast.className = `app-toast ${type}`;
    toast.setAttribute('role', type === 'error' ? 'alert' : 'status');

    const text = document.createElement('span');
    text.className = 'app-toast-message';
    text.textContent = String(message || 'Something went wrong. Please try again.');

    const close = document.createElement('button');
    close.className = 'app-toast-close';
    close.type = 'button';
    close.setAttribute('aria-label', 'Dismiss notification');
    close.textContent = '×';
    close.addEventListener('click', () => toast.remove());

    toast.append(text, close);
    getRegion().appendChild(toast);
    if (duration > 0) window.setTimeout(() => toast.remove(), duration);
    return toast;
  }

  return {
    show,
    success: (message, duration) => show(message, 'success', duration),
    warning: (message, duration) => show(message, 'warning', duration),
    error: (message, duration) => show(message, 'error', duration)
  };
})();

class APIError extends Error {
  constructor(message, { status = 0, code = 'REQUEST_FAILED', retryable = false, cause } = {}) {
    super(message, { cause });
    this.name = 'APIError';
    this.status = status;
    this.code = code;
    this.retryable = retryable;
  }
}

const API = {
  timeoutMs: 15000,

  getBaseUrl() {
    return window.location.origin;
  },

  getToken() {
    return localStorage.getItem('nexus_token') || 'open_access_token';
  },

  setToken(token) {
    if (token) localStorage.setItem('nexus_token', token);
    else localStorage.removeItem('nexus_token');
  },

  getUser() {
    try {
      const user = localStorage.getItem('nexus_user');
      return user ? JSON.parse(user) : { id: 'usr_default_admin', email: 'admin@nexus.local', name: 'Operator', role: 'admin' };
    } catch {
      AppLogger.warn('Saved account details could not be read');
      return { id: 'usr_default_admin', email: 'admin@nexus.local', name: 'Operator', role: 'admin' };
    }
  },

  setUser(user) {
    if (user) localStorage.setItem('nexus_user', JSON.stringify(user));
    else localStorage.removeItem('nexus_user');
  },

  isAuthenticated() {
    return true;
  },

  logout() {
    AppLogger.info('Session reset requested');
    window.location.reload();
  },

  friendlyMessage(status, payload, endpoint = '') {
    const serverMessage = payload && (payload.message || payload.error);
    if (status === 400) return typeof serverMessage === 'string' && serverMessage.length < 240 ? serverMessage : 'Please check the information and try again.';
    if (status === 401 && endpoint.includes('/auth/login')) return 'The email address or password does not match.';
    if (status === 401) return 'Your session may have expired. Please sign in again.';
    if (status === 403) return 'You do not have permission to do that.';
    if (status === 404) return 'That item could not be found. Refresh the page and try again.';
    if (status === 408) return 'The request took too long. Please try again.';
    if (status === 409) return typeof serverMessage === 'string' && serverMessage.length < 240 ? serverMessage : 'This item has changed. Refresh the page and try again.';
    if (status === 413) return 'The selected file is too large.';
    if (status === 429) return 'Too many requests. Please wait a moment and try again.';
    if (status >= 500) return 'The service is having trouble right now. Please try again shortly.';
    return typeof serverMessage === 'string' && serverMessage.length < 240 ? serverMessage : `Request failed (${status}). Please try again.`;
  },

  async request(endpoint, options = {}) {
    const method = String(options.method || 'GET').toUpperCase();
    const headers = {
      Accept: 'application/json',
      ...(options.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {})
    };
    const token = this.getToken();
    if (token && !headers.Authorization) headers.Authorization = `Bearer ${token}`;

    const path = new URL(endpoint, this.getBaseUrl()).pathname;
    const mayRetry = method === 'GET' || method === 'HEAD';
    const maxAttempts = mayRetry ? 3 : 1;

    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      const controller = new AbortController();
      const timeoutId = window.setTimeout(() => controller.abort(), this.timeoutMs);

      try {
        const response = await fetch(`${this.getBaseUrl()}${endpoint}`, {
          ...options,
          method,
          headers,
          signal: options.signal || controller.signal
        });
        window.clearTimeout(timeoutId);

        const raw = response.status === 204 ? '' : await response.text();
        let payload = {};
        let isJson = false;
        if (raw) {
          try {
            payload = JSON.parse(raw);
            isJson = true;
          } catch {
            payload = {};
          }
        }

        if (!response.ok) {
          const error = new APIError(this.friendlyMessage(response.status, payload, endpoint), {
            status: response.status,
            code: payload.code || `HTTP_${response.status}`,
            retryable: [408, 429, 502, 503, 504].includes(response.status)
          });
          if (mayRetry && error.retryable && attempt < maxAttempts) {
            AppLogger.warn('Temporary service response; retrying request', { path, status: response.status, attempt });
            await new Promise(resolve => window.setTimeout(resolve, 350 * (2 ** (attempt - 1))));
            continue;
          }
          AppLogger[response.status >= 500 ? 'error' : 'warn']('API request failed', { path, status: response.status, code: error.code });
          throw error;
        }

        if (!raw) return {};
        if (isJson) return payload;
        const error = new APIError('The service returned an unreadable response. Please try again.', { code: 'INVALID_RESPONSE' });
        AppLogger.error('API response could not be parsed', { path, status: response.status });
        throw error;
      } catch (cause) {
        window.clearTimeout(timeoutId);
        if (cause instanceof APIError) throw cause;

        const timedOut = cause?.name === 'AbortError';
        const error = new APIError(
          timedOut ? 'The request timed out. Check your connection and try again.' : navigator.onLine === false ? 'You appear to be offline. Check your internet connection and try again.' : 'Could not reach the service. Check your connection and try again.',
          { code: timedOut ? 'TIMEOUT' : 'NETWORK_ERROR', retryable: true, cause }
        );
        if (mayRetry && attempt < maxAttempts) {
          AppLogger.warn('Network request interrupted; retrying', { path, attempt, code: error.code });
          await new Promise(resolve => window.setTimeout(resolve, 350 * (2 ** (attempt - 1))));
          continue;
        }
        AppLogger.error('API request could not complete', { path, code: error.code });
        throw error;
      }
    }

    throw new APIError('Could not complete the request. Please try again.', { code: 'RETRIES_EXHAUSTED' });
  },

  get(endpoint) {
    return this.request(endpoint, { method: 'GET' });
  },

  post(endpoint, body) {
    return this.request(endpoint, { method: 'POST', body: JSON.stringify(body) });
  },

  patch(endpoint, body) {
    return this.request(endpoint, { method: 'PATCH', body: JSON.stringify(body) });
  },

  delete(endpoint) {
    return this.request(endpoint, { method: 'DELETE' });
  }
};

window.addEventListener('error', (event) => {
  AppLogger.error('Unexpected page error', { type: event.error?.name || 'ScriptError', line: event.lineno || 0 });
  AppFeedback.error('Something went wrong on this page. Refresh and try again.');
});

window.addEventListener('unhandledrejection', (event) => {
  const name = event.reason?.name || 'UnknownError';
  AppLogger.error('Unexpected background task failure', { type: name });
  AppFeedback.error('A background action did not finish. Please try again.');
});
