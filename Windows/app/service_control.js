'use strict';
// ── STRIPARCO service installer/remover ─────────────────────────────────────
// Thin wrapper around node-windows. Kept separate so the GUI process can
// install/start/remove the LocalSystem guard service without pulling Electron
// into the service runtime. node-windows shells out to a bundled SCM wrapper
// (winsw), so no native module is needed.

const path = require('path');
const P = require('./protection');

const SERVICE_SCRIPT = path.join(__dirname, 'service.js');

function makeService() {
  // Required lazily so a missing dev dependency never crashes the GUI.
  const { Service } = require('node-windows');
  return new Service({
    name: P.SERVICE_NAME,
    description: 'STRIPARCOP content-filter & screen-time guard (tamper protection).',
    script: SERVICE_SCRIPT,
    execPath: process.execPath,              // electron.exe, run as Node below
    env: [{ name: 'ELECTRON_RUN_AS_NODE', value: '1' }],
    wait: 1, grow: 0.5, maxRestarts: 60,     // aggressive auto-restart on failure
  });
}

// Install + start the guard service if it is not already present. Best-effort:
// any failure leaves the per-minute guard task + in-app self-heal as backup.
function ensureServiceInstalled() {
  try {
    const svc = makeService();
    if (svc.exists) { try { svc.start(); } catch (e) {} return; }
    svc.on('install', () => { try { svc.start(); } catch (e) {} });
    svc.on('alreadyinstalled', () => { try { svc.start(); } catch (e) {} });
    svc.install();
  } catch (e) { /* node-windows missing or no admin — ignore */ }
}

function removeService() {
  try {
    const svc = makeService();
    if (!svc.exists) return;
    svc.on('uninstall', () => {});
    svc.uninstall();
  } catch (e) {}
}

module.exports = { ensureServiceInstalled, removeService };
