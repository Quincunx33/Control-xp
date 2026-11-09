/**
 * NEXUS REMOTE - Real-Time WebSocket Client with Exponential Backoff
 */
class NexusSocket {
  constructor() {
    this.ws = null;
    this.reconnectAttempts = 0;
    this.maxReconnectDelay = 15000;
    this.listeners = new Map();
    this.statusListeners = new Set();
    this.isConnected = false;
    this.isConnecting = false;
  }

  connect() {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    const token = API.getToken();
    if (!token) {
      return;
    }

    this.isConnecting = true;
    this._notifyStatus('connecting');

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws`;

    try {
      this.ws = new WebSocket(wsUrl);

      this.ws.onopen = () => {
        this.isConnected = true;
        this.isConnecting = false;
        this.reconnectAttempts = 0;
        this._notifyStatus('online');

        // Authenticate with session token
        this.send({
          type: 'auth',
          token: token
        });
      };

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this._dispatch(data.type, data);
        } catch (e) {
          console.error('Invalid WS frame:', e);
        }
      };

      this.ws.onclose = () => {
        this.isConnected = false;
        this.isConnecting = false;
        this._notifyStatus('offline');
        this._scheduleReconnect();
      };

      this.ws.onerror = (err) => {
        console.warn('WebSocket connection error:', err);
      };
    } catch (e) {
      this.isConnected = false;
      this.isConnecting = false;
      this._notifyStatus('offline');
      this._scheduleReconnect();
    }
  }

  _scheduleReconnect() {
    this.reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(1.5, this.reconnectAttempts), this.maxReconnectDelay);
    setTimeout(() => {
      if (!this.isConnected && API.getToken()) {
        this.connect();
      }
    }, delay);
  }

  send(data) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data));
      return true;
    }
    return false;
  }

  on(eventType, callback) {
    if (!this.listeners.has(eventType)) {
      this.listeners.set(eventType, new Set());
    }
    this.listeners.get(eventType).add(callback);
    return () => this.off(eventType, callback);
  }

  off(eventType, callback) {
    if (this.listeners.has(eventType)) {
      this.listeners.get(eventType).delete(callback);
    }
  }

  onStatusChange(callback) {
    this.statusListeners.add(callback);
    callback(this.isConnected ? 'online' : (this.isConnecting ? 'connecting' : 'offline'));
    return () => this.statusListeners.delete(callback);
  }

  _notifyStatus(status) {
    this.statusListeners.forEach(cb => cb(status));
  }

  _dispatch(eventType, data) {
    if (this.listeners.has(eventType)) {
      this.listeners.get(eventType).forEach(cb => {
        try {
          cb(data);
        } catch (e) {
          console.error(`Error in event listener for ${eventType}:`, e);
        }
      });
    }
  }
}

const socket = new NexusSocket();
