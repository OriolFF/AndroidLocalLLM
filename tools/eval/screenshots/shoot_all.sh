#!/bin/bash
# usage: shoot_all.sh <outDir> <puppeteer node_modules parent> id...   (one process per page, 150 s watchdog each)
out=$1; nm=$2; shift 2
for id in "$@"; do
  PUPPETEER_NODE_MODULES=$nm perl -e 'alarm 150; exec @ARGV' node "$(dirname "$0")/shoot.mjs" "$out" "$id" || echo "$id FAILED/timeout"
done
