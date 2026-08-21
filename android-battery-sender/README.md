# 오버레이 배터리 송신기

삼성 인터넷의 백그라운드 탭 대신 Android 포그라운드 서비스가 서브폰 배터리 잔량을 Firebase Realtime Database `battery` 노드로 전송합니다.

## 설치

GitHub Actions의 `Android battery sender APK` 실행 결과에서 `BatteryOverlaySender-debug` 아티팩트를 내려받아 압축을 풀고 `app-debug.apk`를 서브폰에 설치합니다. Android의 "출처를 알 수 없는 앱 설치" 허용이 필요할 수 있습니다.

## 최초 설정

1. 앱을 실행하고 알림 권한을 허용합니다.
2. 오버레이에서 사용하는 Firebase Realtime Database 기본 주소를 입력합니다. 예: `https://YOUR_PROJECT-default-rtdb.firebaseio.com`
3. Firebase 쓰기 인증이 설정되어 있을 때만 인증 토큰을 입력합니다.
4. `저장하고 전송 시작`을 누릅니다.
5. `배터리 최적화 설정 열기`에서 배터리 사용을 `제한 없음`으로 설정합니다.
6. 삼성 설정의 `배터리 → 백그라운드 사용 제한 → 절전 예외 앱`에도 이 앱을 추가합니다.

실행 중에는 `배터리 오버레이 전송 중` 알림이 유지됩니다. 방송이 끝나면 앱이나 알림에서 `중지`를 누르세요.

## 전송 데이터

앱은 Firebase REST API로 다음 형태를 전송합니다. `timestamp`는 서브폰 시계가 아니라 Firebase 서버 시간으로 기록됩니다.

```json
{
  "source": "android-foreground-service",
  "level": 48,
  "charging": false,
  "connected": true,
  "timestamp": 1787270400000
}
```

인증 토큰은 소스 코드나 APK에 하드코딩되지 않으며, 사용자가 입력한 값은 Android Keystore 키로 암호화됩니다.

## 개발 빌드

JDK 17, Android SDK 35와 Gradle 8.9가 필요합니다.

```sh
gradle testDebugUnitTest lintDebug assembleDebug
```

생성 파일은 `app/build/outputs/apk/debug/app-debug.apk`입니다.
