#!/usr/bin/env bash
set -euo pipefail

API="${API:-http://localhost:8080}"
CLIENT="client:password"

json() { perl -MJSON::PP -e 'local $/; my $d = JSON::PP->new->utf8->decode(<STDIN>); my @p = split /\./, $ARGV[0]; for (@p) { $d = ref $d eq "ARRAY" ? $d->[$_] : $d->{$_} } print defined $d ? (ref $d ? JSON::PP->new->utf8->encode($d) : $d) : ""' "$1"; }

company_id() {
  curl -s -u "$CLIENT" "$API/api/companies?page=0&size=100" |
    perl -MJSON::PP -e 'local $/; my $d = JSON::PP->new->utf8->decode(<STDIN>); my $l = ref $d eq "ARRAY" ? $d : $d->{items}; for (@$l) { print $_->{id} if $_->{inn} eq $ARGV[0] }' "$1"
}

product_id() {
  curl -s "$API/api/products?page=0&size=100" |
    perl -MJSON::PP -CSA -e 'local $/; my $d = JSON::PP->new->decode(<STDIN>); my $l = ref $d eq "ARRAY" ? $d : $d->{items}; for (@$l) { print $_->{id} if $_->{name} eq $ARGV[0] }' "$1"
}

run() {
  local inn="$1" product="$2" amount="$3" term="$4" finish="${5:-}" purpose="Пополнение оборотных средств"
  local cid pid app id status
  cid=$(company_id "$inn")
  pid=$(product_id "$product")
  app=$(printf '{"companyId":"%s","productId":"%s","requestedAmount":%s,"requestedTermMonths":%s,"purpose":"%s","hasCollateral":false}' \
      "$cid" "$pid" "$amount" "$term" "$purpose" |
    curl -s -u "$CLIENT" -H "Content-Type: application/json; charset=utf-8" -H "Idempotency-Key: demo-$inn-$(date +%s%N)" \
      --data-binary @- "$API/api/applications")
  id=$(echo "$app" | json id)
  curl -s -u "$CLIENT" -X POST -H "Content-Type: application/json" -d '{}' "$API/api/applications/$id/consents" > /dev/null
  curl -s -u "$CLIENT" -X POST "$API/api/applications/$id/submit" > /dev/null
  for _ in $(seq 1 40); do
    status=$(curl -s -u "$CLIENT" "$API/api/applications/$id" | json status)
    case "$status" in ENRICHMENT|SCORING|DECISION|SUBMITTED) sleep 2 ;; *) break ;; esac
  done
  if [ "$finish" = "sign" ] && { [ "$status" = "APPROVED" ] || [ "$status" = "COUNTER_OFFER" ]; }; then
    curl -s -u "$CLIENT" -X POST -H "Content-Type: application/json" -d '{}' "$API/api/applications/$id/accept-offer" > /dev/null
    curl -s -u "$CLIENT" -X POST "$API/api/applications/$id/sign" > /dev/null
    status=$(curl -s -u "$CLIENT" "$API/api/applications/$id" | json status)
  fi
  local score
  score=$(curl -s -u "$CLIENT" "$API/api/applications/$id/score" | json score)
  printf '%-13s %-24s %10s %3s мес.  %-14s балл %s\n' "$inn" "$product" "$amount" "$term" "$status" "${score:--}"
  sleep 8
}

curl -s -X POST http://localhost:8089/__admin/scenarios/reset > /dev/null || true

run 7704983159   "Оборотный кредит" 10000000 24 sign
run 7703168426   "Оборотный кредит"  5000000 24
run 7729501760   "Оборотный кредит"  3000000 24
run 772584916321 "Овердрафт"         1500000 12
run 7703849263   "Оборотный кредит" 12000000 24
run 7709518363   "Оборотный кредит"  3000000 24
run 501274839072 "Оборотный кредит"  1000000 12
run 7715834021   "Оборотный кредит"  1500000 12
run 7720496138   "Оборотный кредит"  5000000 24
run 5047182637   "Оборотный кредит"  8000000 24 sign
run 7722085341   "Оборотный кредит"  5000000 24
run 7731497609   "Оборотный кредит"  3000000 24
run 7736150978   "Оборотный кредит"  1000000 12
