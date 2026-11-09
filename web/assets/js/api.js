/**
 * NEXUS REMOTE - Frontend REST API Client
 */
const API = {
  getBaseUrl() {
    return window.location.origin;
  },

  getToken() {
    return localStorage.getItem('nexus_token') || 'open_access_token';
  },

  setToken(token) {
    if (token) {
      localStorage.setItem('nexus_token', token);
    } else {
      localStorage.removeItem('nexus_token');
    }
  },

  getUser() {
    try {
      const u = localStorage.getItem('nexus_user');
      return u ? JSON.parse(u) : { id: 'usr_default_admin', email: 'admin@nexus.local', name: 'Operator', role: 'admin' };
    } catch {
      return { id: 'usr_default_admin', email: 'admin@nexus.local', name: 'Operator', role: 'admin' };
    }
  },

  setUser(user) {
    if (user) {
      localStorage.setItem('nexus_user', JSON.stringify(user));
    } else {
      localStorage.removeItem('nexus_user');
    }
  },

  isAuthenticated() {
    return true;
  },

  logout() {
    console.log('Session reset (Open Access)');
    window.location.reload();
  },

  async request(endpoint, options = {}) {
    const url = `${this.getBaseUrl()}${endpoint}`;
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    };

    const token = this.getToken();
    if (token && !headers['Authorization']) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const config = {
      ...options,
      headers
    };

    try {
      const res = await fetch(url, config);
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.error || `HTTP error ${res.status}`);
      }
      return data;
    } catch (err) {
      throw err;
    }
  },

  get(endpoint) {
    return this.request(endpoint, { method: 'GET' });
  },

  post(endpoint, body) {
    return this.request(endpoint, {
      method: 'POST',
      body: JSON.stringify(body)
    });
  },

  patch(endpoint, body) {
    return this.request(endpoint, {
      method: 'PATCH',
      body: JSON.stringify(body)
    });
  },

  delete(endpoint) {
    return this.request(endpoint, { method: 'DELETE' });
  }
};
