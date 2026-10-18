// Agents the hub runs itself: OpenAI Codex, GitHub Copilot, and any Agent Client Protocol agent. (Claude Code agents
// are interactive sessions in a terminal of their own instead: bin/chums-agents, the channel and the hooks.)
// docs/other-agents.md has the why; design/STATES.md has every signal's meaning on the watch.
//
//   runtime 'codex'    `codex app-server`: OpenAI's own JSON-RPC protocol, the one Codex's IDE extension, desktop app
//                      and terminal use, on a Unix socket of the agent's own. The hub is a client of it, and a Codex
//                      terminal can be one too, on the same thread at the same time (bin/chums-agents open).
//   runtime 'copilot'  the GitHub Copilot SDK (@github/copilot-sdk): GitHub's own JSON-RPC client for the Copilot CLI
//                      runtime, which it runs (its bundled runtime, or `command` for the installed `copilot`).
//   runtime 'acp'      any Agent Client Protocol agent, from its `command`: ["copilot", "--acp"], ["gemini",
//                      "--experimental-acp"], ["npx", "@agentclientprotocol/claude-agent-acp"]…
//
// Each run gives the watch what a Claude Code session gives it, in the same words: the hub's hook vocabulary
// (UserPromptSubmit, PreToolUse, PostToolUse, Stop, StopFailure, PreCompact…), approval cards and spoken replies.
// Orders arrive through deliver(); the agent replies with chums_reply / chums_show from the Chums MCP server
// (channel/chums-channel.mjs in tools-only mode), and when a turn from the watch ends without one, the hub speaks the
// first sentence of the agent's last message instead, so the watch never stays silent.
import { spawn, execFileSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import crypto from 'node:crypto'
import { lines, wsUnix, peer } from './jsonrpc.mjs'
import { standingFor, WATCH_GUIDE, MAX_WORDS, Owner, markFor, wordsFor } from './standing.mjs'

const ROOT = path.join(import.meta.dirname, '..')
const CHANNEL = path.join(ROOT, 'channel/chums-channel.mjs')
const REAL_HOME = os.userInfo().homedir                    // the CLIs' logins live there, whatever HOME the hub has
const VERSION = '0.1.0'

export const RUNTIMES = ['hermes', 'claude', 'codex', 'copilot', 'acp']
/// Agents whose process the hub runs (not a Claude Code session in its own terminal).
export const runByHub = a => !!a.runtime && a.runtime !== 'claude'

const DEBUG = !!process.env.CHUMS_RUNTIME_DEBUG                 // every event the agent sends, in its host log
const LIMIT = /rate.?limit|quota|too many requests|\b429\b|usage limit|allowance|overloaded|capacity|exceeded your/i
const short = (t, n = 60) => { const s = String(t ?? '').replace(/\s+/g, ' ').trim(); return s.length > n ? s.slice(0, n - 1) + '…' : s }

/// The first sentence of an agent's answer, said aloud: no markdown, code, links or paths, 15 words at most. With a
/// longer limit (an order from the iPhone), as many whole sentences as fit in it.
export function spokenLine(text, limit = MAX_WORDS) {
  const t = String(text ?? '')
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/https?:\/\/\S+/g, 'a link')
    .replace(/(^|\s)(~|\.{0,2})?\/[\w.\-/]+/g, (m, sp, _p, off, all) => sp + m.trim().split('/').filter(Boolean).pop())
    .replace(/[`*]|^#+\s*|^\s*([-•]|\d+\.)\s+/gm, '')
    .replace(/\s+/g, ' ').trim()
  if (!t) return ''
  // sentences end at . ! ? before a space or the end (so "notes.md" stays whole)
  const sentences = (t.match(/.+?[.!?]+(?=\s|$)|.+$/g) ?? [t]).map(x => x.trim())
  let first = sentences[0]
  if (limit > MAX_WORDS) for (const s of sentences.slice(1)) {             // the watch: the first sentence only
    if ((first + ' ' + s).split(' ').length > limit) break
    first += ' ' + s
  }
  const words = first.split(' ')
  return words.length > limit ? words.slice(0, limit).join(' ').replace(/[,;:]$/, '') + '…' : first
}

/// What a shell command an agent runs looks like on the watch, in Claude Code's words (hub/hub.mjs toolEvent).
function shellTool(command, actions) {
  const a = actions?.length === 1 ? actions[0] : null
  if (a?.type === 'read' && a.path) return { tool: 'Read', input: { file_path: a.path } }
  if (a?.type === 'search') return { tool: 'Grep', input: { pattern: a.query ?? a.command } }
  if (a?.type === 'listFiles') return { tool: 'Glob', input: { pattern: a.path ?? a.command } }
  // codex wraps every command in the login shell: /bin/zsh -lc '…'
  const plain = actions?.length ? actions.map(x => x.command).join(' && ') : String(command ?? '').replace(/^\S*\/(ba|z)?sh -l?c '([\s\S]*)'$/, '$2')
  return { tool: 'Bash', input: { command: plain } }
}

/// An ACP tool call in Claude Code's words.
function acpTool(tc) {
  const input = tc.rawInput ?? {}, title = String(tc.title ?? '')
  const file = tc.locations?.[0]?.path ?? input.path ?? input.file_path ?? input.filePath
  switch (tc.kind) {
    case 'read': return { tool: 'Read', input: { file_path: file ?? title } }
    case 'edit': case 'delete': case 'move': return { tool: 'Edit', input: { file_path: file ?? title } }
    case 'search': return { tool: 'Grep', input: { pattern: input.pattern ?? input.query ?? title } }
    case 'fetch': return { tool: 'WebFetch', input: { url: input.url ?? input.query ?? title } }
    case 'execute': return { tool: 'Bash', input: { command: input.command ?? input.commands?.join(' && ') ?? title } }
    case 'think': return { tool: 'Task', input: { description: title } }
    default: return { tool: short(title, 40) || 'Tool', input }
  }
}
// the agent calling Chums' own tools: never an event or an approval card, like mcp__chums__* in Claude Code
const CHUMS_TOOL = /chums[\w.-]*(reply|show)\b/i
const isChums = tc => CHUMS_TOOL.test(`${tc.title ?? ''} ${tc.toolCallId ?? ''} ${JSON.stringify(tc._meta ?? '')}`)

// ---------- one agent's process, kept running ----------
class Runner {
  constructor(agent, hub) {
    this.a = agent; this.hub = hub
    this.s = null                    // the hub's session while the agent is up
    this.asks = new Map()            // approval card id -> resolve(behavior)
    this.turn = null                 // { startedAt, text, fromWatch, tools }
    this.stopped = false; this.backoff = 5_000; this.upAt = 0; this.runs = 0; this.ended = 0
    this.logFile = path.join(hub.dir, 'agents', `${agent.id}.host.log`)
    fs.mkdirSync(path.dirname(this.logFile), { recursive: true })
  }
  note(...m) { fs.appendFileSync(this.logFile, `${new Date().toISOString().slice(0, 19).replace('T', ' ')} ${m.join(' ')}\n`) }
  env(extra = {}) {
    const PATH = [`${REAL_HOME}/.npm-global/bin`, '/opt/homebrew/bin', '/usr/local/bin', '/usr/bin', '/bin', '/usr/sbin', '/sbin', process.env.PATH].filter(Boolean).join(':')
    const env = { ...process.env, HOME: REAL_HOME, PATH, ...extra }
    for (const k of ['CHUMS_NO_WHISPER', 'CHUMS_NAME', 'CHUMS_SESSION']) delete env[k]
    return env
  }
  spawnAgent(cmd, args, stdio = ['pipe', 'pipe', 'pipe']) {
    fs.mkdirSync(this.a.cwd, { recursive: true })
    const child = spawn(cmd, args, { cwd: this.a.cwd, env: this.env(), stdio })
    child.on('error', e => this.note('could not start:', e.message))
    const out = fs.openSync(this.logFile, 'a')
    child.stderr?.on('data', d => fs.writeSync(out, d))
    child.on('exit', (code, sig) => { this.note(`exited (${sig ?? code})`); fs.closeSync(out); this.exited() })
    this.child = child
    return child
  }
  start() {
    this.upAt = Date.now(); this.runs++
    this.run().then(() => { this.note('running'); this.hub.log(`${this.a.id}: ${this.a.runtime} running`) })
      .catch(e => { this.note('not started:', e.message); this.hub.log(`${this.a.id}: ${this.a.runtime} not started: ${e.message}`); this.kill(); this.exited() })
  }
  kill() { try { this.child?.kill() } catch {} }
  /// The run is over (its process exited, or it never got going): the agent leaves the watch, and starts again later.
  exited() {
    if (this.ended === this.runs) return
    this.ended = this.runs
    if (this.s) { this.hub.leave(this.s.id); this.s = null }
    this.asks.clear(); this.turn = null; this.child = null
    if (this.stopped) return
    if (Date.now() - this.upAt > 120_000) this.backoff = 5_000           // it had been up a while: a fresh start
    const wait = this.backoff; this.backoff = Math.min(this.backoff * 2, 120_000)
    this.note(`starting again in ${wait / 1000} s`)
    setTimeout(() => !this.stopped && this.start(), wait).unref?.()
  }
  stop() { this.stopped = true; this.kill() }
  join(source) {
    this.s = this.hub.join({ id: this.sid, name: this.a.id, cwd: this.a.cwd, driver: this })
    this.hub.hook(this.s, 'SessionStart', { source })
  }

  // the hub's side: an order or a verdict for this agent
  deliver(item) {
    if (item.type === 'order') this.device = item.device                // its answer's length follows where it was asked
    if (item.type === 'verdict') { const r = this.asks.get(item.requestId); if (r) { this.asks.delete(item.requestId); r(item.behavior) } }
    else if (item.type === 'order') this.order(item)
  }
  /// An approval card on the watch; resolves 'allow' or 'deny' when it's answered.
  ask(tool, description, input) {
    const requestId = crypto.randomBytes(5).toString('hex')
    return { requestId, answer: new Promise(resolve => { this.asks.set(requestId, resolve); this.hub.ask(this.s, { requestId, tool, description, input }) }) }
  }
  newTurn(fromWatch) {
    this.turn = { startedAt: Date.now(), text: '', fromWatch, tools: new Map() }
    this.hub.hook(this.s, 'UserPromptSubmit')
  }
  /// The turn ended. A turn from the watch that didn't speak gets its answer's first sentence said for it.
  finish(how, error) {
    const s = this.s, t = this.turn
    this.turn = null
    if (!s) return
    if (how === 'interrupted') this.hub.react(s, 'interrupt')
    if (how === 'done' || how === 'interrupted') {
      const line = spokenLine(t?.text, wordsFor(this.device))
      if (t?.fromWatch && line && !(s.lastSpokeAt > t.startedAt)) this.hub.reply(s, line, 'done', 'final message')
      this.hub.hook(s, 'Stop')
    } else this.hub.hook(s, 'StopFailure', { error: LIMIT.test(error) ? 'rate_limit' : short(error) || 'error' })
  }
}

// ---------- Agent Client Protocol (`copilot --acp`, `gemini --experimental-acp`, the ACP adapters…) ----------
class AcpRunner extends Runner {
  command() {
    if (!this.a.command?.length) throw new Error('an acp agent needs "command", e.g. ["copilot", "--acp"]')
    return [...this.a.command, ...(this.a.args ?? [])]
  }
  async run() {
    const [cmd, ...args] = this.command()
    this.sid = crypto.randomUUID(); this.queue = []; this.busy = false; this.primed = false
    const child = this.spawnAgent(cmd, args)
    this.rpc = peer(lines(child), {
      onRequest: (m, p) => this.request(m, p), onNotification: (m, p) => this.update(m, p), log: (...x) => this.note(...x),
    })
    const init = await this.rpc.request('initialize', {
      protocolVersion: 1, clientCapabilities: { fs: { readTextFile: false, writeTextFile: false }, terminal: false },
      clientInfo: { name: 'chums', title: 'Chums', version: VERSION },
    }, 60_000)
    this.note(`initialized: ${init.agentInfo?.name ?? cmd} ${init.agentInfo?.version ?? ''} (protocol ${init.protocolVersion})`)
    const env = { CHUMS_SESSION: this.sid, CHUMS_HUB: this.hub.url, CHUMS_TOKEN: this.hub.token, CHUMS_NAME: this.a.id }
    const mcpServers = this.a.chumsTools === false ? [] : [{ name: 'chums', command: process.execPath, args: [CHANNEL], env: Object.entries(env).map(([name, value]) => ({ name, value })) }]
    let resumed = false
    if (this.a.sessionId && init.agentCapabilities?.loadSession) {
      this.loading = true                                     // the agent replays the conversation: none of it is news
      try { await this.rpc.request('session/load', { sessionId: this.a.sessionId, cwd: this.a.cwd, mcpServers }, 120_000); resumed = true }
      catch (e) { this.note(`couldn't load ${this.a.sessionId}: ${e.message}; starting a new one`) }
      this.loading = false
    }
    if (!resumed) {
      const r = await this.rpc.request('session/new', { cwd: this.a.cwd, mcpServers }, 120_000)
      this.hub.saveSessionId(this.a.id, r.sessionId)
    }
    this.acpSession = this.a.sessionId
    this.join(resumed ? 'resume' : 'startup')
  }
  order(item) { this.queue.push(item); if (!this.busy) this.pump() }
  // one prompt at a time (the protocol's rule); orders said meanwhile wait their turn
  async pump() {
    this.busy = true
    while (this.queue.length && this.s) {
      const { text, device } = this.queue.shift()
      const intro = this.primed ? '' : `[Standing instructions from Chums, for this whole conversation:]\n${standingFor(this.a)}\n${WATCH_GUIDE}\n\n`
      this.primed = true
      this.newTurn(true)
      try {
        const r = await this.rpc.request('session/prompt', { sessionId: this.acpSession, prompt: [{ type: 'text', text: `${intro}${markFor(device)} ${text}` }] })
        const stop = r?.stopReason ?? 'end_turn'
        if (stop === 'end_turn') this.finish('done')
        else if (stop === 'cancelled') this.finish('interrupted')
        else this.finish('failed', { max_tokens: 'max output tokens', max_turn_requests: 'too many turns', refusal: 'refused' }[stop] ?? stop)
      } catch (e) {
        if (e.code === 'closed') break
        this.note('prompt failed:', e.message); this.finish('failed', e.message)
      }
    }
    this.busy = false
  }
  update(method, p) {
    if (DEBUG) this.note('<-', method, p.update?.sessionUpdate ?? '', this.loading ? '(loading)' : '')
    if (method !== 'session/update' || this.loading || !this.s) return
    const u = p.update ?? {}, s = this.s
    this.turn ??= { startedAt: Date.now(), text: '', fromWatch: false, tools: new Map() }
    const tools = this.turn.tools
    switch (u.sessionUpdate) {
      case 'agent_message_chunk':
        if (u.content?.type === 'text') this.turn.text += u.content.text
        break
      case 'tool_call': {
        if (tools.has(u.toolCallId)) break
        this.turn.text = ''                                       // its answer is what it says after its last tool call
        if (isChums(u)) { tools.set(u.toolCallId, { chums: true }); break }
        const { tool, input } = acpTool(u)
        tools.set(u.toolCallId, { tool, kind: u.kind, title: u.title, rawInput: u.rawInput, locations: u.locations })
        this.hub.hook(s, 'PreToolUse', { tool, input })
        this.toolDone(u)
        break
      }
      case 'tool_call_update': this.toolDone(u); break
    }
  }
  toolDone(u) {
    const t = this.turn?.tools.get(u.toolCallId)
    if (!t || t.chums || t.done || !['completed', 'failed'].includes(u.status)) return
    t.done = true
    const said = (u.content ?? []).map(c => c.content?.text ?? '').join(' ')
    if (u.status === 'failed') this.hub.hook(this.s, 'PostToolUseFailure', { tool: t.tool, error: short(said || 'failed', 80) })
    else this.hub.hook(this.s, 'PostToolUse', { tool: t.tool, result: JSON.stringify(u.rawOutput ?? said).slice(-1500) })
  }
  async request(method, p) {
    if (method !== 'session/request_permission') throw Object.assign(new Error(`${method} isn't offered`), { code: -32601 })
    const known = this.turn?.tools.get(p.toolCall?.toolCallId) ?? {}
    const tc = { ...known, ...p.toolCall, kind: p.toolCall?.kind ?? known.kind, rawInput: p.toolCall?.rawInput ?? known.rawInput }
    const pick = (...kinds) => { for (const k of kinds) { const o = p.options?.find(x => x.kind === k); if (o) return { outcome: { outcome: 'selected', optionId: o.optionId } } } return { outcome: { outcome: 'cancelled' } } }
    if (known.chums || isChums(tc)) return pick('allow_once', 'allow_always')            // speaking to the watch is always allowed
    const { tool, input } = acpTool(tc)
    const behavior = await this.ask(tool, String(tc.title ?? tool), input).answer
    return behavior === 'allow' ? pick('allow_once', 'allow_always') : pick('reject_once', 'reject_always')
  }
}

// ---------- GitHub Copilot: its SDK ----------
let copilotSdk = null
const loadCopilotSdk = async () => copilotSdk ??= await import('@github/copilot-sdk')
  .catch(e => { throw new Error(`runtime "copilot" needs the Copilot SDK, an optional dependency (about 130 MB): cd ${ROOT} && npm install --no-save @github/copilot-sdk (${e.message})`) })

/// A Copilot CLI tool call in Claude Code's words.
function copilotTool(name, args, d = {}) {
  const i = args ?? {}, n = String(name ?? '').toLowerCase()
  const file = i.path ?? i.file_path ?? i.filePath ?? i.file
  if (d.shellToolInfo || ['bash', 'shell', 'powershell', 'zsh'].includes(n)) return { tool: 'Bash', input: { command: i.command ?? i.fullCommandText ?? name } }
  if (['view', 'read', 'read_file', 'show_file'].includes(n)) return { tool: 'Read', input: { file_path: file } }
  if (['create', 'write', 'write_file', 'create_file'].includes(n)) return { tool: 'Write', input: { file_path: file } }
  if (/edit|replace|patch/.test(n)) return { tool: 'Edit', input: { file_path: file } }
  if (['grep', 'rg', 'search', 'search_code'].includes(n)) return { tool: 'Grep', input: { pattern: i.pattern ?? i.query } }
  if (n === 'glob') return { tool: 'Glob', input: { pattern: i.pattern } }
  if (/fetch/.test(n)) return { tool: 'WebFetch', input: { url: i.url } }
  if (/web_?search/.test(n)) return { tool: 'WebSearch', input: { query: i.query } }
  if (['task', 'agent', 'subagent'].includes(n)) return { tool: 'Task', input: { description: i.description ?? i.prompt } }
  return { tool: short(d.toolTitle ?? (d.mcpServerName ? `${d.mcpServerName} ${d.mcpToolName ?? name}` : name), 40) || 'Tool', input: i }
}

class CopilotRunner extends Runner {
  async run() {
    const { CopilotClient, RuntimeConnection } = await loadCopilotSdk()
    const a = this.a
    this.sid = crypto.randomUUID(); this.calls = new Map()
    fs.mkdirSync(a.cwd, { recursive: true })
    const env = this.env()
    // the SDK's own Copilot runtime (pinned with the SDK), or the installed CLI when `command` names it
    this.client = new CopilotClient({
      workingDirectory: a.cwd, logLevel: 'error',
      ...(a.command?.length ? { connection: RuntimeConnection.forStdio({ path: a.command[0], args: a.command.slice(1), env }) } : { env }),
    })
    await this.client.start()
    // the SDK watches its runtime's process this way itself; when it exits, so does this run
    this.client.processExitPromise?.catch(e => { this.note(e.message); this.exited() })
    const chums = { CHUMS_SESSION: this.sid, CHUMS_HUB: this.hub.url, CHUMS_TOKEN: this.hub.token, CHUMS_NAME: a.id }
    const config = {
      ...(a.model ? { model: a.model } : {}),
      workingDirectory: a.cwd, additionalDirectories: a.addDirs ?? [],
      systemMessage: { mode: 'append', content: `${standingFor(a)}\n\n${WATCH_GUIDE}` },
      mcpServers: a.chumsTools === false ? {} : { chums: { type: 'local', command: process.execPath, args: [CHANNEL], env: chums, tools: ['*'] } },
      onPermissionRequest: req => this.permission(req),
    }
    let session = null, resumed = false
    if (a.sessionId) {
      try { session = await this.client.resumeSession(a.sessionId, config); resumed = true }
      catch (e) { this.note(`couldn't resume ${a.sessionId}: ${e.message}; starting a new session`) }
    }
    if (!session) {
      session = await this.client.createSession(config)
      this.hub.saveSessionId(a.id, session.sessionId)
    }
    this.session = session
    session.on(ev => { try { this.event(ev) } catch (e) { this.note('event:', ev.type, e.message) } })
    this.join(resumed ? 'resume' : 'startup')
    this.note(`session ${session.sessionId}`)
  }
  kill() { const c = this.client; this.client = null; c?.forceStop?.().catch(() => {}) }

  async order(item) {
    // said while it works, the order waits for the turn to end (the SDK's queue, as typing into its terminal would);
    // it becomes a turn of its own when the runtime takes it up (user.message). Its `immediate` mode, which would
    // push it into the turn in progress, can lose a message said just as a turn ends.
    if (!this.turn) this.newTurn(true)
    try { await this.session.send({ prompt: `${markFor(item.device)} ${item.text}` }) }
    catch (e) { this.note('send failed:', e.message); this.finish('failed', e.message) }
  }
  event(ev) {
    if (DEBUG && !/delta|usage|model\.|background_tasks/.test(ev.type)) this.note('<-', ev.type, `turn: ${!!this.turn}`)
    const s = this.s, d = ev.data ?? {}
    if (!s) return
    switch (ev.type) {
      case 'tool.execution_start': {
        if (d.mcpServerName === 'chums' || CHUMS_TOOL.test(d.toolName)) { this.calls.set(d.toolCallId, { chums: true }); break }
        const { tool, input } = copilotTool(d.toolName, d.arguments, d)
        this.calls.set(d.toolCallId, { tool })
        this.turn ??= { startedAt: Date.now(), text: '', fromWatch: false, tools: new Map() }
        this.hub.hook(s, 'PreToolUse', { tool, input })
        break
      }
      case 'tool.execution_complete': {
        const c = this.calls.get(d.toolCallId)
        if (!c || c.chums) break
        this.calls.delete(d.toolCallId)
        const failed = !d.success || (d.shellExecution?.exitCode ?? 0) !== 0
        if (!d.success && d.error && !/denied|declined|reject/i.test(d.error.message)) this.hub.hook(s, 'PostToolUseFailure', { tool: c.tool, error: short(d.error.message, 80) })
        else this.hub.hook(s, 'PostToolUse', { tool: c.tool, result: JSON.stringify({ is_error: failed }) })
        break
      }
      case 'user.message': if (!this.turn) this.newTurn(true); break               // a queued order, taken up now
      // its answer: the last message that isn't on its way to a tool call
      case 'assistant.message': if (this.turn && d.content?.trim() && !d.toolRequests?.length) this.turn.text = d.content; break
      case 'session.error':
        if (this.turn) this.finish('failed', ['rate_limit', 'quota'].includes(d.errorType) ? 'rate limit' : d.message ?? d.errorType)
        break
      case 'session.idle': if (this.turn) this.finish(d.aborted ? 'interrupted' : 'done'); break
      case 'session.compaction_start': this.hub.hook(s, 'PreCompact', { trigger: 'auto' }); break
      case 'session.compaction_complete': this.hub.hook(s, 'PostCompact'); break
      case 'subagent.started': this.hub.hook(s, 'SubagentStart', { agent: d.toolCallId }); break
      case 'subagent.completed': case 'subagent.failed': this.hub.hook(s, 'SubagentStop', { agent: d.toolCallId }); break
    }
  }
  async permission(req) {
    if ((req.kind === 'mcp' && req.serverName === 'chums') || (req.kind === 'custom-tool' && CHUMS_TOOL.test(req.toolName))) return { kind: 'approve-once' }
    const [tool, description, input] =
      req.kind === 'shell' ? ['Bash', req.intention, { command: req.fullCommandText }]
      : req.kind === 'write' ? [req.newFileContents != null && !req.diff ? 'Write' : 'Edit', req.intention, { file_path: req.fileName }]
      : req.kind === 'read' ? ['Read', req.intention, { file_path: req.path }]
      : req.kind === 'url' ? ['WebFetch', req.intention, { url: req.url }]
      : req.kind === 'mcp' ? [`${req.serverName} ${req.toolName}`, req.toolTitle, req.args ?? {}]
      : [req.kind, req.intention ?? req.toolName ?? req.kind, req.args ?? {}]
    const ok = await this.ask(tool, String(description || tool), input).answer
    return ok === 'allow' ? { kind: 'approve-once' } : { kind: 'reject', feedback: `${Owner} said no, on the watch.` }
  }
}

// ---------- OpenAI Codex: its app-server ----------
const SOCK_DIR = `/tmp/chums-${process.getuid?.() ?? 'u'}`        // short: a Unix socket's path has room for 103 bytes
const toml = v => JSON.stringify(v)                               // TOML's strings and arrays of strings are JSON's

class CodexRunner extends Runner {
  socket() { return path.join(SOCK_DIR, `${this.hub.port}-${this.a.id}.sock`) }
  /// An app-server a previous hub left running on this socket (a hub killed hard can't stop it): stop it first.
  stale(sock) {
    try {
      const { pid } = JSON.parse(fs.readFileSync(path.join(this.hub.dir, 'agents', `${this.a.id}.runtime.json`), 'utf8'))
      const cmd = execFileSync('/bin/ps', ['-o', 'command=', '-p', String(pid)], { encoding: 'utf8' })
      if (cmd.includes('app-server') && cmd.includes(sock)) { process.kill(pid); this.note(`stopped the app-server a previous run left (pid ${pid})`) }
    } catch {}
  }
  async run() {
    const a = this.a, sock = this.socket()
    fs.mkdirSync(SOCK_DIR, { recursive: true, mode: 0o700 })
    // a folder of our own, not a link to one, that only we can open (one made earlier with other modes is closed)
    const dir = fs.lstatSync(SOCK_DIR)
    if (!dir.isDirectory() || dir.uid !== process.getuid()) throw new Error(`${SOCK_DIR} isn't ours`)
    if (dir.mode & 0o077) fs.chmodSync(SOCK_DIR, 0o700)
    this.stale(sock)
    fs.rmSync(sock, { force: true })
    this.sid = crypto.randomUUID(); this.files = new Map(); this.byRpc = new Map(); this.activeTurn = null
    // the token's file, not the token: Codex's -c arguments are in the process list, which every user of the Mac can read
    const env = `{ CHUMS_SESSION = ${toml(this.sid)}, CHUMS_HUB = ${toml(this.hub.url)}, CHUMS_CONFIG = ${toml(path.join(this.hub.dir, 'config.json'))}, CHUMS_NAME = ${toml(a.id)} }`
    const config = [
      ...(a.chumsTools === false ? [] : [
        `mcp_servers.chums.command=${toml(process.execPath)}`, `mcp_servers.chums.args=[${toml(CHANNEL)}]`, `mcp_servers.chums.env=${env}`,
      ]),
      ...(a.addDirs?.length ? [`sandbox_workspace_write.writable_roots=[${a.addDirs.map(toml).join(', ')}]`] : []),
      ...(a.config ?? []),
    ]
    const [bin = 'codex', ...more] = a.command ?? []
    this.spawnAgent(bin, [...more, 'app-server', '--listen', `unix://${sock}`, ...config.flatMap(c => ['-c', c])], ['ignore', 'ignore', 'pipe'])
    let transport = null
    for (let i = 0; i < 150 && !transport && this.child; i++) {
      await new Promise(r => setTimeout(r, 100))
      if (fs.existsSync(sock)) transport = await wsUnix(sock).catch(() => null)
    }
    if (!transport) throw new Error(`codex app-server didn't open ${sock}`)
    this.rpc = peer(transport, {
      onRequest: (m, p, id) => this.request(m, p, id), onNotification: (m, p) => this.event(m, p), log: (...x) => this.note(...x),
    })
    this.rpc.on('close', () => this.kill())
    const init = await this.rpc.request('initialize', { clientInfo: { name: 'chums', title: 'Chums', version: VERSION }, capabilities: null }, 60_000)
    this.rpc.notify('initialized')
    this.note(`initialized: ${init.userAgent}`)
    const params = {
      cwd: a.cwd, model: a.model ?? null, approvalPolicy: a.approvalPolicy ?? this.hub.codexPolicy(), sandbox: a.sandbox ?? 'workspace-write',
      developerInstructions: `${standingFor(a)}\n\n${WATCH_GUIDE}`,
    }
    let thread = null
    if (a.sessionId) {
      try { thread = (await this.rpc.request('thread/resume', { threadId: a.sessionId, ...params, excludeTurns: true }, 120_000)).thread }
      catch (e) { this.note(`couldn't resume ${a.sessionId}: ${e.message}; starting a new thread`) }
    }
    if (!thread) {
      thread = (await this.rpc.request('thread/start', params, 120_000)).thread
      this.hub.saveSessionId(a.id, thread.id)
    }
    this.threadId = thread.id
    this.nameThread()
    // what bin/chums-agents open needs to put a Codex terminal on this same thread
    fs.writeFileSync(path.join(this.hub.dir, 'agents', `${a.id}.runtime.json`),
      JSON.stringify({ runtime: 'codex', thread: thread.id, socket: sock, cwd: a.cwd, pid: this.child?.pid }, null, 2))
    this.join(a.sessionId === thread.id && thread.status?.type !== 'notLoaded' ? 'resume' : 'startup')
  }

  /// Its name in Codex's own lists (`codex resume`, the desktop app): "Chums: <agent>". A new thread can only be
  /// named once it has a first turn on disk, so it's tried again after each turn until it takes.
  nameThread() {
    const name = `Chums: ${this.a.name ?? this.a.id}`
    this.rpc.request('thread/name/set', { threadId: this.threadId, name }, 30_000)
      .then(() => { this.named = true }, e => DEBUG && this.note('naming the thread:', e.message))
  }

  async order(item) {
    const s = this.s
    if (this.answerNext) { const r = this.answerNext; this.answerNext = null; return r(item.said ?? item.text) }
    const input = [{ type: 'text', text: `${markFor(item.device)} ${item.text}`, text_elements: [] }]
    if (this.activeTurn) {
      // it's working: the order joins the turn in progress, as typing into its terminal would
      try {
        await this.rpc.request('turn/steer', { threadId: this.threadId, input, expectedTurnId: this.activeTurn })
        if (this.turn) Object.assign(this.turn, { fromWatch: true, startedAt: Date.now(), text: '' })   // what counts is the answer to this
        return
      }
      catch (e) { this.note('steer refused:', e.message) }
    }
    this.watchTurn = true
    try { await this.rpc.request('turn/start', { threadId: this.threadId, input }) }
    catch (e) {
      this.watchTurn = false
      // a turn was still ending (too late to steer, too early to start): it goes once that turn is over
      if (this.activeTurn) { this.note('order waits for the turn to end:', e.message); (this.waitingOrders ??= []).push(item); return }
      this.note('turn failed to start:', e.message); if (s === this.s) this.finish('failed', e.message)
    }
  }

  event(method, p) {
    if (DEBUG && !/delta|tokenUsage|rateLimits|startupStatus/.test(method)) this.note('<-', method, p.item?.type ?? '')
    const s = this.s
    if (!s || (p.threadId && p.threadId !== this.threadId)) return
    switch (method) {
      case 'turn/started':
        this.activeTurn = p.turn.id
        this.newTurn(this.watchTurn); this.watchTurn = false          // a turn typed in a Codex terminal isn't the watch's
        break
      case 'turn/completed': {
        this.activeTurn = null
        if (!this.named) this.nameThread()
        if (this.waitingOrders?.length) setTimeout(() => this.order(this.waitingOrders.shift()), 0)
        const t = p.turn
        if (t.status === 'failed') {
          const info = t.error?.codexErrorInfo, code = typeof info === 'string' ? info : Object.keys(info ?? {})[0]
          this.finish('failed', ['usageLimitExceeded', 'rateLimitExceeded', 'serverOverloaded', 'sessionBudgetExceeded'].includes(code) ? 'rate limit' : t.error?.message ?? code)
        } else this.finish(t.status === 'interrupted' ? 'interrupted' : 'done')
        break
      }
      case 'item/started': this.itemStarted(p.item); break
      case 'item/completed': this.itemCompleted(p.item); break
      case 'thread/status/changed':
        if (p.status?.activeFlags?.includes('waitingOnUserInput')) { s.status = 'waiting'; this.hub.bump() }
        break
      case 'mcpServer/startupStatus/updated':
        if (p.name === 'chums' && p.status !== 'starting') this.note(`chums tools: ${p.status}${p.error ? ` (${p.error})` : ''}`)
        break
      case 'serverRequest/resolved': {
        // answered in a Codex terminal on the same thread: the card on the watch goes
        const id = this.byRpc.get(p.requestId); this.byRpc.delete(p.requestId)
        if (id && this.asks.has(id)) { this.asks.delete(id); this.hub.closeApproval(id) }
        break
      }
    }
  }
  itemStarted(item) {
    const s = this.s, hook = (tool, input) => this.hub.hook(s, 'PreToolUse', { tool, input })
    switch (item.type) {
      case 'commandExecution': { const t = shellTool(item.command, item.commandActions); hook(t.tool, t.input); break }
      case 'fileChange':
        this.files.set(item.id, item.changes ?? [])
        hook(item.changes?.length === 1 && item.changes[0].kind?.type === 'add' ? 'Write' : 'Edit', { file_path: item.changes?.[0]?.path })
        break
      case 'mcpToolCall': if (item.server !== 'chums') hook(`${item.server} ${item.tool}`, item.arguments); break
      case 'dynamicToolCall': hook(item.tool, item.arguments); break
      case 'webSearch': hook('WebSearch', { query: item.query }); break
      case 'imageView': hook('Read', { file_path: item.path }); break
      case 'contextCompaction': this.hub.hook(s, 'PreCompact', { trigger: 'auto' }); break
      case 'subAgentActivity': if (item.kind === 'started') this.hub.hook(s, 'SubagentStart', { agent: item.agentThreadId }); break
    }
  }
  itemCompleted(item) {
    const s = this.s, done = failed => this.hub.hook(s, 'PostToolUse', { result: JSON.stringify({ is_error: !!failed }) })
    switch (item.type) {
      case 'commandExecution':                                // couldn't run at all: a failure; ran and exited non-zero: marked
        if (item.status === 'failed' && item.exitCode == null) this.hub.hook(s, 'PostToolUseFailure', { error: short(item.aggregatedOutput || 'failed', 80) })
        else done(item.status !== 'completed' || (item.exitCode ?? 0) !== 0)
        break
      case 'fileChange': done(item.status !== 'completed'); break
      case 'mcpToolCall': if (item.server !== 'chums') done(item.status === 'failed'); break
      case 'dynamicToolCall': done(item.success === false); break
      case 'webSearch': case 'imageView': done(false); break
      case 'contextCompaction': this.hub.hook(s, 'PostCompact'); break
      case 'subAgentActivity': if (item.kind !== 'started' && item.kind !== 'interacted') this.hub.hook(s, 'SubagentStop', { agent: item.agentThreadId }); break
      case 'collabAgentToolCall':
        for (const id of item.receiverThreadIds ?? []) {
          if (item.tool === 'spawnAgent' && item.status === 'completed') this.hub.hook(s, 'SubagentStart', { agent: id })
          if (item.tool === 'closeAgent') this.hub.hook(s, 'SubagentStop', { agent: id })
        }
        break
      case 'agentMessage': if (this.turn && item.phase !== 'commentary') this.turn.text = item.text ?? ''; break
    }
  }
  async request(method, p, rpcId) {
    const card = (tool, description, input) => { const c = this.ask(tool, description, input); this.byRpc.set(rpcId, c.requestId); return c.answer }
    switch (method) {
      case 'item/commandExecution/requestApproval': {
        const t = shellTool(p.command, p.commandActions)
        const ok = await card('Bash', p.reason ?? 'Run a command', { command: t.tool === 'Bash' ? t.input.command : p.commandActions?.map(x => x.command).join(' && ') ?? p.command })
        // decline: the command doesn't run and the turn goes on, so the agent can say so (cancel would end the turn).
        // Codex 0.160 takes it even when availableDecisions leaves it out.
        return { decision: ok === 'allow' ? 'accept' : 'decline' }
      }
      case 'item/fileChange/requestApproval': {
        const changes = this.files.get(p.itemId) ?? []
        const ok = await card(changes.length === 1 && changes[0].kind?.type === 'add' ? 'Write' : 'Edit',
          p.reason ?? `Change ${changes.length || 'some'} file${changes.length === 1 ? '' : 's'}`, { file_path: changes[0]?.path ?? p.grantRoot })
        return { decision: ok === 'allow' ? 'accept' : 'decline' }
      }
      case 'item/permissions/requestApproval': {
        const want = p.permissions ?? {}
        const what = [want.network && 'network access', want.fileSystem && 'more file access'].filter(Boolean).join(' and ') || 'more access'
        const ok = await card('Permissions', p.reason ?? `Allow ${what}`, { description: p.reason ?? what })
        const grant = {}
        if (ok === 'allow') { if (want.network) grant.network = want.network; if (want.fileSystem) grant.fileSystem = want.fileSystem }
        return { permissions: grant, scope: 'turn' }
      }
      case 'mcpServer/elicitation/request': {
        if (p.serverName === 'chums') return { action: 'accept', content: {}, _meta: null }
        const ok = await card(p.serverName, short(p.message, 160), { url: p.url, description: p.message })
        return { action: ok === 'allow' ? 'accept' : 'decline', content: ok === 'allow' ? {} : null, _meta: null }
      }
      case 'item/tool/requestUserInput': {
        // a question: said on the watch, and what's said next is the answer
        this.hub.reply(this.s, spokenLine(p.questions?.map(q => q.question).join(' ')) || 'A question for you.', 'question')
        const said = await new Promise(r => { this.answerNext = r })
        return { answers: Object.fromEntries((p.questions ?? []).map(q => [q.id, { answers: [said] }])) }
      }
    }
    throw Object.assign(new Error(`${method} isn't offered`), { code: -32601 })
  }
}

// ---------- Hermes / OpenRouter native agent runner ----------
class HermesRunner extends Runner {
  constructor(agent, hub) {
    super(agent, hub)
    this.sid = crypto.randomUUID()
    this.history = []
  }
  async run() {
    this.join('startup')
  }
  async order(item) {
    this.newTurn(item.device === 'watch')
    const prompt = item.said || item.text
    try {
      let openrouterKey = process.env.OPENROUTER_API_KEY
      if (!openrouterKey) {
        const authPath = path.join(REAL_HOME, '.local/share/opencode/auth.json')
        if (fs.existsSync(authPath)) {
          const auth = JSON.parse(fs.readFileSync(authPath, 'utf8'))
          openrouterKey = auth.openrouter?.key
        }
      }

      const systemPrompt = `You are ${this.a.name}, Kash's AI assistant on his Android wearable communicator.
Be calm, practical, concise, and helpful. No em dashes. No corporate filler.
Reply in 1 or 2 punchy, conversational sentences under 25 words suitable for speech.`

      this.history.push({ role: 'user', content: prompt })
      if (this.history.length > 10) this.history = this.history.slice(-10)

      this.hub.hook(this.s, 'PreToolUse', { tool: 'Task', input: { description: 'Thinking' } })

      const res = await fetch('https://openrouter.ai/api/v1/chat/completions', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${openrouterKey}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          model: this.a.model || 'deepseek/deepseek-chat',
          messages: [
            { role: 'system', content: systemPrompt },
            ...this.history
          ],
          max_tokens: 80
        })
      })

      if (!res.ok) {
        const err = await res.text()
        throw new Error(`OpenRouter HTTP ${res.status}: ${err}`)
      }

      const data = await res.json()
      const reply = data.choices?.[0]?.message?.content?.trim() || "I'm here."
      this.history.push({ role: 'assistant', content: reply })

      this.hub.hook(this.s, 'PostToolUse')
      this.hub.reply(this.s, reply, 'reply')
      this.finish('done')
    } catch (e) {
      this.note('order error:', e.message)
      this.hub.reply(this.s, `Sorry, ${e.message}`, 'error')
      this.finish('error', e.message)
    }
  }
}

/// Starts one agent the hub runs (null for a runtime it doesn't know).
export function startRunner(a, hub) {
  if (!RUNTIMES.includes(a.runtime) || !runByHub(a)) { hub.log(`${a.id}: unknown runtime ${a.runtime} (one of ${RUNTIMES.join(', ')})`); return null }
  const r = new { hermes: HermesRunner, codex: CodexRunner, copilot: CopilotRunner, acp: AcpRunner }[a.runtime](a, hub)
  r.start()
  return r
}

/// Starts every agent the hub runs; returns them by agent id.
export function startRunners(agents, hub) {
  const runners = new Map()
  for (const a of agents.filter(runByHub)) { const r = startRunner(a, hub); if (r) runners.set(a.id, r) }
  return runners
}
