# OBS 통합 오버레이

GitHub Pages/OBS 브라우저 소스에서 이동거리, 수익, 날씨, 휴대폰 배터리와 보조배터리 상태를 한 줄로 표시합니다.

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
```

제어판에서 보조배터리 모델·현재 잔량·소비전력을 입력하고 `계산 시작/보정`을 누르면 10초마다 예상 상태가 전송됩니다. 외부 쓰기 요청은 패널 토큰으로 보호됩니다.

실제 Firebase 주소나 인증 토큰은 이 공개 저장소에 커밋하지 마세요. Firebase 규칙은 오버레이 읽기와 송신기 쓰기 권한을 분리하는 구성을 권장합니다.

## 실제 A110A BLE 연동 상태

현재 기본 연동은 시간 기반 예상값입니다. Anker Prime A110A의 BLE 프로토콜은 비공식 역공학 구현이 있으나, 장비/펌웨어별 검증이 필요하므로 이 저장소는 특정 BLE 패키지를 프로덕션 의존성으로 고정하지 않습니다. 실측 수집기를 추가할 때도 위 JSON 스키마를 그대로 사용하면 오버레이 수정 없이 교체할 수 있습니다.
