#!/usr/bin/env bash
# Speisegeist Deployment Validator
# Aufruf: bash validate.sh https://deine-domain.myfritz.net
set -euo pipefail

BASE_URL="${1:-http://localhost}"
PASS=0; FAIL=0

ok()   { echo "  [OK]  $1"; ((PASS++)); }
fail() { echo "  [FAIL] $1"; ((FAIL++)); }

echo ""
echo "=== Speisegeist Deployment Validator ==="
echo "    URL: $BASE_URL"
echo ""

# 1. Frontend erreichbar
echo "--- Frontend ---"
STATUS=$(curl -sfo /dev/null -w "%{http_code}" "$BASE_URL/" || echo "000")
[ "$STATUS" = "200" ] && ok "GET / → 200" || fail "GET / → $STATUS"

# 2. Angular-App ausgeliefert (enthält <app-root>)
BODY=$(curl -sf "$BASE_URL/" 2>/dev/null || echo "")
echo "$BODY" | grep -q "app-root" && ok "SPA HTML enthält <app-root>" || fail "SPA HTML enthält kein <app-root>"

# 3. API erreichbar
echo ""
echo "--- Backend API ---"
STATUS=$(curl -sfo /dev/null -w "%{http_code}" "$BASE_URL/api/recipes" || echo "000")
[ "$STATUS" = "200" ] || [ "$STATUS" = "401" ] && ok "GET /api/recipes → $STATUS (API antwortet)" || fail "GET /api/recipes → $STATUS"

# 4. Auth-Endpoint vorhanden
STATUS=$(curl -sfo /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"x","password":"x"}' || echo "000")
[ "$STATUS" = "401" ] || [ "$STATUS" = "403" ] && ok "POST /api/auth/login → $STATUS (Auth aktiv)" || fail "POST /api/auth/login → $STATUS"

# 5. Actuator Health (intern, über direkte IP + Port 9090)
echo ""
echo "--- Tipp ---"
echo "  Backend Healthcheck intern: curl http://<NAS-IP>:9090/actuator/health"
echo "  (Port 9090 nicht von außen erreichbar — nur im Docker-Netzwerk)"

echo ""
echo "=== Ergebnis: $PASS OK, $FAIL FEHLER ==="
[ "$FAIL" -eq 0 ] && echo "    Deployment erfolgreich!" && exit 0 || echo "    Bitte oben genannte Fehler beheben." && exit 1
