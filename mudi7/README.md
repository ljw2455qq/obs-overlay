# Mudi 7 신호 전송기 설치

이 전송기는 GL.iNet Mudi 7(GL-E5800)의 `cellular.modem`/`cellular.network` ubus 데이터에서 실제 LTE/5G 신호를 읽어 Firebase의 `mudiSignal` 노드로 전송합니다. 관리자 비밀번호나 SIM 정보는 전송하지 않습니다.

## 1. Mudi 7에 접속

서브폰이나 PC를 Mudi 7 Wi-Fi에 연결한 뒤 SSH로 접속합니다. 기본 주소는 `192.168.8.1`입니다.

```sh
ssh root@192.168.8.1
```

## 2. 파일 설치

PC에서 저장소를 받은 폴더를 연 뒤 파일을 Mudi 7로 복사합니다.

```sh
scp mudi7/mudi-signal.sh mudi7/mudi-signal.init mudi7/mudi-signal.config.example root@192.168.8.1:/tmp/
```

그다음 Mudi 7의 SSH 터미널에서 아래 명령을 실행합니다.

```sh
cp /tmp/mudi-signal.sh /usr/bin/mudi-signal
cp /tmp/mudi-signal.init /etc/init.d/mudi-signal
cp /tmp/mudi-signal.config.example /etc/config/mudi_signal
chmod 755 /usr/bin/mudi-signal /etc/init.d/mudi-signal
```

`/etc/config/mudi_signal`의 `firebase_url`을 오버레이가 사용하는 Firebase 주소의 `mudiSignal.json` 경로로 바꿉니다. 쓰기 인증을 사용하는 경우에만 `auth_token`을 입력하세요. 토큰이 들어간 설정 파일은 GitHub에 올리지 마세요.

펌웨어에 `curl`이 없다면 한 번만 설치합니다.

```sh
opkg update
opkg install curl
```

## 3. 자동 시작

```sh
/etc/init.d/mudi-signal enable
/etc/init.d/mudi-signal restart
logread -e mudi-signal
```

정상 동작하면 Firebase `mudiSignal`에 다음 형태의 값이 5초마다 갱신됩니다.

```json
{
  "source": "mudi7",
  "connected": true,
  "rat": "5G",
  "mode": "NR5G-SA",
  "rsrp": -91,
  "rsrq": -10,
  "sinr": 18,
  "band": "n78",
  "bars": 3,
  "timestamp": 1787270400000
}
```

수동 확인 명령은 다음과 같습니다.

```sh
ubus call cellular.modem status '{"bus":"cpu"}'
ubus call cellular.network info '{"bus":"cpu","slot":1}'
```

SIM 슬롯이 2인 경우 두 번째 명령의 `slot`만 `2`로 바꾸면 됩니다. 전송기는 현재 슬롯을 자동으로 감지합니다.
