class NexusSocket {
  constructor() {
    this.ws = null;
    this.reconnectAttempts = 0;
    this.maxReconnectDelay = 15000;
    this.listeners = new Map();
    this.statusListeners = new Set();
    this.isConnected = false;
    this.isConnecting = false;
    this.reconnectTimer = null;
    this.hasConnectedBefore = false;
    this.connectionNoticeShown = false;

    window.addEventListener('online', () => {
      AppLogger.info('Network connection restored');
      this.connect();
    });
    window.addEventListener('offline', () => {
      this._notifyStatus('offline');
      AppLogger.warn('Browser reports no network connection');
    });
  }

  connect() {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) return;
    if (!API.getToken()) {
      this._notifyStatus('offline');
      return;
    }
    if (navigator.onLine === false) {
      this._notifyStatus('offline');
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
        if (this.reconnectTimer) window.clearTimeout(this.reconnectTimer);
        this.reconnectTimer = null;
        this._notifyStatus('online');
        AppLogger.info('Live updates connected');
        if (this.hasConnectedBefore && this.connectionNoticeShown) AppFeedback.success('Live updates are back.');
        this.hasConnectedBefore = true;
        this.connectionNoticeShown = false;
        this.send({ type: 'auth', token: API.getToken() });
      };

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (!data || typeof data.type !== 'string') throw new Error('Missing event type');
          this._dispatch(data.type, data);
        } catch {
          AppLogger.warn('A live update could not be read');
        }
      };

      this.ws.onclose = (event) => {
        this.isConnected = false;
        this.isConnecting = false;
        this._notifyStatus('offline');
        AppLogger.warn('Live update connection closed', { code: event.code, clean: event.wasClean });
        if (this.hasConnectedBefore && !this.connectionNoticeShown) {
          AppFeedback.warning('Live updates paused. The page will keep trying to reconnect.');
          this.connectionNoticeShown = true;
        }
        if (event.code !== 1000 && event.code !== 1008) this._scheduleReconnect();
        if (event.code === 1008) AppFeedback.error('The live connection was rejected. Please sign in again.');
      };

      this.ws.onerror = () => {
        AppLogger.warn('Live update connection encountered a network error');
      };
    } catch {
      this.isConnected = false;
      this.isConnecting = false;
      this._notifyStatus('offline');
      AppLogger.warn('Live update connection could not be started');
      this._scheduleReconnect();
    }
  }

  _scheduleReconnect() {
    if (this.reconnectTimer || !API.getToken() || navigator.onLine === false) return;
    this.reconnectAttempts += 1;
    const baseDelay = Math.min(750 * (2 ** Math.min(this.reconnectAttempts - 1, 6)), this.maxReconnectDelay);
    const delay = Math.round(baseDelay * (0.8 + Math.random() * 0.4));
    this._notifyStatus('reconnecting');
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      if (!this.isConnected) this.connect();
    }, delay);
  }

  disconnect() {
    if (this.reconnectTimer) window.clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
    this.isConnected = false;
    this.isConnecting = false;
    if (this.ws) {
      this.ws.onclose = null;
      this.ws.close(1000, 'Client disconnected');
      this.ws = null;
    }
    this._notifyStatus('offline');
  }

  send(data) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return false;
    try {
      this.ws.send(JSON.stringify(data));
      return true;
    } catch {
      AppLogger.warn('A live update could not be sent');
      return false;
    }
  }

  on(eventType, callback) {
    if (!this.listeners.has(eventType)) this.listeners.set(eventType, new Set());
    this.listeners.get(eventType).add(callback);
    return () => this.off(eventType, callback);
  }

  off(eventType, callback) {
    this.listeners.get(eventType)?.delete(callback);
  }

  onStatusChange(callback) {
    this.statusListeners.add(callback);
    try {
      callback(this.isConnected ? 'online' : (this.isConnecting ? 'connecting' : 'offline'));
    } catch {
      AppLogger.error('A connection status listener failed');
    }
    return () => this.statusListeners.delete(callback);
  }

  _notifyStatus(status) {
    this.statusListeners.forEach((callback) => {
      try {
        callback(status);
      } catch {
        AppLogger.error('A connection status listener failed');
      }
    });
  }

  _dispatch(eventType, data) {
    this.listeners.get(eventType)?.forEach((callback) => {
      try {
        callback(data);
      } catch {
        AppLogger.error('A live update handler failed', { eventType });
      }
    });
  }
}

const socket = new NexusSocket();
