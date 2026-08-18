'use strict';
// ── PS-BLOCK guard service (runs as LocalSystem) ────────────────────────────
// Launched by the Windows Service Control Manager via electron.exe started with
// ELECTRON_RUN_AS_NODE=1 (see service_control.js). As SYSTEM it re-applies the
// tamper protection every cycle, so removing the guard task, un-hiding the
// uninstall entry, or clearing the ACL is silently repaired within ~20 s.
//
// This is the closest practical equivalent to a Vanguard-style always-on guard
// without shipping a (EV + attestation-signed) kernel driver.

const P = require('./protection');

let busy = false;
async function tick() {
  if (busy) return;
  busy = true;
  try { await P.applyProtection(); } catch (e) { /* best-effort */ }
  busy = false;
}

tick();
setInterval(tick, 20000);

// Keep the process alive for the SCM wrapper; nothing else to do.
process.stdin.resume?.();
