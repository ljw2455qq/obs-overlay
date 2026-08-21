#!/bin/sh

# GL.iNet Mudi 7 (GL-E5800) cellular signal -> Firebase Realtime Database

. /lib/functions.sh

CONFIG_NAME="mudi_signal"

read_config() {
  config_load "$CONFIG_NAME"
  config_get FIREBASE_URL main firebase_url ""
  config_get FIREBASE_AUTH main auth_token ""
  config_get INTERVAL main interval "5"
  case "$INTERVAL" in
    ''|*[!0-9]*) INTERVAL=5 ;;
  esac
  [ "$INTERVAL" -ge 2 ] 2>/dev/null || INTERVAL=5
}

json_value() {
  printf '%s' "$1" | jsonfilter -e "$2" 2>/dev/null | sed -n '1p'
}

json_number() {
  if printf '%s' "$1" | grep -Eq '^-?[0-9]+([.][0-9]+)?$'; then
    printf '%s' "$1"
  else
    printf 'null'
  fi
}

json_text() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g' | tr -d '\r\n'
}

firebase_put() {
  payload="$1"
  endpoint="$FIREBASE_URL"
  [ -n "$FIREBASE_AUTH" ] && endpoint="${endpoint}?auth=${FIREBASE_AUTH}"
  curl --silent --show-error --fail --max-time 10 \
    --request PUT --header 'Content-Type: application/json' \
    --data "$payload" "$endpoint" >/dev/null
}

bars_from_rsrp() {
  rsrp="$1"
  if [ "$rsrp" -ge -80 ] 2>/dev/null; then echo 4
  elif [ "$rsrp" -ge -95 ] 2>/dev/null; then echo 3
  elif [ "$rsrp" -ge -105 ] 2>/dev/null; then echo 2
  elif [ "$rsrp" -ge -115 ] 2>/dev/null; then echo 1
  else echo 0
  fi
}

send_offline() {
  firebase_put '{"source":"mudi7","connected":false,"timestamp":{".sv":"timestamp"}}'
}

send_signal() {
  modem_json="$(ubus call cellular.modem status '{\"bus\":\"cpu\"}' 2>/dev/null)"
  slot="$(json_value "$modem_json" '@.current_sim_slot')"
  case "$slot" in ''|*[!0-9]*) slot=1 ;; esac

  network_json="$(ubus call cellular.network info "{\"bus\":\"cpu\",\"slot\":${slot}}" 2>/dev/null)"
  mode=''
  cell_index=''
  for index in 0 1 2 3; do
    candidate="$(json_value "$network_json" "@.networks[$index].cell_info.mode")"
    if [ -n "$candidate" ]; then
      mode="$candidate"
      cell_index="$index"
      break
    fi
  done

  if [ -z "$cell_index" ]; then
    send_offline
    return
  fi

  rsrp="$(json_value "$network_json" "@.networks[$cell_index].cell_info.rsrp")"
  rsrq="$(json_value "$network_json" "@.networks[$cell_index].cell_info.rsrq")"
  sinr="$(json_value "$network_json" "@.networks[$cell_index].cell_info.sinr")"
  band="$(json_value "$network_json" "@.networks[$cell_index].cell_info.band")"
  bars="$(bars_from_rsrp "$rsrp")"
  case "$(printf '%s' "$mode" | tr '[:lower:]' '[:upper:]')" in
    *NR*|*5G*) rat='5G' ;;
    *) rat='LTE' ;;
  esac
  payload="{\"source\":\"mudi7\",\"connected\":true,\"rat\":\"${rat}\",\"mode\":\"$(json_text "$mode")\",\"rsrp\":$(json_number "$rsrp"),\"rsrq\":$(json_number "$rsrq"),\"sinr\":$(json_number "$sinr"),\"band\":\"$(json_text "$band")\",\"bars\":${bars},\"timestamp\":{\".sv\":\"timestamp\"}}"
  firebase_put "$payload"
}

read_config
if [ -z "$FIREBASE_URL" ]; then
  logger -t mudi-signal 'firebase_url is not configured'
  exit 1
fi
command -v curl >/dev/null 2>&1 || {
  logger -t mudi-signal 'curl is required'
  exit 1
}

trap 'send_offline; exit 0' INT TERM
while true; do
  send_signal || logger -t mudi-signal 'Firebase update failed'
  sleep "$INTERVAL"
done
