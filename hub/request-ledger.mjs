import fs from 'node:fs'
import crypto from 'node:crypto'

// Optional phone request receipts. Persist admission before dispatch: after a crash an
// unfinished request is uncertain, never automatically dispatched a second time.
export class RequestLedger {
  constructor(file) {
    this.file = file
    this.entries = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : {}
    this.running = new Map()
  }
  save() {
    const temp = `${this.file}.tmp`
    fs.writeFileSync(temp, JSON.stringify(this.entries), { mode: 0o600 })
    fs.renameSync(temp, this.file)
  }
  async run(id, payload, work) {
    if (!id) return work() // older watch/phone clients keep their existing protocol
    if (!/^[a-zA-Z0-9-]{16,80}$/.test(id)) throw Object.assign(new Error('Invalid request ID'), { status: 400 })
    const hash = crypto.createHash('sha256').update(payload).digest('hex')
    const old = this.entries[id]
    if (old) {
      if (old.hash !== hash) throw Object.assign(new Error('Request ID was already used for a different message'), { status: 409 })
      if (this.running.has(id)) return this.running.get(id)
      if (old.done) return old.result
      throw Object.assign(new Error('Delivery is uncertain after a hub restart. Check the agent conversation before sending again.'), { status: 409 })
    }
    // Refuse a full ledger rather than forgetting old IDs and permitting duplicate work.
    if (Object.keys(this.entries).length >= 10000) throw Object.assign(new Error('Message receipt storage is full. Check the hub.'), { status: 503 })
    this.entries[id] = { hash }
    this.save()
    const job = Promise.resolve().then(work).then(result => {
      this.entries[id] = { hash, done: true, result }
      this.save()
      return result
    }).finally(() => this.running.delete(id))
    this.running.set(id, job)
    return job
  }
}
