import { createReadStream, existsSync } from 'node:fs';
import { stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';
import { WebSocketServer } from 'ws';
import crypto from 'node:crypto';

const PORT = Number(process.env.PORT || 8080);
const __dirname = fileURLToPath(new URL('.', import.meta.url));
const consoleRoot = normalize(join(__dirname, '..', 'remote-console-web'));

const socketMeta = new Map();
const deviceConnections = new Map();
const controllerConnections = new Map();
const pairings = new Map();
const sessions = new Map();

const mimeTypes = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8'
};

function send(ws, message) {
  if (ws && ws.readyState === 1) {
    ws.send(JSON.stringify(message));
  }
}

function setControllerPairing(controllerId, deviceId) {
  const set = pairings.get(controllerId) ?? new Set();
  set.add(deviceId);
  pairings.set(controllerId, set);
}

function isPaired(controllerId, deviceId) {
  return pairings.get(controllerId)?.has(deviceId) ?? false;
}

function routeToController(controllerId, message) {
  const socket = controllerConnections.get(controllerId);
  send(socket, message);
}

function routeToDevice(deviceId, message) {
  const connection = deviceConnections.get(deviceId);
  send(connection?.ws, message);
}

function wsError(ws, baseMessage, errorMessage) {
  send(ws, {
    type: 'error',
    requestId: baseMessage.requestId ?? null,
    senderId: 'broker',
    targetId: baseMessage.senderId ?? null,
    deviceId: baseMessage.deviceId ?? null,
    sessionId: baseMessage.sessionId ?? null,
    payload: { message: errorMessage }
  });
}

function deviceList() {
  return Array.from(deviceConnections.values()).map(({ device, lastSeen, ws }) => ({
    ...device,
    lastSeen,
    online: ws.readyState === 1
  }));
}

function controllerIdFor(ws, message) {
  const meta = socketMeta.get(ws);
  return meta?.id || message.senderId;
}

function serializeState() {
  return {
    devices: deviceList(),
    controllers: Array.from(controllerConnections.keys()),
    pairings: Array.from(pairings.entries()).map(([controllerId, deviceIds]) => ({
      controllerId,
      deviceIds: Array.from(deviceIds)
    })),
    sessions: Array.from(sessions.values())
  };
}

function removeDeviceSessions(deviceId, reason = 'device_disconnected') {
  for (const [sessionId, session] of sessions.entries()) {
    if (session.deviceId === deviceId) {
      sessions.delete(sessionId);
      routeToController(session.controllerId, {
        type: 'session.closed',
        senderId: 'broker',
        deviceId: session.deviceId,
        sessionId,
        payload: { closed: true, reason }
      });
    }
  }
}

async function serveStatic(req, res) {
  if (req.url === '/healthz') {
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ ok: true, devices: deviceConnections.size, sessions: sessions.size }));
    return;
  }
  if (req.url === '/api/state') {
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(serializeState(), null, 2));
    return;
  }
  const urlPath = req.url === '/' ? '/index.html' : req.url;
  const requestedPath = normalize(join(consoleRoot, urlPath));
  if (!requestedPath.startsWith(consoleRoot) || !existsSync(requestedPath)) {
    res.writeHead(404);
    res.end('Not found');
    return;
  }
  const fileStat = await stat(requestedPath);
  if (!fileStat.isFile()) {
    res.writeHead(404);
    res.end('Not found');
    return;
  }
  res.writeHead(200, {
    'Content-Type': mimeTypes[extname(requestedPath)] || 'application/octet-stream'
  });
  createReadStream(requestedPath).pipe(res);
}

const server = createServer((req, res) => {
  serveStatic(req, res).catch((error) => {
    res.writeHead(500);
    res.end(error.message);
  });
});

const wss = new WebSocketServer({ noServer: true });

wss.on('connection', (ws) => {
  socketMeta.set(ws, { role: 'unknown', id: null });

  ws.on('message', (buffer) => {
    let message;
    try {
      message = JSON.parse(buffer.toString());
    } catch (error) {
      wsError(ws, {}, `Invalid JSON: ${error.message}`);
      return;
    }

    switch (message.type) {
      case 'controller.register': {
        const controllerId = message.senderId || `controller-${crypto.randomUUID()}`;
        socketMeta.set(ws, { role: 'controller', id: controllerId });
        controllerConnections.set(controllerId, ws);
        send(ws, {
          type: 'controller.registered',
          senderId: 'broker',
          targetId: controllerId,
          payload: { controllerId }
        });
        break;
      }

      case 'device.register': {
        const device = message.payload?.device;
        if (!device?.deviceId) {
          wsError(ws, message, 'device payload is required');
          return;
        }
        socketMeta.set(ws, { role: 'device', id: device.deviceId });
        deviceConnections.set(device.deviceId, {
          ws,
          device,
          pairCode: message.payload?.pairCode ?? '',
          lastSeen: Date.now()
        });
        send(ws, {
          type: 'device.registered',
          requestId: message.requestId ?? null,
          senderId: 'broker',
          targetId: device.deviceId,
          deviceId: device.deviceId,
          payload: { accepted: true }
        });
        break;
      }

      case 'device.heartbeat': {
        const meta = socketMeta.get(ws);
        if (meta?.role !== 'device') {
          wsError(ws, message, 'heartbeat only allowed for devices');
          return;
        }
        const existing = deviceConnections.get(meta.id);
        if (existing) {
          existing.lastSeen = Date.now();
          if (message.payload?.allowAgentControl !== undefined) {
            existing.device.capabilities.agentControl = Boolean(message.payload.allowAgentControl);
          }
          deviceConnections.set(meta.id, existing);
        }
        break;
      }

      case 'device.list.request': {
        send(ws, {
          type: 'device.list.response',
          requestId: message.requestId ?? null,
          senderId: 'broker',
          targetId: controllerIdFor(ws, message),
          payload: { devices: deviceList() }
        });
        break;
      }

      case 'pair.request': {
        const controllerId = controllerIdFor(ws, message);
        const deviceId = message.deviceId;
        const connection = deviceConnections.get(deviceId);
        if (!controllerId || !connection) {
          wsError(ws, message, 'Device is offline or controller is not registered');
          return;
        }
        if ((message.payload?.pairCode ?? '') !== connection.pairCode) {
          wsError(ws, message, 'Pair code does not match');
          return;
        }
        setControllerPairing(controllerId, deviceId);
        const confirmation = {
          type: 'pair.confirm',
          requestId: message.requestId ?? null,
          senderId: 'broker',
          targetId: controllerId,
          deviceId,
          payload: { paired: true }
        };
        send(ws, confirmation);
        routeToDevice(deviceId, {
          ...confirmation,
          targetId: deviceId
        });
        break;
      }

      case 'session.open': {
        const controllerId = controllerIdFor(ws, message);
        const deviceId = message.deviceId;
        const connection = deviceConnections.get(deviceId);
        if (!controllerId || !connection) {
          wsError(ws, message, 'Device is offline');
          return;
        }
        if (!isPaired(controllerId, deviceId)) {
          wsError(ws, message, 'Pair the device before opening a session');
          return;
        }
        const session = {
          sessionId: crypto.randomUUID(),
          deviceId,
          controllerId,
          mode: connection.device.mode,
          startedAt: Date.now(),
          leaseExpiresAt: Date.now() + 30 * 60 * 1000
        };
        sessions.set(session.sessionId, session);
        send(ws, {
          type: 'session.opened',
          requestId: message.requestId ?? null,
          senderId: 'broker',
          targetId: controllerId,
          deviceId,
          sessionId: session.sessionId,
          payload: { session }
        });
        routeToDevice(deviceId, {
          type: 'session.open',
          requestId: message.requestId ?? null,
          senderId: controllerId,
          targetId: deviceId,
          deviceId,
          sessionId: session.sessionId,
          payload: { session, source: message.payload?.source ?? 'manual' }
        });
        break;
      }

      case 'session.close': {
        const controllerId = controllerIdFor(ws, message);
        const session = sessions.get(message.sessionId);
        if (!session || session.controllerId !== controllerId) {
          wsError(ws, message, 'Session not found or not owned by this controller');
          return;
        }
        sessions.delete(message.sessionId);
        send(ws, {
          type: 'session.closed',
          requestId: message.requestId ?? null,
          senderId: 'broker',
          targetId: controllerId,
          deviceId: session.deviceId,
          sessionId: session.sessionId,
          payload: { closed: true }
        });
        routeToDevice(session.deviceId, {
          type: 'session.close',
          requestId: message.requestId ?? null,
          senderId: controllerId,
          targetId: session.deviceId,
          deviceId: session.deviceId,
          sessionId: session.sessionId,
          payload: {}
        });
        break;
      }

      case 'session.control':
      case 'session.snapshot.request':
      case 'session.file.meta':
      case 'session.file.chunk': {
        const controllerId = controllerIdFor(ws, message);
        const session = sessions.get(message.sessionId);
        if (!session || session.controllerId !== controllerId) {
          wsError(ws, message, 'Session not found or not owned by this controller');
          return;
        }
        routeToDevice(session.deviceId, {
          ...message,
          senderId: controllerId,
          targetId: session.deviceId,
          deviceId: session.deviceId
        });
        break;
      }

      case 'session.control.ack':
      case 'session.snapshot.response':
      case 'session.file.response':
      case 'error': {
        const session = message.sessionId ? sessions.get(message.sessionId) : null;
        const targetId = message.targetId || session?.controllerId;
        if (!targetId) {
          return;
        }
        routeToController(targetId, {
          ...message,
          senderId: message.senderId || 'broker',
          targetId
        });
        break;
      }

      default:
        wsError(ws, message, `Unsupported message type: ${message.type}`);
    }
  });

  ws.on('close', () => {
    const meta = socketMeta.get(ws);
    if (meta?.role === 'device') {
      deviceConnections.delete(meta.id);
      removeDeviceSessions(meta.id, 'device_disconnected');
    }
    if (meta?.role === 'controller') {
      controllerConnections.delete(meta.id);
      pairings.delete(meta.id);
      for (const [sessionId, session] of sessions.entries()) {
        if (session.controllerId === meta.id) {
          sessions.delete(sessionId);
          routeToDevice(session.deviceId, {
            type: 'session.close',
            senderId: 'broker',
            targetId: session.deviceId,
            deviceId: session.deviceId,
            sessionId,
            payload: { reason: 'controller_disconnected' }
          });
        }
      }
    }
    socketMeta.delete(ws);
  });
});

server.on('upgrade', (request, socket, head) => {
  if (request.url !== '/ws') {
    socket.destroy();
    return;
  }
  wss.handleUpgrade(request, socket, head, (ws) => {
    wss.emit('connection', ws, request);
  });
});

server.listen(PORT, () => {
  console.log(`Remote broker listening on http://0.0.0.0:${PORT}`);
});
