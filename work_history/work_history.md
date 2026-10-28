# 페어웨이 소풍 (Fairway Picnic / acadeGame) 작업 히스토리

본 문서는 프로젝트(`acadeGame`)의 연속성을 유지하기 위해 단계별 구현 내역과 검증 결과를 누적(Append)하여 기록하는 문서입니다. **(기존 기록을 덮어쓰지 않고 항상 하단에 이어서 추가할 것)**

- **최초 작성 일시**: 2026-10-29
- **대상 패키지**: `com.herosonsa.fairwaypicnic` (네임스페이스 `com.example.fairwaypicnic`)
- **기술 스택**: Kotlin 2.3, Jetpack Compose Material 3, Android SDK 36 (`minSdk = 26`), `AudioTrack` 16-bit PCM 파형 합성 사운드 엔진 (`GhibliSoundEngine`), `VibrationEffect` 햅틱 연동

---

## [2026-10-29] Phase 1: 지브리 수채화 감성 골프 라운딩 아케이드 미니게임 7종 초기 설계 및 v1.0.0 구축

### 1. 기획 및 게임 구성 (총 7종)
- **1홀 티샷 순서 정하기 (3종)**
  1. **드라이버 티 바운스 (`TeeBounceGameScreen.kt`)**: 원형으로 둘러선 동반자 중앙의 드라이버 헤드 페이스에 나무 티를 튕겨 잔디에 떨어뜨립니다. 티의 뾰족한 끝이 가리킨 사람이 1번 오너가 되고 오른쪽(시계방향) 순서대로 2·3·4번 타순이 확정됩니다.
  2. **나무 홈 뽑기 막대 (`NotchStickGameScreen.kt`)**: 가죽 통에 꽂힌 나무 막대를 뽑아 아랫부분에 파인 홈 개수(1~4개)로 타순을 정합니다. 막대가 통 밖으로 올라오는 결정적 순간에 슬로우모션이 발동합니다.
  3. **숲속 오솔길 사다리 (`LadderGameScreen.kt`)**: 동반자 골프공이 통나무 다리 갈림길을 따라 홀컵으로 내려가며 타순 결정 모드와 커피 1명 당첨 모드를 모두 지원합니다.
- **커피 사기 · 꼴등 · 팀 정하기 복불복 (4종)**
  4. **니어핀 퍼팅 치킨게임 (`NearPinPuttingGameScreen.kt`)**: 퍼터를 당겨 힘을 조절해 홀컵으로 공을 굴립니다. 홀컵 뒤 연못 해저드에 빠지거나 홀컵에서 가장 먼 꼴등이 커피 당첨자가 됩니다.
  5. **숲속 골프공 바구니 (`ForestBallBasketGameScreen.kt`)**: 버드나무 바구니 속 9개의 공 중 황금 도토리 도장이 찍힌 당첨 볼 하나를 피하는 러시안 룰렛 방식 복불복입니다.
  6. **언덕 위 풍차 룰렛 (`WindmillRouletteGameScreen.kt`)**: 클럽하우스 나무 풍차 바퀴를 돌려 커피·간식·캐디피 끝전 당첨자를 뽑으며 정지 직전 바늘이 황동 못을 넘나드는 슬로우모션이 적용됩니다.
  7. **행운의 나무 패 뒤집기 (`LuckyWoodTileGameScreen.kt`)**: 섞인 나무 패를 골라 동시에 뒤집어 커피 당첨자를 뽑거나 청솔 A팀·단풍 B팀(2대2 스킨스)으로 편을 나눕니다.

### 2. 지브리풍 아트워크 및 클라이맥스 연출 구현
- 따뜻한 수채화 질감의 배경 일러스트(`bg_fairway_meadow.jpg`, `bg_wood_table.jpg`)와 4인 골퍼 캐릭터 아바타(`avatar_golfer_1~4.jpg`)를 제작해 적용했습니다.
- 결과 직전 구간에서 `0.22x` 감속 슬로우모션 비네팅 오버레이와 심장박동(`두근두근`) 파동 애니메이션 및 진동(`VibrationEffect`)이 함께 동작하도록 `GhibliVisuals.kt`를 구현하고 `FairwayPicnic-v1.0.0-release.apk` 빌드를 완료했습니다.

---

## [2026-10-29] Phase 2: 순위 배지 6색 분리, 전 게임 공통 선수입장·2클릭 이름 조합기, 진행시간 설정 및 사운드 고도화 (v1.1.0)

### 1. 결과 순위 배지 색상 완전 분리 (`Color.kt`, `GhibliVisuals.kt`)
- 결과 모달의 1위 강조색과 참가자 고유색이 겹치던 문제를 해결하기 위해 순위 전용 6색 상호 배타 팔레트(`RankBadgeColors`: `#B85233`, `#2E6F4E`, `#356B96`, `#9A6B24`, `#6B5087`, `#2F7678`)와 `distinctRankBadgeColor(rankIndex)`를 도입해 1위부터 6위까지 모든 배지와 아바타 테두리가 서로 다른 색상으로 표시되도록 개선했습니다.

### 2. 전 게임 공통 `선수입장` 게이트 및 2클릭 성씨·호칭 조합기 (`GameModels.kt`, `PlayerPreferencesStore.kt`, `GhibliVisuals.kt`, `MainActivity.kt`)
- **3단 명단 프리셋 지원**: 기본값을 `1번캐디백~4번캐디백`으로 설정하고 두 번째 프리셋 `1번참가자~4번참가자` 및 사용자 지정 명단 프리셋을 원터치로 전환할 수 있도록 구성했습니다.
- **2클릭 성씨 + 호칭 자동 결합 (`KoreanGolfNameHelper`)**:
  - 1단계에서 다빈도 성씨 11종(`김씨, 박씨, 이씨, 정씨, 최씨, 강씨, 조씨, 윤씨, 장씨, 임씨, 한씨`)을 누르면 `김씨`로 입력됩니다.
  - 2단계에서 호칭 6종(`프로, 사장, 회장, 선배, 선수, 대표`)을 누르면 앞선 성씨에서 `'씨'`를 자동 제거하고 결합해 `김프로`로 표기하며 이미 호칭이 붙은 상태에서 다른 호칭을 눌러도 `김회장`으로 즉시 교체됩니다(직접 텍스트 입력 병행 지원).
- **전 게임 공통 `선수입장` 연동**: 어떤 미니게임을 선택하든 시작 직전 `선수입장` 모달이 먼저 열려 명단과 진행 시간을 확인·변경할 수 있고 다른 게임으로 이동해도 설정된 선수 명단이 그대로 이어집니다.

### 3. 게임 진행 시간(`5초 · 10초 · 15초`) 설정 기능
- 게임 시작부터 최종 결과 발표까지의 진행 시간을 `5초`(빠름), `10초`(표준 기본값), `15초`(긴장감 강조) 중 선택할 수 있게 하고 7종 모든 미니게임의 물리·애니메이션 타이밍에 연동했습니다.

### 4. 지브리풍 사운드트랙 합성 및 휴대폰 시스템 볼륨 실시간 연동 (`GhibliSoundEngine.kt`, `MainActivity.kt`)
- 외부 음원 의존성 없이 16-bit PCM 실시간 파형 사전 합성으로 로비·티샷·퍼팅·복불복 어쿠스틱 배경음악 4종과 7종 게임별 시작·진행·클라이맥스 심장박동·결과 팡파르 효과음을 제작해 탑재했습니다.
- `MainActivity`에 `volumeControlStream = AudioManager.STREAM_MUSIC`을 지정하고 `RINGER_MODE_CHANGED_ACTION`, `VOLUME_CHANGED_ACTION`, `ACTION_AUDIO_BECOMING_NOISY`, `AudioDeviceCallback`을 실시간 구독해 휴대폰이 무음 모드(`RINGER_MODE_SILENT`)일 때는 자동 음소거되고 휴대폰 측면 볼륨 버튼 및 외장 스피커·블루투스 미디어 볼륨이 즉시 마스터 볼륨으로 반영되도록 구현했습니다.
- 사운드 설정 창(`SoundSettingsDialog`)에서 전체 사운드 및 채널별(배경음악, 진행 효과음, 클라이맥스 심장박동, 결과 팡파르) 개별 켜기/끄기와 볼륨 슬라이더 조절을 지원합니다.

### 5. 테스트 및 에뮬레이터 실행 검증
- `GameModelsTest` 단위 테스트(7종 게임 구성, 캐디백·참가자 프리셋, 2클릭 성씨·호칭 결합, 6색 순위 배지 비중복, 진행시간 정규화) 전수 통과를 확인했습니다.
- `medium_phone` 에뮬레이터에서 실기동 캡처 및 런타임 오류 0건(`AndroidRuntime:E`)을 검증하고 `FairwayPicnic-v1.1.0-release.apk` (`versionCode = 2`, `8,264,811` bytes) 빌드를 완료했습니다.

---

## 주요 디렉터리 및 파일 구조

- `app/src/main/kotlin/com/example/fairwaypicnic/`
  - `MainActivity.kt`: 앱 진입점, `STREAM_MUSIC` 하드웨어 볼륨 직결, 공통 `선수입장`·사운드 설정 모달 및 화면 전환 제어
  - `audio/GhibliSoundEngine.kt`: 지브리풍 4종 어쿠스틱 BGM 루프 및 게임별 효과음 PCM 파형 합성, 무음·블루투스·미디어 볼륨 연동 엔진
  - `data/PlayerPreferencesStore.kt`: 선수 명단 프리셋, 진행 시간(`5/10/15초`), 채널별 사운드 설정 영구 저장소
  - `model/GameModels.kt`: 7종 게임 정의(`GolfGameType`), `KoreanGolfNameHelper` 2클릭 성씨·호칭 조합기, `AppSettings` 모델
  - `ui/components/GhibliVisuals.kt`: 수채화 배경·캐릭터 배지, `PlayerEntryDialog`, `SoundSettingsDialog`, 6색 순위 결과 모달, 슬로우모션·심장박동 오버레이
  - `ui/games/`: 7종 미니게임 화면 (`TeeBounceGameScreen.kt`, `NotchStickGameScreen.kt`, `LadderGameScreen.kt`, `NearPinPuttingGameScreen.kt`, `ForestBallBasketGameScreen.kt`, `WindmillRouletteGameScreen.kt`, `LuckyWoodTileGameScreen.kt`)
  - `ui/home/HomeScreen.kt`: 지브리 피크닉 감성 메인 로비 및 게임 선택 카드 화면
  - `ui/theme/`: 라이트 수채화 컬러 팔레트(`Color.kt`, `RankBadgeColors`), 타이포그래피(`Type.kt`), 테마(`Theme.kt`)
- `app/src/test/kotlin/com/example/fairwaypicnic/GameModelsTest.kt`: 핵심 도메인 로직 단위 테스트
- `FairwayPicnic-v1.0.0-release.apk`, `FairwayPicnic-v1.1.0-release.apk`: 릴리스 APK 산출물
- `work_history/work_history.md`: 프로젝트 누적 작업 히스토리 문서
