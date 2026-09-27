#!/system/bin/sh
# Horizon live-data poller. Runs as root on the head unit. Parses dumpsys for
# vehicle + GPS values that the testkey app can't read directly, and writes
# /sdcard/horizon/live.json which the WebView polls. Raw SI units; the page
# converts to mph/F/ft. Missing values are simply omitted (page shows "--").
OUT=/sdcard/horizon/live.json
while true; do
  D=`dumpsys car_service 2>/dev/null`
  fv(){ echo "$D" | grep "lastEvent:Property:$1," | head -n1 | sed -n 's/.*floatValues: \[\([^]]*\)\].*/\1/p'; }
  iv(){ echo "$D" | grep "lastEvent:Property:$1," | head -n1 | sed -n 's/.*int32Values: \[\([^]]*\)\].*/\1/p'; }
  speed_ms=`fv 0x11600207`; oil_c=`fv 0x11600304`; out_c=`fv 0x11600703`; rpm=`fv 0x11600305`; fuel=`fv 0x11600307`
  fan=`iv 0x15400500`; gear=`iv 0x11400400`
  LOC=`dumpsys location 2>/dev/null | grep "gps Location\[" | head -n1`
  alt_m=`echo "$LOC" | sed -n 's/.* alt=\([-0-9.]*\).*/\1/p'`
  bear=`echo "$LOC" | sed -n 's/.* bear=\([-0-9.]*\).*/\1/p'`
  gvel=`echo "$LOC" | sed -n 's/.* vel=\([-0-9.]*\).*/\1/p'`
  {
    printf '{"_ts":%s' "`date +%s`"
    for kv in speed_ms:"$speed_ms" oil_c:"$oil_c" out_c:"$out_c" rpm:"$rpm" fuel:"$fuel" fan:"$fan" gear:"$gear" alt_m:"$alt_m" heading:"$bear" gspeed_ms:"$gvel"; do
      k=${kv%%:*}; v=${kv#*:}
      case "$v" in
        ""|*[!0-9.-]*) ;;
        *) printf ',"%s":%s' "$k" "$v";;
      esac
    done
    printf '}\n'
  } > "$OUT.tmp" 2>/dev/null && mv "$OUT.tmp" "$OUT" 2>/dev/null
  sleep 2
done
