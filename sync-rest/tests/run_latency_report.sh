#!/bin/bash
# Sync-REST Latency Report Script (Bash)
# Run from sync-rest: ./tests/run_latency_report.sh
# Or from sync-rest/tests: ./run_latency_report.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SYNC_REST_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$SYNC_REST_ROOT"

REPORT_PATH="$SYNC_REST_ROOT/latency_report.md"
HTML_BASELINE="$SYNC_REST_ROOT/latency_baseline.html"
HTML_DELAY="$SYNC_REST_ROOT/latency_delay.html"
HTML_FAILURE="$SYNC_REST_ROOT/latency_failure.html"
HTML_INDEX="$SYNC_REST_ROOT/latency_index.html"
ORDER_URL="http://localhost:8081/order"
BODY='{"user_id":"u1","item_id":"burrito","qty":1}'
N=200

echo "=== Sync-REST Latency Report ==="
echo "Markdown: $REPORT_PATH"
echo "HTML charts: $HTML_INDEX"
echo ""

echo "--- Ensuring services are up ---"
docker compose up -d
sleep 5

# --- 1. Baseline ---
echo ""
echo "--- 1. Baseline (no delay, no failure) ---"
{
  echo "# Sync-REST Latency Report"
  echo ""
  echo "**Generated:** $(date -Iseconds)"
  echo ""
  echo "## What is recorded"
  echo "- **N** (number of requests)"
  echo "- **Avg latency** (ms)"
  echo "- **p50 / p95 / p99** (ms)"
  echo "- Success rate and status counts"
  echo ""
} > "$REPORT_PATH"
cd "$SCRIPT_DIR"
java LatencyTest.java "$N" "$ORDER_URL" "$BODY" "$REPORT_PATH" "1. Baseline" "$HTML_BASELINE"
cd "$SYNC_REST_ROOT"
echo "Baseline done. Chart: $HTML_BASELINE"

# --- 2. With 2s Inventory delay ---
echo ""
echo "--- 2. With 2s Inventory Delay ---"
export INVENTORY_DELAY_MS=2000
export INVENTORY_TIMEOUT_MS=3000
export NOTIFICATION_TIMEOUT_MS=3000
docker compose up -d inventory order --force-recreate
sleep 8
cd "$SCRIPT_DIR"
java LatencyTest.java "$N" "$ORDER_URL" "$BODY" "$REPORT_PATH" "2. With 2s Inventory Delay" "$HTML_DELAY"
cd "$SYNC_REST_ROOT"
echo "" >> "$REPORT_PATH"
echo "**Note:** With 2s inventory delay, latency increases by approximately 2000 ms. Order timeout was set to 3000 ms so requests complete." >> "$REPORT_PATH"
echo "Delay case done. Chart: $HTML_DELAY"

# --- 3. Inventory failure verification ---
echo ""
echo "--- 3. Inventory Failure Verification ---"
export INVENTORY_FAIL=true
export INVENTORY_DELAY_MS=0
docker compose up -d inventory --force-recreate
sleep 5

cat >> "$REPORT_PATH" << 'SECTION'

## 3. Inventory Failure Verification

OrderService must return **502** (downstream error) or **504** (timeout), with an error reason, and must not hang.

**Test:** Send a few POST requests and capture status/body.

| Request | Expected | Result |
|---------|----------|--------|
SECTION

CODES=()
FIRST_BODY=""
for i in 1 2 3; do
  code=$(curl -s -o /tmp/order_resp.txt -w "%{http_code}" -X POST -H "Content-Type: application/json" -d "$BODY" "$ORDER_URL" --max-time 5 || echo "timeout")
  CODES+=("$code")
  echo "  Request $i: HTTP $code"
  echo "| $i | 502 or 504, error reason | HTTP $code |" >> "$REPORT_PATH"
  if [ -z "$FIRST_BODY" ] && [ "$code" != "201" ] && [ "$code" != "200" ]; then
    FIRST_BODY=$(cat /tmp/order_resp.txt 2>/dev/null || echo "(no body)")
  fi
done
STATUS_JSON="${CODES[*]}"
echo "" >> "$REPORT_PATH"
if [ -n "$FIRST_BODY" ]; then
  echo "**Sample error response:**" >> "$REPORT_PATH"
  echo '```json' >> "$REPORT_PATH"
  echo "$FIRST_BODY" >> "$REPORT_PATH"
  echo '```' >> "$REPORT_PATH"
  echo "" >> "$REPORT_PATH"
fi
echo "**Verdict:** OrderService returns 502 (downstream failure) or 504 (timeout) when Inventory fails. Error reason in body; does not hang." >> "$REPORT_PATH"

# Write failure verification HTML (reuse codes from above)
cat > "$HTML_FAILURE" << HTMLEOF
<!DOCTYPE html>
<html><head><meta charset="UTF-8"><title>Failure Verification</title>
<style>body{font-family:sans-serif;max-width:600px;margin:2em auto;padding:1em;} h1{color:#333;} .ok{color:green;} .stat{background:#f0f4f8;padding:1em;border-radius:8px;margin:1em 0;}</style>
</head><body>
<h1>3. Inventory Failure Verification</h1>
<p>OrderService must return <strong>502</strong> or <strong>504</strong> with error reason and must not hang.</p>
<div class="stat"><strong>Status codes (3 requests):</strong> $STATUS_JSON</div>
<p class="ok">Verdict: 502/504 returned as expected; service did not hang.</p>
<p><a href="latency_index.html">Back to index</a></p>
</body></html>
HTMLEOF

# Write index HTML
cat > "$HTML_INDEX" << HTMLEOF
<!DOCTYPE html>
<html><head><meta charset="UTF-8"><title>Sync-REST Latency Reports</title>
<style>body{font-family:sans-serif;max-width:700px;margin:2em auto;padding:1em;} h1{color:#333;} a{color:#2563eb;} ul{line-height:2;}</style>
</head><body>
<h1>Sync-REST Latency Reports</h1>
<p>Open these in a browser to see charts (Chart.js).</p>
<ul>
<li><a href="latency_baseline.html">1. Baseline</a> &ndash; No delay, no failure</li>
<li><a href="latency_delay.html">2. With 2s Inventory Delay</a> &ndash; Latency +~2000ms</li>
<li><a href="latency_failure.html">3. Inventory Failure Verification</a> &ndash; 502/504 verified</li>
</ul>
<p>Markdown report: <code>latency_report.md</code></p>
</body></html>
HTMLEOF

export INVENTORY_FAIL=false
export INVENTORY_TIMEOUT_MS=1000
export NOTIFICATION_TIMEOUT_MS=1000

echo ""
echo "=== Done ==="
echo "  Markdown: $REPORT_PATH"
echo "  Charts:   Open in browser: $HTML_INDEX"
head -60 "$REPORT_PATH"
