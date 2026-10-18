// Hermes agents (runtime "hermes" in hub/runtimes.mjs) against stand-ins: a chat-completions server that checks every
// request the way OpenAI-style APIs do (a tool result must follow the call that asked for it), and a Home Assistant
// that records what it was asked. The watch's side is the hub's API, as in tools/test-runtimes.mjs.
// node tools/test-hermes.mjs
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import http from 'node:http'
import { startPrivateHub } from './test-hub-private.mjs'

const work = fs.mkdtempSync(path.join(os.tmpdir(), 'chums-hermes-'))
const listen = handler => new Promise(r => { const s = http.createServer(handler).listen(0, '127.0.0.1', () => r(s)) })
const bodyOf = req => new Promise(r => { let b = ''; req.on('data', d => b += d); req.on('end', () => r(b)) })

// ----- the model: a script per order, picked by a word in the order -----
const requests = []
let failNext = 0, slow = 0
const call = (id, name, args) => ({ id, type: 'function', function: { name, arguments: JSON.stringify(args) } })
const say = content => ({ role: 'assistant', content })
const script = {
  uname: [{ role: 'assistant', content: null, tool_calls: [call('u1', 'bash', { command: 'uname -s' })] }],
  twice: [{ role: 'assistant', content: null, tool_calls: [call('t1', 'bash', { command: 'echo one' }), call('t2', 'bash', { command: 'echo two' })] }],
  chain: [{ role: 'assistant', content: null, tool_calls: [call('c1', 'bash', { command: 'echo first' })] },
          { role: 'assistant', content: null, tool_calls: [call('c2', 'bash', { command: 'echo second' })] }],
  sleepy: [{ role: 'assistant', content: null, tool_calls: [call('s1', 'bash', { command: 'sleep 1.5; echo awake' })] }],
  lights: [{ role: 'assistant', content: null, tool_calls: [call('h1', 'home_assistant', { domain: 'light', service: 'turn_off', entity_id: 'light.living_room' })] }],
  unlock: [{ role: 'assistant', content: null, tool_calls: [call('l1', 'home_assistant', { domain: 'lock', service: 'unlock', entity_id: 'lock.front_door' })] }],
  sneaky: [{ role: 'assistant', content: null, tool_calls: [call('x1', 'home_assistant', { domain: '../../api/config/core', service: 'check_config', entity_id: 'a.b' }),
    call('x2', 'home_assistant', { domain: 'light', service: 'turn_off' }), call('x3', 'home_assistant', { domain: 'homeassistant', service: 'restart', entity_id: 'homeassistant.x' })] }],
  ghost: [{ role: 'assistant', content: null, tool_calls: [call('g1', 'chums_show', {})] }],
  homelab: [{ role: 'assistant', content: null, tool_calls: [call('p1', 'homelab_status', {})] }],
}
const model = await listen(async (req, res) => {
  const body = JSON.parse(await bodyOf(req)), msgs = body.messages
  requests.push(body)
  const send = (code, j) => { res.writeHead(code, { 'content-type': 'application/json' }); res.end(JSON.stringify(j)) }
  // what a real API rejects: a tool result whose call isn't in the assistant message just before it
  for (let i = 0; i < msgs.length; i++) if (msgs[i].role === 'tool') {
    let j = i - 1; while (msgs[j]?.role === 'tool') j--
    if (!msgs[j]?.tool_calls?.some(c => c.id === msgs[i].tool_call_id)) return send(400, { error: { message: "messages with role 'tool' must be a response to a preceeding message with 'tool_calls'" } })
  }
  if (failNext > 0) { failNext--; return send(500, { error: { message: 'upstream exploded' } }) }
  if (slow) await new Promise(r => setTimeout(r, slow))
  const users = msgs.filter(m => m.role === 'user'), order = users.at(-1).content
  const key = Object.keys(script).find(k => order.includes(k))
  const round = msgs.slice(msgs.lastIndexOf(users.at(-1)) + 1).filter(m => m.role === 'assistant').length
  const step = key && script[key][round]
  if (step) return send(200, { choices: [{ message: step }] })
  const last = msgs.at(-1)
  send(200, { choices: [{ message: say(last.role === 'tool' ? `Result: ${msgs.filter(m => m.role === 'tool').slice(-3).map(m => m.content).join(' | ')}` : `You said ${order.split('\n').pop().replace(/^\[[^\]]*\] /, '')}`) }] })
})
// ----- Home Assistant -----
const hass = []
const ha = await listen(async (req, res) => { hass.push(`${req.url} ${await bodyOf(req)}`); res.writeHead(req.headers.authorization === 'Bearer hass-good' ? 200 : 401); res.end('[]') })

const agents = [{ id: 'garfield', name: 'Garfield', character: 'garfield', runtime: 'hermes', cwd: path.join(work, 'garfield') }]
const { cfg, stop } = await startPrivateHub({ agents, config: {
  hermesBaseUrl: `http://127.0.0.1:${model.address().port}/v1`, hermesApiKey: 'test',
  hassUrl: `http://127.0.0.1:${ha.address().port}`, hassToken: 'hass-good',
  homelab: [{ name: 'Model', host: '127.0.0.1', port: model.address().port }, { name: 'Nothing', host: '127.0.0.1', port: 9 }],
} })
const H = `http://127.0.0.1:${cfg.port}`
const api = async (p, body) => {
  const r = await fetch(H + p, { method: body ? 'POST' : 'GET', headers: { authorization: `Bearer ${cfg.token}`, 'content-type': 'application/json' }, body: body && JSON.stringify(body) })
  return r.json().catch(() => null)
}
const state = () => api('/v1/state?since=0')
const sleep = ms => new Promise(r => setTimeout(r, ms))
const until = async (what, f, ms = 8000) => {
  const end = Date.now() + ms
  for (;;) { const v = await f(); if (v) return v; if (Date.now() > end) { console.log('  (timed out waiting for', what + ')'); return null } await sleep(100) }
}
let fails = 0; const ok = (c, m) => { console.log(c ? 'PASS' : 'FAIL', m); if (!c) fails++ }
const seen = new Set()
const lines = async () => (await state()).spoken.filter(l => l.agent === 'garfield' && ['done', 'blocked'].includes(l.kind))
/// Says `text` to Garfield and waits for its next line.
const ask = async (text, ms) => {
  await api('/v1/say', { text, agent: 'garfield', device: 'phone' })
  const l = await until(`a reply to "${text}"`, async () => (await lines()).find(x => !seen.has(x.id)), ms)
  for (const x of await lines()) seen.add(x.id)
  return l
}
/// The approval card the agent is waiting on, answered.
const answer = async behavior => {
  const a = await until('an approval card', async () => (await state()).approvals.find(x => x.session === 'garfield'))
  if (a) await api('/v1/verdict', { requestId: a.requestId, behavior })
  return a
}

ok(await until('garfield to join', async () => (await state()).agents?.find(a => a.id === 'garfield')?.status === 'idle'), 'a hermes agent joins the hub')
ok((await api('/v1/permissions'))?.hermes?.default === 'Asks first', 'Hermes has permission modes; Asks first by default')

let r = await ask('plain hello')
ok(r?.text === 'You said plain hello' && r.kind === 'done', `a turn without tools is answered (${r?.text})`)

const pending = ask('uname please')
let card = await answer('allow')
ok(card?.tool === 'Bash' && card.preview.includes('uname -s'), 'in Asks first, a command waits for Allow on the watch and phone')
r = await pending
ok(/Result: Linux|Result: Darwin/.test(r?.text ?? ''), `allowed, it runs and the model gets its output (${r?.text})`)

const declined = ask('uname again')
await answer('deny')
r = await declined
ok(requests.at(-1).messages.at(-1).content.includes('declined'), 'denied, the model is told it was declined')

await api('/v1/agents/garfield/permission', { mode: 'auto' })
await until('garfield back in auto', async () => (await state()).agents?.find(a => a.id === 'garfield')?.status === 'idle')
ok((await state()).agents.find(a => a.id === 'garfield').permission === 'auto', 'its mode can be set to Decides on its own')

r = await ask('twice now')
ok(/one.*two/.test(r?.text ?? ''), `two tool calls in one message both run (${r?.text})`)
for (const t of ['after one', 'after two', 'after three', 'after four', 'twice more', 'after five', 'after six', 'after seven', 'after eight', 'after nine']) await ask(t)
const orphans = requests.filter(b => b.messages.find(m => m.role !== 'system')?.role === 'tool')
ok(orphans.length === 0 && (await lines()).at(-1).text === 'You said after nine', 'trimmed history never starts with a tool result, so the API never rejects it')

r = await ask('chain them')
ok(/first.*second/.test(r?.text ?? ''), `a second round of tool calls runs too (${r?.text})`)

failNext = 1
r = await ask('this one fails')
ok(r?.kind === 'blocked' && /error, 500/.test(r.text), `an API error is said as a sentence, not "Done." (${r?.text})`)
r = await ask('and recovers')
ok(r?.text === 'You said and recovers', 'the next turn works: the failed one left nothing half-written behind')

const ping = (async () => { await sleep(300); const s = Date.now(); await api('/v1/ping'); return Date.now() - s })()
r = await ask('sleepy command')
ok(/awake/.test(r?.text ?? '') && await ping < 500, 'a slow command doesn\'t stop the hub (it answered a ping meanwhile)')

slow = 300
await api('/v1/say', { text: 'first of two', agent: 'garfield', device: 'phone' })
await api('/v1/say', { text: 'second of two', agent: 'garfield', device: 'phone' })
const two = (await until('both replies', async () => { const n = (await lines()).filter(x => !seen.has(x.id)); return n.length >= 2 && n }, 10000) || []).map(l => l.text)
slow = 0
for (const x of await lines()) seen.add(x.id)
ok(two[0] === 'You said first of two' && two[1] === 'You said second of two', `orders said at once are answered in order (${two.join(' / ')})`)

r = await ask('lights off')
ok(hass.at(-1) === '/api/services/light/turn_off {"entity_id":"light.living_room"}' && /done/.test(r?.text ?? ''), 'Home Assistant gets the service call')
const before = hass.length
r = await ask('sneaky calls')
const said = requests.at(-1).messages.filter(m => m.role === 'tool').slice(-3).map(m => m.content)
ok(hass.length === before && said[0].includes('must be one of') && said[1].includes('entity') && said[2].includes('must be one of'),
  'a domain outside the list, a path in the domain, or no entity is refused without calling Home Assistant')

await api('/v1/agents/garfield/permission', { mode: 'default' })
await until('garfield back in default', async () => (await state()).agents?.find(a => a.id === 'garfield')?.status === 'idle')
const unlocking = ask('unlock the door')
card = await answer('deny')
await unlocking
ok(card?.tool === 'Home Assistant' && !hass.some(h => h.includes('lock/unlock')), 'a lock asks first, and denied, Home Assistant is never called')

r = await ask('ghost tool')
ok(requests.at(-1).messages.at(-1).content === 'Unknown tool chums_show.', 'a tool it doesn\'t have is reported as unknown')
r = await ask('homelab check')
ok(/Model: up, Nothing: down/.test(r?.text ?? ''), `homelab probes come from the config (${r?.text})`)
ok(!requests.at(-1).tools?.some(t => t.function.name === 'weather'), 'no weather tool without a "weather" place in the config')

console.log(fails ? `${fails} FAILED` : 'all passed')
stop(); model.close(); ha.close(); fs.rmSync(work, { recursive: true, force: true })
process.exit(fails ? 1 : 0)
