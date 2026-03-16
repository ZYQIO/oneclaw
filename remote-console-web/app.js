const statusEl = document.querySelector('#status');
const logEl = document.querySelector('#log');
const deviceListEl = document.querySelector('#device-list');
const wsUrlEl = document.querySelector('#ws-url');
const pairCodeEl = document.querySelector('#pair-code');
const inputTextEl = document.querySelector('#input-text');
const remoteUploadPathEl = document.querySelector('#remote-upload-path');
const remoteDownloadPathEl = document.querySelector('#remote-download-path');
const uploadFileEl = document.querySelector('#upload-file');
const snapshotImageEl = document.querySelector('#snapshot-image');
const fileListEl = document.querySelector('#file-list');

const controllerId = localStorage.getItem('oneclaw-controller-id') || `web-${crypto.randomUUID()}`;
localStorage.setItem('oneclaw-controller-id', controllerId);

wsUrlEl.value = localStorage.getItem('oneclaw-ws-url') || `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;

let socket;
let selectedDeviceId = null;
let devices = [];
const sessions = new Map();
let snapshotInterval = null;

function appendLog(line) {
  logEl.textContent = `${new Date().toLocaleTimeString()} ${line}\n${logEl.textContent}`.trim();
}

function setStatus(text) {
  statusEl.textContent = text;
}

function requestId() {
  return crypto.randomUUID();
}

function send(message) {
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    appendLog('Socket is not connected.');
    return;
  }
  socket.send(JSON.stringify(message));
}

function selectedSession() {
  return selectedDeviceId ? sessions.get(selectedDeviceId) : null;
}

function refreshDevices() {
  send({
    type: 'device.list.request',
    requestId: requestId(),
    senderId: controllerId,
    payload: {}
  });
}

function requestSnapshot() {
  const session = selectedSession();
  if (!session) {
    appendLog('Open a session before requesting snapshots.');
    return;
  }
  send({
    type: 'session.snapshot.request',
    requestId: requestId(),
    senderId: controllerId,
    deviceId: selectedDeviceId,
    sessionId: session.sessionId,
    payload: {}
  });
}

function listFiles() {
  const session = selectedSession();
  if (!session) {
    appendLog('Open a session before listing files.');
    return;
  }
  send({
    type: 'session.file.meta',
    requestId: requestId(),
    senderId: controllerId,
    deviceId: selectedDeviceId,
    sessionId: session.sessionId,
    payload: {
      command: {
        action: 'LIST',
        path: remoteDownloadPathEl.value.trim() || '.'
      }
    }
  });
}

function startSnapshotLoop() {
  clearInterval(snapshotInterval);
  snapshotInterval = setInterval(() => {
    if (selectedSession()) {
      requestSnapshot();
    }
  }, 1500);
}

function renderDevices() {
  deviceListEl.innerHTML = '';
  devices.forEach((device) => {
    const card = document.createElement('article');
    card.className = `device-card ${selectedDeviceId === device.deviceId ? 'selected' : ''}`;
    card.innerHTML = `
      <h3>${device.name}</h3>
      <p>${device.deviceId}</p>
      <p>mode=${device.mode.toLowerCase()} • ${device.screenWidth}x${device.screenHeight}</p>
      <p>video=${device.capabilities.video} touch=${device.capabilities.touch} file=${device.capabilities.fileTransfer}</p>
      <div class="button-row">
        <button data-action="select">Select</button>
        <button data-action="pair">Pair</button>
        <button data-action="open">Open</button>
        <button data-action="close">Close</button>
      </div>
    `;
    card.querySelector('[data-action="select"]').addEventListener('click', () => {
      selectedDeviceId = device.deviceId;
      renderDevices();
      requestSnapshot();
    });
    card.querySelector('[data-action="pair"]').addEventListener('click', () => {
      send({
        type: 'pair.request',
        requestId: requestId(),
        senderId: controllerId,
        targetId: device.deviceId,
        deviceId: device.deviceId,
        payload: { pairCode: pairCodeEl.value.trim() }
      });
    });
    card.querySelector('[data-action="open"]').addEventListener('click', () => {
      send({
        type: 'session.open',
        requestId: requestId(),
        senderId: controllerId,
        targetId: device.deviceId,
        deviceId: device.deviceId,
        payload: { source: 'manual' }
      });
    });
    card.querySelector('[data-action="close"]').addEventListener('click', () => {
      const session = sessions.get(device.deviceId);
      if (!session) {
        appendLog('No session to close.');
        return;
      }
      send({
        type: 'session.close',
        requestId: requestId(),
        senderId: controllerId,
        targetId: device.deviceId,
        deviceId: device.deviceId,
        sessionId: session.sessionId,
        payload: {}
      });
    });
    deviceListEl.append(card);
  });
}

function connect() {
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    appendLog('Socket is already connected or connecting.');
    return;
  }
  const wsUrl = wsUrlEl.value.trim();
  localStorage.setItem('oneclaw-ws-url', wsUrl);
  socket = new WebSocket(wsUrl);
  setStatus(`Connecting to ${wsUrl}`);

  socket.addEventListener('open', () => {
    setStatus(`Connected as ${controllerId}`);
    send({
      type: 'controller.register',
      senderId: controllerId,
      payload: {}
    });
    refreshDevices();
  });

  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data);
    appendLog(`${message.type}: ${JSON.stringify(message.payload || {})}`);

    switch (message.type) {
      case 'device.list.response':
        devices = message.payload.devices || [];
        renderDevices();
        break;
      case 'pair.confirm':
        setStatus(`Paired with ${message.deviceId}`);
        break;
      case 'session.opened':
        sessions.set(message.deviceId, message.payload.session);
        selectedDeviceId = message.deviceId;
        setStatus(`Session opened for ${message.deviceId}`);
        renderDevices();
        requestSnapshot();
        startSnapshotLoop();
        break;
      case 'session.closed':
        sessions.delete(message.deviceId);
        if (selectedDeviceId === message.deviceId) {
          fileListEl.textContent = 'Remote files will appear here.';
        }
        renderDevices();
        break;
      case 'session.snapshot.response':
        if (message.payload.snapshot?.base64Data) {
          snapshotImageEl.src = `data:image/png;base64,${message.payload.snapshot.base64Data}`;
        }
        break;
      case 'session.file.response':
        if (message.payload.entries) {
          const lines = message.payload.entries.map((entry) => {
            const kind = entry.isDirectory ? '[DIR]' : '[FILE]';
            return `${kind} ${entry.path} (${entry.sizeBytes} bytes)`;
          });
          fileListEl.textContent = lines.join('\n') || 'No files.';
        }
        if (message.payload.base64Data) {
          const bytes = Uint8Array.from(atob(message.payload.base64Data), (char) => char.charCodeAt(0));
          const blob = new Blob([bytes], { type: 'application/octet-stream' });
          const anchor = document.createElement('a');
          anchor.href = URL.createObjectURL(blob);
          anchor.download = (message.payload.path || 'remote-file').split('/').pop();
          anchor.click();
          URL.revokeObjectURL(anchor.href);
        }
        break;
      case 'error':
        setStatus(message.payload.message || 'Remote error');
        break;
      default:
        break;
    }
  });

  socket.addEventListener('close', () => {
    setStatus('Disconnected');
    clearInterval(snapshotInterval);
  });
}

document.querySelector('#connect-btn').addEventListener('click', connect);
document.querySelector('#refresh-btn').addEventListener('click', refreshDevices);
document.querySelector('#snapshot-btn').addEventListener('click', requestSnapshot);
document.querySelector('#list-files-btn').addEventListener('click', listFiles);

document.querySelector('#home-btn').addEventListener('click', () => {
  const session = selectedSession();
  if (!session) return;
  send({
    type: 'session.control',
    requestId: requestId(),
    senderId: controllerId,
    deviceId: selectedDeviceId,
    sessionId: session.sessionId,
    payload: { source: 'manual', command: { action: 'HOME' } }
  });
});

document.querySelector('#back-btn').addEventListener('click', () => {
  const session = selectedSession();
  if (!session) return;
  send({
    type: 'session.control',
    requestId: requestId(),
    senderId: controllerId,
    deviceId: selectedDeviceId,
    sessionId: session.sessionId,
    payload: { source: 'manual', command: { action: 'BACK' } }
  });
});

document.querySelector('#send-text-btn').addEventListener('click', () => {
  const session = selectedSession();
  if (!session) return;
  send({
    type: 'session.control',
    requestId: requestId(),
    senderId: controllerId,
    deviceId: selectedDeviceId,
    sessionId: session.sessionId,
    payload: {
      source: 'manual',
      command: { action: 'TEXT', text: inputTextEl.value }
    }
  });
});

document.querySelector('#upload-btn').addEventListener('click', async () => {
  const session = selectedSession();
  const file = uploadFileEl.files?.[0];
  if (!session || !file) {
    appendLog('Select a file and open a session before uploading.');
    return;
  }
  const arrayBuffer = await file.arrayBuffer();
  const base64Data = btoa(String.fromCharCode(...new Uint8Array(arrayBuffer)));
  send({
    type: 'session.file.meta',
    requestId: requestId(),
    senderId: controllerId,
    deviceId: selectedDeviceId,
    sessionId: session.sessionId,
    payload: {
      command: {
        action: 'UPLOAD',
        targetPath: remoteUploadPathEl.value.trim(),
        base64Data
      }
    }
  });
});

document.querySelector('#download-btn').addEventListener('click', () => {
  const session = selectedSession();
  if (!session) {
    appendLog('Open a session before downloading.');
    return;
  }
  send({
    type: 'session.file.meta',
    requestId: requestId(),
    senderId: controllerId,
    deviceId: selectedDeviceId,
    sessionId: session.sessionId,
    payload: {
      command: {
        action: 'DOWNLOAD',
        path: remoteDownloadPathEl.value.trim()
      }
    }
  });
});

snapshotImageEl.addEventListener('click', (event) => {
  const session = selectedSession();
  const device = devices.find((item) => item.deviceId === selectedDeviceId);
  if (!session || !device) {
    return;
  }
  const bounds = snapshotImageEl.getBoundingClientRect();
  const relativeX = (event.clientX - bounds.left) / bounds.width;
  const relativeY = (event.clientY - bounds.top) / bounds.height;
  const x = Math.round(relativeX * device.screenWidth);
  const y = Math.round(relativeY * device.screenHeight);
  send({
    type: 'session.control',
    requestId: requestId(),
    senderId: controllerId,
    deviceId: selectedDeviceId,
    sessionId: session.sessionId,
    payload: {
      source: 'manual',
      command: { action: 'TAP', x, y }
    }
  });
});
