import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { once } from 'node:events'
import { RequestLedger } from '../hub/request-ledger.mjs'
import { startPrivateHub } from './test-hub-private.mjs'

const work = fs.mkdtempSync(path.join(os.tmpdir(), 'chums-phone-'))
let hub
try {
  const file = path.join(work, 'ledger.json')
  const ledger = new RequestLedger(file)
  let executions = 0
  const execute = async () => { executions++; await new Promise(r => setTimeout(r, 20)); return { kind: 'order', id: 'one' } }
  const id = 'phone-concurrent-0001'
  const results = await Promise.all([ledger.run(id, 'same', execute), ledger.run(id, 'same', execute)])
  assert.equal(executions, 1)
  assert.deepEqual(results[0], results[1])
  assert.deepEqual(await new RequestLedger(file).run(id, 'same', execute), results[0])
  await assert.rejects(ledger.run(id, 'changed', execute), { status: 409 })
  fs.writeFileSync(path.join(work, 'pending.json'), JSON.stringify({ 'phone-uncertain-0001': { hash: ledger.entries[id].hash } }))
  await assert.rejects(new RequestLedger(path.join(work, 'pending.json')).run('phone-uncertain-0001', 'same', execute), { status: 409 })
  assert.equal(executions, 1, 'uncertain admissions never dispatch again')
  console.log('PASS concurrent retries, durable receipts, payload mismatch and uncertain restart')

  hub = await startPrivateHub()
  const call = async (route, body, requestId, publicDoor = false) => {
    const r = await fetch(`http://127.0.0.1:${publicDoor ? hub.cfg.publicPort : hub.cfg.port}${route}`, {
      method: body ? 'POST' : 'GET', headers: {
        authorization: `Bearer ${publicDoor ? hub.cfg.watchToken : hub.cfg.token}`,
        'content-type': 'application/json', ...(requestId ? { 'X-Chums-Request-Id': requestId } : {})
      }, body: body ? JSON.stringify(body) : undefined
    })
    return { status: r.status, body: await r.json() }
  }
  assert.equal((await call('/v1/state?since=0')).body.phoneRequestReceipts, true)
  await call('/channel/register', { sessionId: 'phone-session', name: 'ray', cwd: work })
  const message = { text: 'Make a file', agent: 'ray', device: 'phone' }
  const receiptId = 'phone-request-00001'
  const first = await call('/v1/say', message, receiptId, true)
  assert.equal(first.body.kind, 'order')
  assert.deepEqual(await call('/v1/say', message, receiptId, true), first)
  const orders = (await call('/channel/next?sessionId=phone-session')).body.items
  assert.equal(orders.filter(i => i.type === 'order').length, 1, 'lost-response retry enqueues only one order')
  assert.equal((await call('/v1/say', { ...message, agent: 'peeps' }, receiptId, true)).status, 409)
  assert.equal((await call('/v1/say', { ...message, agent: 'removed-agent' }, 'phone-removed-00001', true)).body.kind, 'ask', 'missing explicit agent never falls through to another session')
  assert.equal((await call('/channel/next?sessionId=phone-session')).body.items.filter(i => i.type === 'order').length, 1)
  await call('/channel/permission', { sessionId: 'phone-session', request_id: 'phone-approval', tool_name: 'Bash', description: 'Write file', input_preview: 'touch result.txt' })
  const verdict = await call('/v1/verdict', { requestId: 'phone-approval', behavior: 'deny' }, undefined, true)
  assert.equal(verdict.body.kind, 'verdict')
  assert.equal(verdict.body.confirmed, true)
  assert.equal((await call('/v1/verdict', { requestId: 'phone-approval', behavior: 'allow' }, undefined, true)).body.confirmed, false, 'stale approval must not look confirmed')
  const home = hub.home
  const exited = once(hub.hub, 'exit')
  hub.stop({ keepHome: true })
  await exited
  hub = await startPrivateHub({ home })
  assert.deepEqual(await call('/v1/say', message, receiptId, true), first, 'receipt survives hub restart')
  console.log('PASS phone HTTP contract: single dispatch, agent binding, scoped verdict and receipt after restart')
} finally {
  hub?.stop()
  fs.rmSync(work, { recursive: true, force: true })
}
