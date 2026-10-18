// A private hub for the tests: its own HOME (so its own ~/.chums config and tokens), its own ports, no
// speech-to-text, so tests never reach the real hub or a real Claude Code session in its routing table.
// import { startPrivateHub } from './test-hub-private.mjs'; const { cfg, stop } = await startPrivateHub()
// Options: agents (its ~/.chums/agents.json; else the hub writes its defaults), env (more environment for the hub and
// the agents it runs), config (more keys for its config.json), logFile (where the hub's log goes), home (a previous
// private hub's, to start a hub again on it), and CHUMS_TEST_LOG=1 to see the hub's log. It returns hub (the process)
// and stop({ keepHome }).
// Its bin/chums-agents is a stand-in that only writes what it was asked to <home>/chums-agents.log: a test that
// restarts a Claude Code agent never touches a real LaunchAgent.
import { spawn } from 'node:child_process'
import fs from 'node:fs'; import os from 'node:os'; import path from 'node:path'

export async function startPrivateHub({ agents, env = {}, logFile, home: again, config = {} } = {}) {
  const home = again ?? fs.mkdtempSync(path.join(os.tmpdir(), 'chums-test-'))
  let cfg
  if (again) cfg = JSON.parse(fs.readFileSync(path.join(home, '.chums/config.json'), 'utf8'))
  else {
    fs.mkdirSync(path.join(home, '.chums'))
    cfg = { ...config, port: 17717 + Math.floor(Math.random() * 500) * 2, token: 'test-local-' + Date.now(), watchToken: 'test-watch-' + Date.now() }
    cfg.publicPort = cfg.port + 1
    fs.writeFileSync(path.join(home, '.chums/config.json'), JSON.stringify(cfg))
  }
  if (agents) fs.writeFileSync(path.join(home, '.chums/agents.json'), JSON.stringify(agents, null, 2))
  const agentsBin = path.join(home, 'chums-agents')
  fs.writeFileSync(agentsBin, '#!/bin/sh\necho "$*" >> "$0.log"\n', { mode: 0o755 })
  let hub
  // a port another test's hub already has (tests run side by side) stops this hub at once: it starts again on others
  for (let attempt = 1; ; attempt++) {
    hub = spawn('node', [path.join(import.meta.dirname, '../hub/hub.mjs')], {
      env: { ...process.env, CHUMS_AGENTS_BIN: agentsBin, ...env, HOME: home, CHUMS_NO_WHISPER: '1' }, stdio: logFile ? ['ignore', fs.openSync(logFile, 'a'), fs.openSync(logFile, 'a')] : process.env.CHUMS_TEST_LOG ? 'inherit' : 'ignore',
    })
    let exited = false
    hub.once('exit', () => { exited = true })
    for (let i = 0; i < 50 && !exited; i++) {
      try { if ((await fetch(`http://127.0.0.1:${cfg.port}/v1/ping`, { headers: { authorization: `Bearer ${cfg.token}` } })).ok) break } catch {}
      await new Promise(r => setTimeout(r, 100))
    }
    if (!exited || again || attempt === 5) break
    cfg.port = 17717 + Math.floor(Math.random() * 500) * 2; cfg.publicPort = cfg.port + 1
    fs.writeFileSync(path.join(home, '.chums/config.json'), JSON.stringify(cfg))
  }
  process.on('exit', () => hub.kill())          // a test that dies doesn't leave its hub (and the agents it runs) behind
  // (the hub may still be writing as it stops, e.g. its souls' first commit: the removal tries again)
  return { cfg, home, hub, stop: ({ keepHome } = {}) => { hub.kill(); if (!keepHome) fs.rmSync(home, { recursive: true, force: true, maxRetries: 10, retryDelay: 100 }) } }
}
