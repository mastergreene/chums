# Universal Chums implementation plan

This branch is a first implementation slice, not the finished platform. Keep the original Apple and Codex clients working.

## Product model
Garfield is the principal/default agent. Patch (coding), pet monitoring, Claude Code, and Codex are independent agents, each with an isolated session, status, activity, permissions, and notifications. Claude's consumer chat, Claude API and Claude Code are different products and must not be treated as interchangeable connections.

A character skin (Garfield, Peeps, Squint, Ray) is independent of agent identity. Switching skins must not change the selected backend, agent ID, thread, or working directory.

## Existing vs needed
- Existing: Android Compose app; Hub with session, approvals and voice; Claude Code hooks and Codex app-server adapters; Garfield rig; a custom OpenAI-compatible HermesRunner.
- This branch: independently selectable native Garfield, Peeps, Squint and Ray faces; Peeps/Squint are initial Compose interpretations, not exact original Swift ports.
- Needed: live Hermes Agent integration, proper roster, full artifact parity, Android device QA, Wear OS client, custom pendant client.
- Important: the custom HermesRunner is NOT the user's actual Hermes Agent install. Preserve it only as a labelled standalone fallback; never pretend its short in-memory session is the native Hermes memory.

## Phase 0 — security
Historically, an Android watch token was committed. Rotate any deployed affected watch token at the actual host. Current source changes cannot revoke an existing token. Audit device exposure, TLS, approval scoping, credential revocation and upload controls. Do not commit secrets. Acceptance: revoked token fails and agents cannot approve each other's actions.

## Phase 1 — Android stabilization
Run Gradle debug/release compile, lint, unit and instrumentation tests. Test physical Android device speech capture/recognition/playback, repeated replies, rotation, permissions, UI insets, reconnection, agent switching and separate approval cards. Include offline and Tailscale/cellular trials. None are marked passing until actually executed.

## Phase 2 — native Hermes transport
Discover the existing Hermes Agent's supported local API/CLI transport and documented authentication. Implement an adapter preserving existing named bots (Garfield, Patch, pet monitor), real session IDs, memory, skills, tool states and policy. Do not invent an endpoint or replace native memory with an LLM chat history. Handle session recovery, event pagination, reconnect, progress, and per-call approvals. Acceptance: native Hermes and Chums show the same agent turn and tool outcome.

## Phase 3 — multi-agent roster
Create a server-authoritative registry: id, displayName, backend, connection, upstreamAgentId, character, isDefault, capabilities, status, permissionPolicy. Add an Android list/card UI rather than tap-to-cycle only. Preserve distinct Claude Code and Codex runtime integrations. Give Garfield default focus and optional explicit delegation to other agents, not silent session switching. Agent-specific notifications and approvals are mandatory.

## Phase 4 — upstream parity
Compare each feature with original Chums: conversations, tools, approvals, artifacts (images, HTML, PDF, video, files), voice, character states, settings, notifications and offline handling. Create evidence-based pass/partial/missing tests. Do not mark visual animations equivalent until compared to upstream rigs.

## Phase 5 — wearable protocol
Define versioned device events for idle, sleep, listening, thinking, working, speaking, approval, blocked, reconnect. Keep a shared schema and reusable assets while each platform gets native rendering. Build a thin Wear OS client before custom pendant hardware; evaluate BLE phone relay vs direct authenticated Wi-Fi. Constrain secrets and tool permissions on wearable clients and test battery usage.

## Acceptance gate
A demonstrable end-to-end journey: select Garfield, speak from Android, watch real upstream status and tools, receive a response, approve a scoped action, switch to Patch without cross-contamination, change Peeps/Squint visual skin without changing agent, and reconnect without duplicating history. Then repeat with Claude Code and Codex before claiming complete parity.
