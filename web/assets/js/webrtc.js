/**
 * NEXUS REMOTE - WebRTC Screen Stream Viewer
 */
class StreamViewer {
  constructor(videoElementId, options = {}) {
    this.videoEl = document.getElementById(videoElementId);
    this.deviceId = options.deviceId || null;
    this.pc = null;
    this.active = false;
    this.statusCallback = options.onStatusChange || (() => {});
    this.configuration = {
      iceServers: [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' }
      ]
    };
    this._bindSignals();
  }

  _bindSignals() {
    socket.on('webrtc_offer', async (msg) => {
      if (msg.device_id === this.deviceId && this.active) {
        await this._handleOffer(msg.data);
      }
    });

    socket.on('webrtc_candidate', async (msg) => {
      if (msg.device_id === this.deviceId && this.pc && msg.data) {
        try {
          await this.pc.addIceCandidate(new RTCIceCandidate(msg.data));
        } catch (e) {
          console.warn('Error adding ICE candidate:', e);
        }
      }
    });

    socket.on('webrtc_stop', (msg) => {
      if (msg.device_id === this.deviceId) {
        this.stop();
      }
    });
  }

  async start(deviceId) {
    this.deviceId = deviceId;
    this.stop();
    this.active = true;
    this.statusCallback('connecting');

    try {
      this.pc = new RTCPeerConnection(this.configuration);

      this.pc.ontrack = (event) => {
        if (this.videoEl && event.streams && event.streams[0]) {
          this.videoEl.srcObject = event.streams[0];
          this.statusCallback('streaming');
        }
      };

      this.pc.onicecandidate = (event) => {
        if (event.candidate) {
          socket.send({
            type: 'webrtc_candidate',
            device_id: this.deviceId,
            data: event.candidate
          });
        }
      };

      this.pc.onconnectionstatechange = () => {
        const state = this.pc ? this.pc.connectionState : 'closed';
        if (state === 'connected') {
          this.statusCallback('streaming');
        } else if (state === 'disconnected' || state === 'failed') {
          this.statusCallback('failed');
        }
      };

      // Request screen stream from device via command / signaling
      socket.send({
        type: 'webrtc_offer',
        device_id: this.deviceId,
        data: { action: 'request_stream' }
      });

      return true;
    } catch (e) {
      console.error('Failed to initialize WebRTC:', e);
      this.statusCallback('failed');
      return false;
    }
  }

  async _handleOffer(offerData) {
    if (!this.pc) return;
    try {
      await this.pc.setRemoteDescription(new RTCSessionDescription(offerData));
      const answer = await this.pc.createAnswer();
      await this.pc.setLocalDescription(answer);

      socket.send({
        type: 'webrtc_answer',
        device_id: this.deviceId,
        data: answer
      });
    } catch (e) {
      console.error('Error handling WebRTC offer:', e);
      this.statusCallback('failed');
    }
  }

  stop() {
    this.active = false;
    if (this.pc) {
      try {
        this.pc.close();
      } catch (e) {}
      this.pc = null;
    }
    if (this.videoEl && this.videoEl.srcObject) {
      const tracks = this.videoEl.srcObject.getTracks();
      tracks.forEach(t => t.stop());
      this.videoEl.srcObject = null;
    }
    if (this.deviceId) {
      socket.send({
        type: 'webrtc_stop',
        device_id: this.deviceId
      });
    }
    this.statusCallback('stopped');
  }
}
