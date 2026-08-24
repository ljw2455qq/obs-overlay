# OBS 통합 오버레이

GitHub Pages/OBS 브라우저 소스에서 이동거리, 수익, 날씨, 휴대폰 배터리, Mudi 7 LTE/5G 신호·배터리와 보조배터리 상태를 한 줄로 표시합니다.

## Mudi 7 LTE/5G 신호 표시

오버레이는 Firebase Realtime Database의 `mudiSignal` 노드를 읽습니다. 기존 RealtimeIRL 갱신 간격 기반 신호 막대 대신 Mudi 7이 측정한 실제 셀룰러 `RSRP`로 막대 수를 계산하고, `LTE`/`5G`를 함께 표시합니다. 같은 전송값의 실제 라우터 배터리를 보조배터리 `P` 옆에 `M`으로 표시하며, 마우스를 올리면 충전 상태를 확인할 수 있습니다.

Mudi 7에서 상시 전송기를 설치하는 방법은 [`mudi7/README.md`](mudi7/README.md)를 따르세요. 전송기가 라우터에서 직접 실행되므로 서브폰의 Chrome이나 IRL 방송 시스템 화면을 닫아도 계속 갱신됩니다.

메인 IRL 제어판에서 수동으로 입력한 배터리값은 Firebase의 `mudiBatteryManual` 노드에 저장됩니다. `enabled: true`인 동안에는 수동 배터리만 자동 `mudiSignal` 배터리보다 우선하며, LTE/5G 신호는 항상 라우터 자동값을 유지합니다. `사용 중`으로 전송하면 입력한 `100% → 0% 예상시간`과 전송 시각을 기준으로 GitHub 오버레이가 잔량을 직접 계산하므로 IRL 서버가 꺼져도 계속 감소합니다. 서버를 다시 실행하면 Firebase의 마지막 상태를 현재 시각까지 진행시켜 자동 복원합니다. 제어판에서 **라우터 자동값**을 누르면 `enabled: false`가 저장되어 자동 배터리값으로 돌아갑니다.

```json
{"enabled":true,"batteryLevel":82,"batteryCharging":false,"connected":true,"batteryConnected":true,"fullRuntimeHours":13.5,"estimated":true,"running":true,"source":"irl-manual","timestamp":1787529600000}
```

- 자동 `mudiSignal`은 15초 동안 갱신되지 않으면 회색으로 표시합니다.
- 자동 `mudiSignal`은 60초가 지나면 `OFFLINE`으로 표시합니다.
- `index.html?demo=1`에서는 `5G` 3칸 신호와 MUDI7 배터리 82%를 미리 볼 수 있습니다.

## 보조배터리 표시

오버레이는 Firebase Realtime Database의 `ankerBattery` 노드를 읽습니다.

```json
{
  "model": "A1695",
  "level": 73.2,
  "inputWatts": 0,
  "outputWatts": 7.5,
  "temperature": null,
  "remainingSeconds": 35100,
  "connected": true,
  "running": true,
  "estimated": true,
  "source": "irl-estimator",
  "timestamp": 1787184000000
}
```

- `estimated: true`이면 온도 대신 예상 남은 시간을 표시합니다.
- 실제 BLE 수집값은 `estimated: false`와 `temperature`를 보내면 온도를 표시합니다.
- 30초 동안 갱신되지 않으면 흐리게, 60초가 지나면 `OFFLINE`으로 표시합니다.
- `index.html?demo=1`로 OBS에 넣기 전 디자인을 확인할 수 있습니다.

## C:\\irl 연동

IRL 통합 제어 서버의 `.env`에 아래 값을 넣고 서버를 재시작합니다.

```dotenv
POWER_BANK_OVERLAY_FIREBASE_URL=https://YOUR_PROJECT-default-rtdb.firebaseio.com/ankerBattery.json
POWER_BANK_OVERLAY_FIREBASE_AUTH_TOKEN=
# 선택: 비워 두면 같은 DB의 mudiBatteryManual 노드를 자동 사용합니다.
MUDI_BATTERY_OVERLAY_FIREBASE_URL=
```

제어판에서 보조배터리 모델·현재 잔량·소비전력을 입력하고 `계산 시작/보정`을 누르면 10초마다 예상 상태가 전송됩니다. 외부 쓰기 요청은 패널 토큰으로 보호됩니다.

실제 Firebase 주소나 인증 토큰은 이 공개 저장소에 커밋하지 마세요. Firebase 규칙은 오버레이 읽기와 송신기 쓰기 권한을 분리하는 구성을 권장합니다.

## 실제 A110A BLE 연동 상태

현재 기본 연동은 시간 기반 예상값입니다. Anker Prime A110A의 BLE 프로토콜은 비공식 역공학 구현이 있으나, 장비/펌웨어별 검증이 필요하므로 이 저장소는 특정 BLE 패키지를 프로덕션 의존성으로 고정하지 않습니다. 실측 수집기를 추가할 때도 위 JSON 스키마를 그대로 사용하면 오버레이 수정 없이 교체할 수 있습니다.
