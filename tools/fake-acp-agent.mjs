// A stand-in Agent Client Protocol agent for tools/test-runtimes.mjs: the hub drives it as it would `copilot --acp`.
// What it does depends on words in the prompt (tool, approve, reply, refuse, limit, crash); it notes every request in
// $FAKE_LOG, one JSON line each, so the test can check what the hub sent.
import fs from 'node:fs'
import readline from 'node:readline'
import { spawn } from 'node:child_process'

const note = o => process.env.FAKE_LOG && fs.appendFileSync(process.env.FAKE_LOG, JSON.stringify({ agent: 'acp', ...o }) + '\n')
const send = m => process.stdout.write(JSON.stringify({ jsonrpc: '2.0', ...m }) + '\n')
let next = 0
const waiting = new Map()
const ask = (method, params) => new Promise(r => { const id = next++; waiting.set(id, r); send({ id, method, params }) })
const update = (sessionId, u) => send({ method: 'session/update', params: { sessionId, update: u } })
const say = (sessionId, text) => { for (const part of text.match(/.{1,12}/g)) update(sessionId, { sessionUpdate: 'agent_message_chunk', content: { type: 'text', text: part } }) }
let mcp = null                                                  // the Chums MCP server the hub gave the session

// the session's MCP server, spoken to the way an agent would: initialize, then tools/call
async function callTool(server, name, args) {
  if (!mcp) {
    const env = Object.fromEntries((server.env ?? []).map(e => [e.name, e.value]))
    const p = spawn(server.command, server.args, { env: { ...process.env, ...env }, stdio: ['pipe', 'pipe', 'inherit'] })
    const pending = new Map(); let id = 0
    readline.createInterface({ input: p.stdout }).on('line', l => { const m = JSON.parse(l); pending.get(m.id)?.(m); pending.delete(m.id) })
    const rpc = (method, params) => new Promise(r => { const i = ++id; pending.set(i, r); p.stdin.write(JSON.stringify({ jsonrpc: '2.0', id: i, method, params }) + '\n') })
    await rpc('initialize', { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 'fake-acp', version: '0' } })
    p.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) + '\n')
    const tools = await rpc('tools/list', {})
    mcp = { rpc, tools: tools.result.tools }
    note({ mcpTools: mcp.tools.map(t => ({ name: t.name, annotations: t.annotations })) })
  }
  return mcp.rpc('tools/call', { name, arguments: args })
}

const sessions = new Map()
async function prompt(id, { sessionId, prompt: blocks }) {
  const full = blocks.map(b => b.text ?? '').join('')
  note({ method: 'session/prompt', text: full })
  const text = full.split('\n').pop()                          // his words: the last line, after instructions and soul
  const s = sessions.get(sessionId)
  if (/crash/.test(text)) process.exit(1)
  if (/limit/.test(text)) return send({ id, error: { code: -32000, message: 'Rate limit exceeded. Try again in a minute.' } })
  if (/refuse/.test(text)) return send({ id, result: { stopReason: 'refusal' } })
  update(sessionId, { sessionUpdate: 'agent_thought_chunk', content: { type: 'text', text: 'Thinking.' } })
  if (/tool/.test(text)) {
    update(sessionId, { sessionUpdate: 'tool_call', toolCallId: 't1', title: 'Run the tests', kind: 'execute', status: 'pending', rawInput: { command: 'npm test' } })
    update(sessionId, { sessionUpdate: 'tool_call_update', toolCallId: 't1', status: 'in_progress' })
    update(sessionId, { sessionUpdate: 'tool_call_update', toolCallId: 't1', status: 'completed', rawOutput: { content: '12 passing\n<exited with exit code 0>' } })
    update(sessionId, { sessionUpdate: 'tool_call', toolCallId: 't2', title: 'Read package.json', kind: 'read', status: 'completed', locations: [{ path: '/tmp/x/package.json' }] })
    say(sessionId, 'Tests pass: all `12` of them, in **3 seconds**. Here are the details of every test that ran today.')
  }
  if (/approve/.test(text)) {
    const tc = { toolCallId: 'p1', title: 'Delete the build folder', kind: 'execute', status: 'pending', rawInput: { command: 'rm -rf build' } }
    update(sessionId, { sessionUpdate: 'tool_call', ...tc })
    const r = await ask('session/request_permission', { sessionId, toolCall: tc, options: [
      { optionId: 'yes-once', kind: 'allow_once', name: 'Allow once' }, { optionId: 'yes-always', kind: 'allow_always', name: 'Always allow' },
      { optionId: 'no', kind: 'reject_once', name: 'Deny' }] })
    note({ permission: r.result?.outcome })
    const allowed = r.result?.outcome?.optionId === 'yes-once'
    update(sessionId, { sessionUpdate: 'tool_call_update', toolCallId: 'p1', status: allowed ? 'completed' : 'failed', content: allowed ? [] : [{ type: 'content', content: { type: 'text', text: 'denied by the user' } }] })
    say(sessionId, allowed ? 'Deleted the build folder.' : 'Okay, I left it.')
  }
  if (/reply/.test(text)) {
    // the agent asks before an MCP tool call, as Copilot does; the hub allows its own tools without a card
    const tc = { toolCallId: 'c1', title: 'chums-chums_reply', kind: 'other', status: 'pending', rawInput: { text: 'Replied through the tool.' } }
    update(sessionId, { sessionUpdate: 'tool_call', ...tc })
    const r = await ask('session/request_permission', { sessionId, toolCall: tc, options: [{ optionId: 'ok', kind: 'allow_once', name: 'Allow' }, { optionId: 'no', kind: 'reject_once', name: 'Deny' }] })
    note({ chumsPermission: r.result?.outcome })
    const out = await callTool(s.mcpServers.find(m => m.name === 'chums'), 'chums_reply', { text: 'Replied through the tool.', status: 'done' })
    note({ chumsReply: out.result?.content?.[0]?.text })
    update(sessionId, { sessionUpdate: 'tool_call_update', toolCallId: 'c1', status: 'completed' })
    say(sessionId, 'This long final message is for the Mac only and should never be spoken on the watch at all.')
  }
  send({ id, result: { stopReason: 'end_turn' } })
}

readline.createInterface({ input: process.stdin }).on('line', l => {
  const m = JSON.parse(l)
  if (m.id !== undefined && !m.method) { waiting.get(m.id)?.(m); waiting.delete(m.id); return }
  if (m.method !== 'session/prompt') note({ method: m.method, params: m.params })
  switch (m.method) {
    case 'initialize':
      return send({ id: m.id, result: { protocolVersion: 1, agentCapabilities: { loadSession: true }, agentInfo: { name: 'fake-acp', version: '1' }, authMethods: [] } })
    case 'session/new': {
      const sessionId = `fake-${process.pid}-${Date.now()}`
      sessions.set(sessionId, m.params)
      return send({ id: m.id, result: { sessionId } })
    }
    case 'session/load':
      sessions.set(m.params.sessionId, m.params)
      say(m.params.sessionId, 'An old answer, replayed while loading.')     // never news: the hub must ignore it
      return send({ id: m.id, result: {} })
    case 'session/prompt': return prompt(m.id, m.params)
    default: if (m.id !== undefined) send({ id: m.id, error: { code: -32601, message: 'no' } })
  }
})
