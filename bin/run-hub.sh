#!/usr/bin/env bash
# Runs the hub in the foreground with its log in ~/.chums/hub.log (for a systemd unit or a terminal on Linux).
mkdir -p "$HOME/.chums"
exec node "$(cd "$(dirname "$0")/.." && pwd)/hub/hub.mjs" >> "$HOME/.chums/hub.log" 2>&1
