# PCA-APP

PCA-APP은 Android 기기에서 Bluetooth Classic SPP 통신을 이용해 라즈베리파이 기반 주차 출차 제어 장치에 명령을 전송하는 앱입니다. 사용자는 로그인 후 좌측, 우측, 전진 출차 방향을 선택할 수 있으며, 출차 진행 중에는 취소 명령을 보낼 수 있습니다.

## 주요 기능

- 더미 계정 기반 로그인 화면 제공
- 라즈베리파이와 Bluetooth Classic SPP 연결
- 연결 실패 또는 연결 끊김 발생 시 3초 간격 자동 재연결 시도
- 출차 방향 선택 명령 전송
  - `LEFT_EXIT`
  - `RIGHT_EXIT`
  - `STRAIGHT_EXIT`
- 출차 취소 명령 전송
  - `CANCEL_EXIT`
- 라즈베리파이 응답 패킷 처리
  - `EXIT_DONE`: 출차 완료 알림 표시
  - `EXIT_CANCELED`: 출차 취소 알림 표시
- 최근 송수신 패킷 및 Bluetooth 연결 상태 표시

## 화면 구성

### 1. 로그인 화면

앱 실행 시 로그인 화면이 먼저 표시됩니다.

- 앱 이름: `PCA-APP`
- Bluetooth 연결 상태 표시
- 아이디 입력창
- 비밀번호 입력창
- 로그인 버튼

기본 더미 계정은 다음과 같습니다.

```text
ID: admin
Password: 1234
```

### 2. 메인 제어 화면

로그인 성공 후 출차 제어 화면으로 이동합니다.

- Bluetooth 연결 상태 표시
- 최근 송수신 패킷 표시
- 좌측 출차 버튼
- 우측 출차 버튼
- 전진 출차 버튼
- 출차 취소 버튼

Bluetooth가 연결되지 않은 상태에서는 출차 관련 버튼이 비활성화됩니다.

## Bluetooth 통신 정보

앱은 Bluetooth Classic SPP 방식으로 라즈베리파이에 연결합니다.

```kotlin
UUID: 00001101-0000-1000-8000-00805F9B34FB
```

현재 코드에 설정된 대상 Bluetooth MAC 주소는 다음과 같습니다.

```text
D8:3A:DD:38:66:FC
```

대상 장치를 변경하려면 `MainActivity.kt`의 `TARGET_DEVICE_MAC` 값을 수정하면 됩니다.

```kotlin
private const val TARGET_DEVICE_MAC = "D8:3A:DD:38:66:FC"
```

MAC 주소가 비어 있거나 찾지 못한 경우, 앱은 다음 이름의 페어링된 장치를 탐색합니다.

```text
PCA-RPI
admin
raspberrypi
```

## 송신 패킷

| 사용자 동작 | 전송 패킷 |
|---|---|
| 좌측 출차 선택 | `LEFT_EXIT` |
| 우측 출차 선택 | `RIGHT_EXIT` |
| 전진 출차 선택 | `STRAIGHT_EXIT` |
| 출차 취소 선택 | `CANCEL_EXIT` |

모든 패킷은 줄바꿈 문자 `\n`을 포함해 전송됩니다.

## 수신 패킷

| 수신 패킷 | 앱 동작 |
|---|---|
| `EXIT_DONE` | 출차 완료 다이얼로그 표시 |
| `EXIT_CANCELED` | 출차 취소 다이얼로그 표시 |

정의되지 않은 패킷은 현재 별도 알림 없이 무시됩니다.

## 권한

Android 버전에 따라 필요한 Bluetooth 권한이 다릅니다.

### Android 12 이상

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
```

### Android 11 이하

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

프로젝트의 `AndroidManifest.xml`에 위 권한이 선언되어 있어야 Bluetooth 연결 권한 요청이 정상적으로 동작합니다.

## 프로젝트 주요 파일

```text
MainActivity.kt
activity_main.xml
```

### MainActivity.kt

앱의 주요 로직을 담당합니다.

- 로그인 처리
- Bluetooth 권한 확인 및 요청
- 라즈베리파이 연결 및 자동 재연결
- 출차 명령 패킷 송신
- 라즈베리파이 응답 패킷 수신 및 처리
- 버튼 활성화 상태 제어

### activity_main.xml

앱의 UI 레이아웃을 정의합니다.

- 로그인 화면
- 메인 제어 화면
- Bluetooth 상태 카드
- 출차 방향 선택 카드
- 출차 취소 버튼

## 실행 전 준비 사항

1. Android 기기에서 Bluetooth를 켭니다.
2. 라즈베리파이 Bluetooth 장치와 Android 기기를 미리 페어링합니다.
3. `MainActivity.kt`의 `TARGET_DEVICE_MAC` 값이 실제 라즈베리파이 Bluetooth MAC 주소와 일치하는지 확인합니다.
4. 앱을 실행하고 Bluetooth 권한을 허용합니다.
5. 더미 계정으로 로그인합니다.

```text
admin / 1234
```

## 동작 흐름

1. 앱 실행
2. Bluetooth 권한 확인
3. 권한이 없으면 권한 요청
4. Bluetooth 활성화 여부 확인
5. 페어링된 라즈베리파이 장치 검색
6. SPP UUID를 사용해 Bluetooth Socket 연결
7. 로그인 성공 후 메인 제어 화면 표시
8. 출차 방향 선택 시 명령 패킷 전송
9. 출차 완료 또는 취소 응답 수신 시 알림 표시
10. 연결이 끊기면 자동 재연결 시도

## 개발 환경

- Language: Kotlin
- Platform: Android
- UI: XML Layout
- Communication: Bluetooth Classic SPP
- External Device: Raspberry Pi

## 참고 사항

- 현재 로그인 정보는 코드에 하드코딩된 더미 계정입니다. 실제 서비스에 사용할 경우 서버 인증 또는 안전한 로컬 인증 방식으로 변경하는 것이 좋습니다.
- Bluetooth MAC 주소도 코드에 직접 작성되어 있으므로, 배포 환경에서는 설정 화면이나 환경 설정 파일로 분리하는 것이 좋습니다.
- 라즈베리파이 측 프로그램은 앱에서 전송하는 문자열 패킷을 줄 단위로 읽고 처리해야 합니다.
- 출차 명령이 진행 중일 때는 중복 명령 전송을 막기 위해 출차 방향 버튼이 비활성화됩니다.
