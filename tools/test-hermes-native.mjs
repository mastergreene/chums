// Native Hermes uses ACP. Exercise its transport without real credentials or model calls.
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { startPrivateHub } from './test-hub-private.mjs'
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'chums-native-'))
const log = path.join(work, 'acp.log')
const command = [process.execPath, path.join(import.meta.dirname, 'fake-acp-agent.mjs')]
const agents = ['garfield', 'patch'].map(id => ({ id, name: id, runtime: 'hermes-agent', command, cwd: path.join(work, id), hermesHome: path.join(work, id + '-profile') }))
let hub
try {
  hub = await startPrivateHub({ agents, env: { FAKE_LOG: log } })
  const api = async (route, body) => {
    const r = await fetch(`http://127.0.0.1:${hub.cfg.port}${route}`, { method: body ? 'POST' : 'GET', headers: { authorization: `Bearer ${hub.cfg.watchToken}`, 'content-type': 'application/json' }, body: body ? JSON.stringify(body) : undefined })
    assert.equal(r.ok, true, route)
    return r.json()
  }
  const state = () => api('/v1/state?since=0')
  const until = async f => {
    for (let i = 0; i < 120; i++) { const value = await f(); if (value) return value; await new Promise(r => setTimeout(r, 100)) }
    throw new Error('Native Hermes condition timed out')
  }
  await until(async () => (await state()).agents.filter(a => a.status === 'idle').length === 2)
  const saved = JSON.parse(fs.readFileSync(path.join(hub.home, '.chums/agents.json')))
  assert(saved.every(a => a.sessionId), 'native session ids are persisted')
  assert.notEqual(saved[0].sessionId, saved[1].sessionId, 'agents have distinct sessions')
  await api('/v1/say', { text: 'approve this', agent: 'garfield', device: 'phone' })
  const card = await until(async () => (await state()).approvals.find(a => a.session === 'garfield'))
  assert.match(card.preview, /rm -rf build/)
  assert(!(await state()).approvals.some(a => a.session === 'patch'))
  await api('/v1/verdict', { requestId: card.requestId, behavior: 'deny' })
  await until(async () => (await state()).spoken.some(s => s.agent === 'garfield' && /left it/.test(s.text)))
  const records = fs.readFileSync(log, 'utf8').trim().split('\n').map(JSON.parse)
  assert(records.some(r => r.permission?.optionId === 'no'))
  const prompt = records.find(r => r.method === 'session/prompt')?.text ?? ''
  assert(!prompt.includes('[Your soul,'), 'native Hermes identity is never replaced by a Chums soul')
  const home = hub.home
  hub.stop({ keepHome: true })
  await new Promise(r => hub.hub.once('exit', r))
  hub = await startPrivateHub({ home, env: { FAKE_LOG: log } })
  await until(async () => (await state()).agents.filter(a => a.status === 'idle').length === 2)
  const after = JSON.parse(fs.readFileSync(path.join(home, '.chums/agents.json')))
  assert.deepEqual(after.map(a => a.sessionId), saved.map(a => a.sessionId))
  assert.equal((await state()).spoken.some(s => /old answer/.test(s.text)), false, 'resume replay is silent')
  console.log('PASS native Hermes ACP: distinct durable sessions, scoped denial, command preview and restart')
} finally {
  hub?.stop()
  fs.rmSync(work, { recursive: true, force: true })
}
