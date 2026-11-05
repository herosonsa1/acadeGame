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

## [2026-10-29] Phase 2: 순위 배지 6색 분리, 전 게임 공통 선수입장·2클릭 이름 조합기, 진행시간 설정 및 사운드 고도화 [v1.1.0]

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


---

## [2026-10-29] Phase 3: 배경음 전용 음소거 토글·상단 상태 아이콘 및 지브리 동영상·애니메이션 연출 고도화 [v1.2.0]

### 1. 배경음(BGM) 전용 음소거 원터치 토글 및 상단 상태 아이콘 표기 (`GameModels.kt`, `GhibliSoundEngine.kt`, `GhibliVideoAndEffects.kt`, `HomeScreen.kt`, `GhibliVisuals.kt`, `MainActivity.kt`)
- **배경음 단독 음소거 토글 로직**: `AppSettings.isBgmMuted`와 `toggleBgmOnlyMute()`를 구현하여 배경음악만 즉시 끄고 게임 시작·진행·클라이맥스 심장박동·결과 발표 효과음은 그대로 재생되도록 분리했습니다. 버튼을 한 번 더 누르면 토글 방식으로 배경음 음소거가 곧바로 해제됩니다.
- **`AudioTrack.MODE_STATIC` 정밀 제어**: `GhibliSoundEngine`에서 배경음악 음소거 시 정적 루프 트랙을 안전하게 일시정지(`pause()`)하고 재개 시 루프 구간(`setLoopPoints(0, pcm.size, -1)`)을 복원해 끊김 없이 이어지도록 보강했습니다.
- **상단 음소거 상태 벡터 아이콘 상시 표기**: 홈 로비 상단 바, 7종 미니게임 스테이지 상단 헤더, `선수입장` 모달 상단, `사운드 설정` 모달 상단에 `BgmMuteStatusToggleButton` 및 `BgmMuteStatusVectorIcon`을 배치했습니다. 음소거 활성 시 코랄 테라코타 배지와 대각선 슬래시(`/` + `X`) 음표 아이콘, `'배경음 음소거됨'`(`'배경음 끔'`) 문구가 선명하게 표시됩니다.

### 2. 게임 선택 및 진행 중 지브리 동영상·애니메이션 연출 적용 (`res/raw/*.mp4`, `res/drawable-nodpi/*.jpg`, `GhibliVideoAndEffects.kt`, 7종 게임 화면)
- **첨부 영상 프레임 분할 및 최적화 인코딩**: `순서정하기게임_영상.mp4`(`720x1280`, `24fps`)에서 장면 전환 경계를 프레임 단위로 분리해 무음(`-an`) H.264(`yuv420p`, `540x960`) 클립 4종(`vid_select_order.mp4`, `vid_tee_bounce.mp4`, `vid_group_cheer.mp4`, `vid_stick_draw.mp4`)과 첫 프레임 포스터 이미지 4종(`poster_*.jpg`)을 탑재했습니다.
- **`TextureView` 기반 무깜빡임 동영상 합성**: `SurfaceView`/`VideoView`의 검은 화면 깜빡임과 Z-오더 뚫림을 방지하기 위해 첫 프레임 포스터 위에서 알파 페이드로 전환되는 `GhibliRawVideoPlayer`(`TextureView` + `CenterCrop` 행렬 보정 + 슬로우모션 배속 연동)를 구현했습니다.
- **선택·입장·진행·결과 4단계 연출 통합**:
  1. 홈 화면에서 게임을 고르면 동심원 파동과 나뭇잎 파티클(`HomeSelectionBurstOverlay`)이 퍼지고 `선수입장` 모달 상단에서 선택 게임 동영상 프리뷰가 재생됩니다(키보드 입력 시 자동 접힘).
  2. 게임 입장 직후 1.38초 분량의 지브리 인트로 컷신(`GameIntroCutsceneOverlay`, 원터치 즉시 스킵 지원)이 재생됩니다.
  3. 7종 게임 진행 중 무대 배경(`StageCinemaBackdrop`)에서 실시간 동영상이 재생되며 방사형 집중선(`AnimeSpeedLinesOverlay`), 타격·뽑기 스타버스트(`AnimeImpactBurstOverlay`), 초록 나뭇잎(`FloatingFairwayLeavesOverlay`), 슬로우모션 구간 좌·우 코너 동반자 깜짝 리액션 배지(`AnimeGolferReactionOverlay`)가 함께 어우러집니다.
  4. 최종 결과 모달(`GameResultDialog`) 상단에 동반자 전원 응원 영상(`vid_group_cheer.mp4`) 배너를 추가했습니다.

### 3. 테스트 및 릴리스 산출물
- `GameModelsTest` 단위 테스트(`verifyBgmOnlyMuteToggleBehavior`, `verifyAllSevenGamesHaveValidVideoMediaSpecs` 포함 총 6개 테스트) 전수 통과 및 `lintVitalRelease` 통과를 확인했습니다.
- `FairwayPicnic-v1.2.0-release.apk` (`versionCode = 3`, `11,774,743` bytes) 빌드 및 `medium_phone` 에뮬레이터 실행 검증을 완료했습니다.


---

## [2026-10-29] Phase 4: 행운의 더블 주사위·잔디밭 민속 윷놀이·숲속 오두막 타로 운세 3종 신설 [v1.3.0]

### 1. 행운의 더블 주사위 (`ArcadeFortuneModels.kt`, `DoubleDiceGameScreen.kt`)
- **2개 주사위 합산 승부 및 더블 보너스 규칙**: 참가자마다 2개의 원목 주사위를 던져 두 눈금의 합계(`2~12`)가 가장 높은 사람이 승리하도록 설계했습니다. 두 눈금이 같은 더블(`1+1` ~ `6+6`)이 나오면 동일 합계 일반 눈금보다 앞서는 보너스를 부여했고 최고점 동점자가 생기면 서든데스 연장 굴리기로 `1위 승자`부터 최하위 순위까지 명확하게 가려집니다.
- **원목 보드 투구 연출 및 전용 사운드**: 가죽 주사위 컵 흔들림음(`playDiceCupShake`)과 원목 트레이 연속 바운스음(`playDiceBounce`)을 합성해 적용했습니다. 마지막 참가자의 결정적 투구 순간에는 슬로우모션과 심장박동 연출이 작동합니다.

### 2. 잔디밭 민속 윷놀이 (`ArcadeFortuneModels.kt`, `YutNoriGameScreen.kt`)
- **미니게임 맞춤형 승자·패자 판정 규칙**: 긴 말판 대국 대신 라운딩 전후 5~15초 안에 승패가 갈리도록 미니 오솔길 윷판 레이스 규칙을 고안했습니다. 4개 자작나무 윷가락을 던져 `도(1칸)`·`개(2칸)`·`걸(3칸)`·`윷(4칸)`·`모(5칸)`·`빽도(1칸 후퇴)`를 판정하며 `윷`과 `모`가 나오면 `'한 번 더!'` 연속 보너스 투구가 즉시 이어집니다.
- **앞선 말 잡기 역전 규칙**: 이동한 칸에 앞서가던 동반자의 말이 있으면 상대 말을 잡아 상대는 1칸 뒤로 밀려나고 본인은 2칸 보너스 전진을 얻습니다. 전원 투구가 끝나면 최종 도달 칸수와 연속 보너스 기록을 비교해 `1위 승자`와 최하위 `패자`를 가려냅니다.

### 3. 숲속 오두막 타로 운세 (`ArcadeFortuneModels.kt`, `TarotFortuneGameScreen.kt`, `GhibliSoundEngine.kt`)
- **6종 운세 주제 선택**: 상단 운세 바에서 `오늘의 종합운`, `라운딩·스코어운`, `재물·금전운`, `애정·대인운`, `직장·사업·성취운`, `건강·컨디션운` 6가지 운세 중 원하는 주제를 골라 타로점을 볼 수 있습니다. 1인 단독 점괘와 2~6인 단체 점괘를 모두 지원합니다.
- **22장 메이저 아르카나 정·역방향 리딩 및 참가자 등수 산출**: `0번 바보(The Fool)`부터 `21번 세계(The World)`까지 22장 메이저 아르카나 전체 덱과 정방향·역방향 해석, 운세 주제별 맞춤 조언, 행운의 색상·숫자·아이템을 수록했습니다. 여러 명이 함께 볼 때는 각 사용자가 고른 카드의 기본 운세에 주제 시너지 편차를 더해 `52~99점` 사이의 행운 점수를 산출하고 참가자 수에 맞춰 `1위`부터 `N위`까지 중복 없는 등수와 개인별 상세 점괘 카드를 함께 보여줍니다.
- **전용 오르골 BGM 및 아르카나 효과음**: `별빛 숲속 오두막 오르골`(`MYSTIC_TAROT_CABIN`) 3/4박자 첼레스타·플루트 왈츠 배경음악과 타로 카드 패 펼침음(`playTarotDeckSpread`), 신비로운 아르페지오 공개음(`playTarotMysticReveal`)을 추가했습니다.

### 4. 홈 로비 10종 확장 및 검증 산출물 (`HomeScreen.kt`, `GhibliVisuals.kt`, `MainActivity.kt`, `GameModelsTest.kt`)
- **카테고리 탭 및 벡터 썸네일 확장**: 홈 로비를 `전체 10종`, `주사위 · 윷 · 타로`, `1홀 티샷 순서`, `커피 · 꼴등 복불복` 4개 가로 스크롤 탭으로 개편하고 신규 3종 전용 수채화 벡터 아이콘과 동영상 연출 매핑을 완료했습니다.
- **단위 테스트 및 릴리스 APK**: `GameModelsTest`의 9개 단위 테스트(주사위 합산·동점 해소, 윷놀이 판정·보너스·말 잡기·승패 분리, 타로 6종 주제·22장 아르카나·다인 점수 등수 검증 포함)와 `lintVitalRelease`를 모두 통과했으며 `FairwayPicnic-v1.3.0-release.apk`(`versionCode = 4`, `11,905,815` bytes) 빌드를 완료했습니다.


---

## [2026-10-29] Phase 5: 만능 결정 룰렛+ 전면 무료 탑재 및 사진·전 과정 영상 메신저 공유 구축 [v1.4.0]

### 1. 상용 룰렛 앱 REA 역공학 및 비골프 범용 직행 설계 (`DecisionRouletteModels.kt`, `MainActivity.kt`, `HomeScreen.kt`)
- **역공학 기반 유료 기능 전면 무료화**: `com.komorebi.roulette` 및 의사결정 룰렛 앱 구조를 JADX로 분석해 항목 수 무제한·항목별 가중치(`1~20배`)·클라이맥스 반전 특수효과 12종·발동 빈도 5단계·6종 감성 팔레트·7종 결정 사운드·4자리 PIN 잠금 기능을 광고와 결제 없이 무료로 개방했습니다.
- **골프 설정 생략 직행 진입 및 빠른 셋팅**: 홈 화면에서 `만능 결정 룰렛+`을 선택하면 캐디백 선수입장 창과 골프 인트로 영상을 건너뛰고 즉시 범용 룰렛 화면으로 진입합니다. 점심 메뉴·회식 장소·커피 계산·집안일 분담·발표 순서·벌칙 미션 등 일상 템플릿 10종과 여러 줄 일괄 붙여넣기(`항목 * 3` 배율 문법 지원)·`1~N` 숫자 자동 생성기를 탑재했습니다.
- **원판 좌반구 한글 정방향 표시 및 결정론적 궤적 엔진**: 원판 좌측 반구(`90~270도`) 조각 라벨에 `180도` 회전 보정을 적용해 어느 각도에서도 한글이 뒤집히지 않도록 구현했습니다. 또한 궤적 샘플링 프레임 수에 `ceil` 연산과 최종 시각 클램핑을 적용해 화면 정지 각도와 공유 영상 마지막 프레임이 오차 `0.0도`로 일치합니다.

### 2. 영구 보관함 및 추첨 통계 저장소 (`RouletteStorageRepository.kt`)
- **무손실 순수 코틀린 직렬화 코덱**: 특수문자나 줄바꿈이 포함된 항목명과 당첨 메모도 깨지지 않도록 표준 `java.util.Base64` 기반 `RouletteStorageCodec`을 설계해 `Dispatchers.IO` 환경에서 보관함(`roulette_saved_wheels_v1.dat`)·활성 룰렛(`roulette_active_wheel_v1.dat`)·추첨 이력(`roulette_history_v1.dat`)을 영구 보존합니다.
- **언제든 재실행 및 관리**: 한 번 만든 룰렛은 덮어쓰기·새 이름으로 저장·사본 복제·PIN 보호 삭제·기본 템플릿 복원을 지원하며 언제든 원터치로 다시 불러와 돌릴 수 있습니다.

### 3. 결과 카드 이미지 및 셋팅~회전~최종결정 전 과정 영상 공유 (`RouletteMediaExporter.kt`, `RouletteShareManager.kt`, `DecisionRouletteGameScreen.kt`)
- **고해상도 결과 카드 이미지 생성**: 추첨 완료 즉시 `1080x1350` 해상도(`JPEG 92%`, 약 `140KB`)의 라이트 테마 결과 카드를 생성해 정지 원판·당첨 항목·확률·적용된 특수효과·비밀 메모를 한 장에 담아냅니다.
- **전 과정 하드웨어 H.264 및 AAC 동영상 인코딩**: `MediaCodec` Surface(`video/avc`)와 PCM(`audio/mp4a-latm` `64kbps`) 2단계 버퍼링 `MediaMuxer` 파이프라인으로 셋팅 화면(`0.90초`)부터 고속 회전·핀 틱 사운드·슬로우모션·12종 반전 연출·최종 당첨 꽃잎 축하(`1.65초`)까지 전 과정을 기록합니다. 카카오톡·텔레그램용 표준 HD(`720x1280`, `30fps`, 실측 약 `1.42MB`)와 문자 전송용 초경량(`540x960`, `24fps`, 약 `0.5~1.0MB`) 두 가지 화질 옵션을 제공합니다.
- **메신저 무결성 공유 연동**: `FileProvider`와 `ClipDescription` 기반 안전 `ClipData` 구성으로 카카오톡·문자 메시지·텔레그램·시스템 공유 시트·휴대폰 갤러리 저장을 모두 지원하며 카카오톡 파일 스트림 누락 방지를 위해 요약 문구 클립보드 자동 백업을 함께 적용했습니다.

### 4. 단위 테스트·에뮬레이터 검증 및 릴리스 산출물
- `DecisionRouletteModelsTest` 6개 및 `GameModelsTest` 9개 등 총 15개 단위 테스트와 `lintVitalRelease`를 전수 통과했습니다.
- `FairwayPicnic-v1.4.0-release.apk`(`versionCode = 5`, `12,086,927` bytes) 빌드를 완료하고 격리 에뮬레이터(`emulator-5558`)에서 직행 진입·원판 정방향 한글 렌더링·추첨 종료 시 결과 공유 창 자동 오픈·결과 이미지 및 전 과정 영상 생성까지 무오류 동작을 확인했습니다.


## Phase 6: '언덕 위 풍차 룰렛' 전 기능 이식 및 지브리 목가적 풍차 UI 고도화 [v1.5.0]
- **'만능 결정 룰렛+' 메뉴 및 독립 저장소 100% 보존 (`RouletteStorageRepository.kt`, `HomeScreen.kt`)**:
  - 기존 `만능 결정 룰렛+`(`GolfGameType.DECISION_ROULETTE_PLUS`)의 홈 화면 최상단 메뉴와 직행 진입 동선, 기존 사용자 데이터 파일(`roulette_saved_wheels_v1.dat`, `roulette_active_wheel_v1.dat`, `roulette_history_v1.dat`)을 한 글자 손상 없이 그대로 유지했습니다.
  - `RouletteStorageRepository`에 `storageNamespace`와 `defaultTemplatesProvider`를 도입해 `언덕 위 풍차 룰렛` 전용 독립 보관함(`windmill_saved_wheels_v1.dat`, `windmill_active_wheel_v1.dat`, `windmill_history_v1.dat`)을 분리 구축했습니다.
- **출전 선수(선수입장) 양방향 동기화 및 전용 풍차 프리셋 10종 탑재 (`DecisionRouletteModels.kt`)**:
  - `RouletteColorPalette.HILLSIDE_WINDMILL`(`언덕 풍차 원목` 8색 저채도 팔레트)을 추가하고 `선수입장`에서 설정한 동반자 명단이 자동 반영되는 선수 연동형 풍차 프리셋 4종(`그늘집 커피·음료 쏘기`, `그늘집 간식·만두 쏘기`, `캐디피·카트비 끝전 담당`, `카트·팀 순번 하나씩 뽑기`)과 그늘집 간식·19홀 뒤풀이·홀 스페셜 미션·공략 클럽 결정·월례회 경품 가중치·멀리건 인정 등 일반 골프장 프리셋 6종을 포함한 총 10종의 기본 템플릿을 구성했습니다.
  - 상단 서브헤더와 항목 편집 다이얼로그 양쪽에 `선수 채우기 (N명)` 원터치 칩을 추가해 일반 맞춤 풍차에서도 언제든 현재 출전 선수 명단을 즉시 채워 넣을 수 있도록 연결했습니다.
- **지브리 언덕 원목 풍차 스테이지 캔버스 및 전 기능 이식 (`WindmillRouletteGameScreen.kt`, `GhibliVisuals.kt`)**:
  - `GameStageScaffold`에 `subtitleOverride`와 `customSubHeader` 슬롯을 확장해 카드 중첩 없이 상단에 풍차 보관함·현재 저장·새 풍차·선수 채우기·기록 칩과 저장된 풍차 원터치 전환 바를 배치했습니다.
  - `HillsideWindmillWheelCanvas`에서 초원 이중 언덕 능선, 들꽃 포인트, 돌담·참나무 A자형 풍차 오두막 탑과 아치형 원목 문, 원판과 일체형으로 회전하는 4장의 크림 린넨 원목 격자 날개, 황동 리벳 이중 원목 림, 좌반구(`90~270도`) 정방향 보정 한글 라벨, 8톱니 허브 캡, 테라코타 지붕 처마 플래퍼를 벡터 그래픽으로 구현했습니다.
  - 무제한 항목·가중치(`1~20배`), 일괄 입력(`항목 * 3`), `1~N` 숫자 생성, 12종 반전 특수효과 및 발동 빈도, 회전 시간(`3~20초`)·직접중지·당첨 제외·`?` 바람 장막·비밀 당첨 메모·4자리 PIN 잠금·무사 통과 동반자 면제 표시까지 만능 결정 룰렛+의 전 기능을 빠짐없이 이식했습니다.
- **풍차 테마 고해상도 결과 이미지(`JPEG`) 및 전 과정 `MP4` 영상 공유 확장 (`RouletteMediaExporter.kt`)**:
  - `RouletteExportVisualStyle.HILLSIDE_WINDMILL` 모드를 추가해 `1080x1350` 결과 카드 이미지와 `720x1280` HD / `540x960` 초경량 `MP4` 동영상 프레임에도 언덕 초원 배경과 돌담 풍차 탑, 회전하는 4장의 원목 격자 풍차 날개가 그대로 렌더링되어 카카오톡·문자·텔레그램·갤러리로 공유되도록 완성했습니다.
- **단위 테스트 확장 및 에뮬레이터 실기 검증 완료 (`DecisionRouletteModelsTest.kt`)**:
  - `verifyHillsideWindmillTemplatesAndPlayerSync` 단위 테스트를 포함한 전체 JUnit 테스트 통과, `lintVitalRelease` 무오류 검증, 격리 에뮬레이터(`emulator-5558`) 환경에서 홈 화면 두 룰렛 공존·풍차 스핀·결과 카드 이미지 및 회전 전 과정 `MP4` 영상 생성 검증을 마치고 `FairwayPicnic-v1.5.0-release.apk`(`versionCode = 6`)를 패키징했습니다.



---

## [2026-10-29] Phase 7: 수록 미니게임 11종 전체 공통 결과 카드 사진 및 전 과정 영상 메신저 공유 구축 [v1.6.0]

### 1. 전 게임 공통 공유 모델 및 메신저 발송 파이프라인 확장 (`CommonGameShareModels.kt`, `RouletteShareManager.kt`, `file_paths.xml`)
- **9종 미니게임 맞춤형 장면 스냅샷 모델 설계**: 기존 2종 룰렛(`만능 결정 룰렛+`, `언덕 위 풍차 룰렛`)을 제외한 나머지 9종 게임의 실제 플레이 상태와 결과를 그대로 재현할 수 있도록 `CommonGameSharePayload`와 봉인 인터페이스 `GameExtraSceneData`(`TeeBounce`, `NotchStick`, `ForestLadder`, `NearPinPutting`, `BallBasket`, `LuckyWoodTile`, `DoubleDice`, `YutNori`, `TarotFortune`)를 정의했습니다.
- **캐시 경로 및 메신저 안전 공유 연동**: `file_paths.xml`에 `shared_games/` 캐시 디렉터리를 추가하고 `RouletteShareManager`에 `buildCommonGameShareSummaryText`와 `shareCommonGameMediaFile`을 구현해 카카오톡(스트림 단독 전송 및 요약 문구 클립보드 자동 복사)·문자 메시지·텔레그램·시스템 공유 시트·갤러리 영구 저장을 전 게임 공통으로 지원합니다.

### 2. 게임별 벡터 스테이지 렌더러 및 H.264/AAC 동영상 인코더 구현 (`CommonGameMediaExporter.kt`, `CommonGameShareDialog.kt`)
- **고해상도 결과 카드 이미지(`1080x1350` `JPEG`) 생성**: 크림 라이트 배경 위에 게임별 영문 스탬프, 대표 당첨자 하이라이트 배너, 해당 라운드의 벡터 일러스트 스테이지, 고유 배지 색상이 적용된 동반자 순위표를 한 장의 선명한 카드로 합성합니다.
- **출전 셋팅~진행~슬로우모션~최종결정 전 과정 `MP4` 인코딩**: 외부 라이브러리 없이 Android 표준 `Canvas`와 하드웨어 `MediaCodec`(`video/avc` Surface + `audio/mp4a-latm` AAC) 및 2단계 버퍼드 `MediaMuxer`로 약 `6.8초` 분량의 고효율 동영상(`720x1280` HD / `540x960` 초경량)을 생성합니다. 티 회전 지목, 오너 스틱 상승 및 눈금 노출, 사다리 안개 걷힘과 공 이동, 연못 해저드 퍼팅 궤적, 도토리 당첨 볼 오픈, 나무 패 3D 뒤집기, 더블 주사위 바운스, 10칸 말판 전진과 윷가락 투구, 메이저 아르카나 타로 카드 오픈 애니메이션과 출발 차임·우드 틱·두근두근 심장박동·5음 축하 팡파레 사운드가 프레임 단위로 동기화됩니다.
- **통합 미리보기 및 메신저 공유 모달**: `CommonGameShareDialog`에서 결과 이미지 미리보기와 인코딩된 `MP4` 루프 재생(`TextureView` + `MediaPlayer`)을 즉시 확인하고 화질 선택 및 원터치 메신저 전송을 수행할 수 있습니다.

### 3. 9종 미니게임 화면 및 공통 결과 다이얼로그 전면 연동 (`GhibliVisuals.kt`, `ui/games/*.kt`, `HomeScreen.kt`)
- **결과 모달 및 종료 스테이지 이중 진입점 제공**: 공통 `GameResultDialog`와 타로 전용 `TarotReadingResultDialog` 내부에 `결과 카드 사진 공유`와 `진행 전 과정 영상 공유` 버튼을 나란히 배치해 원하는 공유 탭으로 즉시 진입할 수 있게 했으며 9종 게임 모든 종료 화면 하단 컨트롤 바에도 `공유` 버튼을 추가해 결과 창을 닫은 뒤에도 언제든 공유 창을 다시 열 수 있도록 구성했습니다.
- **단위 테스트 추가 및 빌드·에뮬레이터 검증**: `CommonGameShareTest`를 신설해 11종 전체 게임의 고유 파일 접두어·영문 스탬프·대표 행 추출·요약 텍스트 생성을 검증하고 `FairwayPicnic-v1.6.0-release.apk`(`versionCode = 7`) 릴리스 패키징을 완료했습니다.

---

## [2026-10-30] Phase 8: '만능 결정 룰렛+' 언덕 위 원목 풍차 디자인 통합 및 3단계 서사형 힐링 어쿠스틱 배경음악 개편 `[v1.7.0]`

### 1. '만능 결정 룰렛+' 목가적 언덕 위 풍차 배경·원판·공유 미디어 통합 (`WindmillRouletteGameScreen.kt`, `DecisionRouletteGameScreen.kt`, `RouletteMediaExporter.kt`, `DecisionRouletteModels.kt`)
- **초원 풍차 스테이지 및 4날개 원목 룰렛 캔버스 통합**: `HillsideWindmillWheelCanvas`와 `WindmillHubDisplayState`를 공용 모듈로 개방하고 `DecisionRouletteGameScreen`에 `GameStageScaffold`(`showPlayerEntryButton = false`)와 `StageCinemaBackdrop(GolfGameType.WINDMILL_ROULETTE)`를 적용했습니다. 이를 바탕으로 골프 선수입장 게이트 없이 즉시 실행되는 범용성과 독립 보관함(`roulette_*_v1.dat`)·일상 템플릿 10종은 그대로 보존하면서 이중 초원 언덕·돌담 참나무 풍차 오두막 탑·4장의 크림 린넨 원목 격자 날개·황동 리벳 이중 원목 림·8톱니 허브 캡·테라코타 처마 플래퍼·플로팅 원목 명패 디자인을 `언덕 위 풍차 룰렛`과 동일하게 적용했습니다.
- **풍차 씬 공유 카드(`JPEG`) 및 전 과정 영상(`MP4`) 확장**: `RouletteExportVisualStyle.DECISION_WINDMILL` 모드를 추가해 `만능 결정 룰렛+`에서 생성되는 `1080x1350` 결과 카드 이미지와 `720x1280` `HD` / `540x960` `SD` 회전 영상에도 `DECISION ROULETTE+` 스탬프와 언덕 위 원목 풍차 탑·4장 회전 날개가 함께 렌더링되도록 연동했습니다. 홈 화면의 `만능 결정 룰렛+` 벡터 아이콘과 기본 팔레트 역시 `HILLSIDE_WINDMILL` 원목 톤으로 조화롭게 맞췄습니다.

### 2. 귀가 편안한 중저음 어쿠스틱 악기 합성 및 3단계 서사형 장편 배경음악 구축 (`GhibliSoundEngine.kt`, `MainActivity.kt`, `GhibliVisuals.kt`)
- **고음역 자극 제거 및 스튜디오 어쿠스틱 6종 악기·저역통과 필터 설계**: 귀를 따갑게 만들던 고음역 순수 사인파·종소리 배음(`MIDI 76~96`)을 완전히 배제하고 모든 선율과 화성을 `C2~C5`(`MIDI 36~72`, 기본 주파수 `523Hz` 이하) 중저음역 안에 배치했습니다. 펠트 피아노, 핑거스타일 나일론 기타, 서정적 첼로, 업라이트 콘트라베이스, 로즈우드 마림바, 브러시 우드 셰이커·저음 카혼을 물리 엔벨로프로 모델링하고 3탭 원목 홀 초기 반사음과 `1.28kHz` 2단 저역통과 필터(`2-Pole Warm Low-Pass Filter`), 소프트 새추레이션을 적용했습니다. 주요 효과음 또한 부드러운 중음 우드·로즈우드 톤으로 재조율했습니다.
- **16마디 장편 작곡 및 게임 진행 동기화 3단계 크로스페이드**: 짧은 8~12초 루프 대신 5종 테마별로 `A -> A' -> B -> C` 16마디(`64`박, 악장당 약 35~45초, 테마당 총 2분 이상) 장편 악보를 작곡했습니다. `LocalSoundEngine`과 `GameStageScaffold`를 연동해 수록된 11종 모든 게임에서 대기·셋팅 시 **평온한 시작(`CALM_INTRO`)**, 회전·주행·추첨 진행 중 **설레는 두근거림(`PLAYING_PULSE`)**, 최종 결과 발표 및 축하 시 **기쁨의 결과(`JOYFUL_FINALE`)** 악장으로 `450ms` 듀얼 `AudioTrack` 크로스페이드가 자동 수행됩니다. 사운드 설정 모달에서도 현재 재생 중인 테마와 3단계 악장 상태 확인 및 원터치 미리듣기를 지원합니다.
- **단위 테스트 및 릴리스 검증 완료**: `DecisionRouletteModelsTest`에 15개 전 악장의 `64`박 장편 길이·`MIDI <= 72` 고음 제한·전후반 편곡 비반복성·루프 경계 무잡음 검증 및 `DECISION_WINDMILL` 스타일 검증 테스트를 추가하고 `FairwayPicnic-v1.7.0-release.apk`(`versionCode = 8`) 빌드를 완료했습니다.

---

## [2026-10-30] Phase 9: 앱 스토어 등록용 공식 개인정보처리방침 작성 및 GitHub Pages 라이브 배포 (`docs/privacy.html`)

- **실제 권한 및 데이터 구조 100% 정합 개인정보처리방침 수립**: `com.herosonsa.fairwaypicnic` 매니페스트(`VIBRATE` 단독 권한, `INTERNET` 미사용 오프라인 앱, 메신저 `<queries>` 가시성)와 로컬 전용 저장 구조(`DataStore`, `roulette_*_v1.dat`, `windmill_*_v1.dat`, 사용자 주도 `FileProvider`/`MediaStore` 미디어 공유), 데이터 파기 방법, 아동 보호 조항, 영문 요약문(`Privacy Policy Summary`), 개발자 문의처(`herosonsa1@gmail.com`)를 담아 `docs/privacy.html` 및 `docs/index.html`을 작성했습니다.
- **GitHub Pages 라이브 HTTPS 호스팅 개통**: 공개 저장소 `herosonsa1/acadeGame`의 `gh-pages` 브랜치에 배포하고 GitHub Pages(`https_enforced`)를 활성화해 Google Play Console 등에 즉시 입력 가능한 공식 URL(`https://herosonsa1.github.io/acadeGame/privacy.html` 및 `https://herosonsa1.github.io/acadeGame/`) `HTTP 200` 응답을 검증했습니다.


---

## [2026-10-30] Phase 10: 스토어 등록용 고해상도 아이콘·그래픽 이미지 제작 및 앱 런처 아이콘 동기화 `[v1.7.1]`

- **Google Play Console 공식 규격 대표 아이콘 및 그래픽 이미지 제작 (`store_assets/`)**:
  - 초원 언덕 위 돌담·참나무 4날개 풍차 룰렛(`만능 결정 룰렛+` & `언덕 위 풍차 룰렛`)과 퍼팅 그린·홀컵 깃발·골프공·원목 주사위를 조합한 마스터 일러스트를 제작하고 Google Play 자동 마스킹 규칙(투명 여백 및 외부 그림자 금지, 중앙 안전 영역 배치, 배지 문구 배제)에 맞춘 `fairway_picnic_playstore_icon_512.png`(`512x512`, `32-bit RGBA`, `alpha = 255` Full-Bleed, `563,031 bytes`)를 생성했습니다.
  - 노출 형태 확인 및 타 마켓 홍보용 `20%` 라운딩 미리보기 아이콘 `fairway_picnic_icon_rounded_preview_512.png`(`512x512`, `RGBA`, `564,854 bytes`)와 스토어 등록 필수 항목인 그래픽 이미지 `fairway_picnic_feature_graphic_1024x500.png`(`1024x500`, `PNG`, `613,489 bytes`, 4배율 슈퍼샘플링 프레임 및 시스템 폰트 한글 타이포그래피 렌더링)를 함께 구축했습니다.
- **안드로이드 앱 내부 런처 아이콘 동기화 및 `[v1.7.1]` 릴리스 패키징**:
  - 단말 홈 화면 아이콘과 스토어 아이콘이 완전히 일치하도록 `drawable-nodpi/ic_launcher_artwork.webp`(`432x432` 어댑티브 전경·배경 통합 레이어), `drawable/ic_launcher_monochrome.xml`(Android 13 이상 단색 테마 벡터 아이콘), `mipmap-anydpi-v26/ic_launcher.xml`·`ic_launcher_round.xml`, 밀도별(`mdpi`~`xxxhdpi`) `ic_launcher.webp`·`ic_launcher_round.webp`를 모두 교체했습니다.
  - `versionCode = 9` / `versionName = "1.7.1"`로 버전을 올리고 단위 테스트 20종과 `lintVitalRelease`를 통과한 뒤 `FairwayPicnic-v1.7.1-release.apk`(`12,298,304 bytes`) 및 `store_assets/FairwayPicnic-v1.7.1-release.aab`(`12,244,913 bytes`) 패키징을 마쳤습니다.

---

## [2026-10-30] Phase 11: 스토어 등록 올인원 패키지(등록 텍스트 · 아이콘 · 그래픽 이미지 · 홍보 동영상 · 휴대전화 스크린샷 8종) 완비

- **스토어 등록 원고 및 콘솔 설문 가이드 작성 (`store_assets/01_store_listing_*.md/.txt`)**:
  - Google Play Console 글자 수 제한을 통과하도록 앱 이름(`24`자/`30`자), 간단한 설명(`69`자/`80`자), 자세한 설명(`1,579`자/`4,000`자), 첫 출시 노트(`279`자/`500`자), 영문(`en-US`) 등록 원고 앱 콘텐츠·데이터 보안·콘텐츠 등급(`IARC`) 설문 응답 가이드를 작성하고 `im-not-ai` 게이트 검증(`exit_code = 0`)을 마쳤습니다.
- **실기 플레이 기반 휴대전화 스크린샷 8종 풀세트 구축 (`05_phone_screenshots_9x16/` & `05_phone_screenshots_raw/`)**:
  - 격리 에뮬레이터(`emulator-5558`) Demo Mode 환경에서 캡처한 실제 인앱 화면을 바탕으로 `1080x1920`(`9:16` 정비율) 라이트 테마 에디토리얼 쇼케이스 스크린샷 `8`종(`01_home_11_games.png` ~ `08_share_and_healing_bgm.png`)과 `1080x2400` 무프레임 원본 캡처 `8`종을 완성했습니다.
- **Full HD 홍보 동영상 2종(`16:9` 가로형 & `9:16` 세로형) 제작 및 통합 ZIP 패키징**:
  - 실제 게임 플레이 녹화 클립(`만능 결정 룰렛+`, `드라이버 티 바운스`, `니어핀 퍼팅 치킨게임`, `잔디밭 민속 윷놀이`)과 `1.35kHz` 2단 저역통과 필터가 적용된 3단계 서사형 어쿠스틱 사운드트랙(`48kHz Stereo AAC`)을 합성해 `04_promo_video_16x9_1920x1080.mp4`(`1920x1080`, `30fps`, `34.0`초, `2.98MB`)와 `04_promo_video_9x16_1080x1920.mp4`(`1080x1920`, `30fps`, `34.0`초, `3.52MB`)를 인코딩하고 전체 에셋을 `FairwayPicnic_Store_Assets_Package_v1.7.1.zip`(`17.67MB`)으로 패키징했습니다.


---

## [2026-10-30] Phase 12: APK 및 Android App Bundle 전체 패키지명(`com.jjalddalmark.minigamerullet`) 전환 및 정식 릴리스 서명 구축 `[v1.7.2]`

- **코드베이스 전체 패키지 식별자(`applicationId` · `namespace` · Kotlin 소스 트리) 일괄 전환**:
  - `app/build.gradle.kts`의 `namespace`와 `applicationId`를 모두 `com.jjalddalmark.minigamerullet`(`versionCode = 10`, `versionName = "1.7.2"`)으로 일원화했습니다.
  - `main`, `test`, `androidTest` 소스 세트 전체 Kotlin 파일 `30`개를 `kotlin/com/jjalddalmark/minigamerullet/` 디렉터리로 이전하고 기존 `com/example` 트리를 완전히 제거했으며 `FileProvider` 권한(`com.jjalddalmark.minigamerullet.fileprovider`)까지 자동 정합성을 확인했습니다.
- **Google Play Console 업로드용 정식 Release 서명키 생성 및 번들(`AAB` · `APK`) 검증**:
  - 프로덕션 업로드 거부 원인인 디버그 서명(`signingConfigs.getByName("debug")`)을 제거하고 전용 2048비트 RSA 업로드 키스토어(`keystore/minigamerullet-upload-key.jks`, 별칭 `upload`, 유효기간 `10,000`일, `SHA-256: ac2735b9d67b5da30f702508ea7d0fb200ce287d661bb9d923f42b8998760fa2`)와 `keystore.properties`(`.gitignore` 보안 분리)를 구성했습니다.
  - `clean`, `testDebugUnitTest`, `assembleDebugAndroidTest`, `lintVitalRelease`, `assembleRelease`, `bundleRelease` 전체 파이프라인을 무오류로 통과한 뒤 `aapt2`, `apksigner`, `jarsigner` 및 `AAB` 내부 `AndroidManifest.xml`·`classes.dex` 바이너리 감사를 거쳐 구 패키지 문자열 잔존 `0`건과 정식 `CN=Jjalddalmark` 서명을 검증했습니다.
- **라이브 개인정보처리방침(`gh-pages`) 및 스토어 등록 문서 동기화**:
  - `docs/privacy.html`, `docs/index.html`, `store_assets/01_store_listing_texts_ko.md`, `01_store_listing_copy_paste.txt` 내 패키지명을 `com.jjalddalmark.minigamerullet`으로 갱신하고 GitHub Pages(`https://herosonsa1.github.io/acadeGame/privacy.html`) 재배포 및 `HTTP 200` 실시간 반영을 확인했습니다.


---

## [2026-10-30] Phase 13: 공식 스토어 등록 명칭(`미니게임 - 만능 룰렛 & 골프 복불복`) 앱 내부 및 스토어 에셋 전 구간 반영 `[v1.7.3]`

- **인앱 타이틀 · 설명 문구 · 공유 카드 푸터 · 메신저 요약문 일괄 개편 (`versionCode = 11`)**:
  - `res/values/strings.xml`의 `app_name`을 `미니게임 - 만능 룰렛 & 골프 복불복`으로 변경하고 `HomeScreen.kt` 상단 배너를 `MINIGAME · ROULETTE & GOLF` / `미니게임 - 만능 룰렛 & 골프 복불복` / `일상 만능 결정 룰렛+부터 티샷 순서·커피 내기·주사위·윷·타로까지 11종 수록`으로 개편했습니다.
  - `RouletteShareManager.kt`의 메신저 공유 요약 헤더, `CommonGameMediaExporter.kt`와 `RouletteMediaExporter.kt`의 결과 이미지(`JPEG`)·진행 영상(`MP4`) 하단 브랜드 표기, 갤러리 영구 저장 디렉터리(`Pictures/MinigameRullet`, `Movies/MinigameRullet`) 및 안내 토스트를 모두 새 명칭에 맞춰 통일했습니다.
  - `HomeScreenTest`, `CommonGameShareTest`, `DecisionRouletteModelsTest`에 새 앱 명칭 검증 단언문을 추가해 단위 테스트 및 `lintVitalRelease` 무오류 통과를 확인했습니다.
- **개인정보처리방침 라이브 웹페이지 및 스토어 시각 에셋 · 번들 동기화**:
  - `docs/privacy.html` 및 `docs/index.html`을 `미니게임 - 만능 룰렛 & 골프 복불복` 명칭으로 갱신하고 GitHub Pages(`https://herosonsa1.github.io/acadeGame/privacy.html`) 재배포 및 `HTTP 200` 실시간 반영을 검증했습니다.
  - 스토어 등록 텍스트(`01_store_listing_*.md/.txt`), `1024x500` 그래픽 이미지(`03_feature_graphic_1024x500.png`), 첫 번째 휴대전화 스크린샷(`01_home_11_games.png`), Full HD 홍보 동영상 2종(`04_promo_video_16x9_1920x1080.mp4`, `04_promo_video_9x16_1080x1920.mp4`), 정식 Release 서명 번들(`com.jjalddalmark.minigamerullet-v1.7.3-release.aab`)과 통합 압축본(`com.jjalddalmark.minigamerullet_Store_Assets_Package_v1.7.3.zip`)을 일괄 재생성했습니다.


---

## [2026-10-30] Phase 14: 자체 테스트 · 배포 · 스토어 등록 전 산출물 `.\dist` 단일 폴더 통합 및 자동 동기화 파이프라인 구축

- **`.\dist` 단일 디렉터리 통합 및 루트 중복 산출물 정리 (`AP-DIST-UNIFIED-DELIVERABLE-DIR`)**:
  - 프로젝트 루트에 산재해 있던 구버전 `.apk` 파일들과 별도 `store_assets/` 디렉터리를 정리하고 모든 최종 산출물(`com.jjalddalmark.minigamerullet-v1.7.3-release.aab`, `com.jjalddalmark.minigamerullet-v1.7.3-release.apk`, `com.jjalddalmark.minigamerullet-v1.7.3-debug.apk`, `com.jjalddalmark.minigamerullet_Store_Assets_Package_v1.7.3.zip`, `privacy.html`, `00_DIST_GUIDE.md` 및 `dist/store_listing/` 하위 등록 텍스트·아이콘·그래픽 이미지·홍보 동영상·휴대전화 스크린샷 전종)을 `.\dist` 폴더 내부에 일원화했습니다.
- **Gradle 빌드 완료 시 `.\dist` 자동 복사 태스크 내장 (`app/build.gradle.kts`)**:
  - Gradle Configuration Cache와 완벽히 호환되는 `syncDebugApkToDist`, `syncReleaseApkToDist`, `syncReleaseBundleToDist` 태스크를 등록하고 `assembleDebug`, `assembleRelease`, `bundleRelease`에 `finalizedBy`로 연결해 추후 수정·재빌드 시에도 항상 구버전 중복 파일을 제거하고 최신 산출물이 `.\dist`로 자동 동기화되도록 구성했습니다.


## Phase 15: Android 16 (API 수준 36) 타겟팅 및 보안·성능·적응형 멀티윈도우 표준 적용 [v1.7.4]
- **Android 16 (`API 36`) 빌드 구성 및 버전 승격 (`app/build.gradle.kts`, `gradle.properties`)**:
  - Google Play Console의 API 35 기존 트랙 경고를 해소하고 최신 보안·성능 기준을 충족하도록 `compileSdk = 36`, `targetSdk = 36`(`platformBuildVersionCode = 36`, Android 16 SDK Platform Revision 2 / Build-Tools `36.0.0`) 타겟팅을 검증하고 `versionCode = 12`, `versionName = "1.7.4"`로 승격했습니다.
  - `gradle.properties` 내 Windows 경로 콜론 이스케이프(`C\:`)를 정비해 `lintRelease` 정적 분석을 오류 0건으로 통과시켰습니다.
- **Android 16 필수 동작 변경사항(Behavior Changes) 반영 (`AndroidManifest.xml`, `MainActivity.kt`)**:
  - **예측형 뒤로가기(Predictive Back) 활성화**: `<application>`에 `android:enableOnBackInvokedCallback="true"`를 선언해 `androidx.activity.compose.BackHandler` 기반 화면 전환 애니메이션과 네이티브 제스처가 자연스럽게 연동되도록 구성했습니다.
  - **대형 화면 적응형 멀티윈도우 전환(`sw >= 600dp`)**: API 36 대형 화면에서 무시되며 Play Console 경고를 유발하는 `android:screenOrientation="portrait"` 고정을 제거하고 `android:resizeableActivity="true"`와 `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|density|uiMode"`를 적용했습니다.
  - **상태 보존 및 Edge-to-Edge**: `MainActivity.kt`의 활성 게임 및 다이얼로그 상태를 `rememberSaveable`로 승격해 폴더블 펼침·화면 회전·멀티윈도우 크기 조절 시에도 진행 중인 게임과 사운드 재생이 끊기지 않도록 보강했습니다.
- **16KB 네이티브 페이지 정렬 및 `.\dist` 최신 산출물 검증**:
  - `aapt2 dump badging`, `bundletool dump manifest`, ELF 헤더 검사, `apksigner` 및 `jarsigner` 검증으로 `dist/com.jjalddalmark.minigamerullet-v1.7.4-release.aab`, `dist/com.jjalddalmark.minigamerullet-v1.7.4-release.apk`, `dist/com.jjalddalmark.minigamerullet-v1.7.4-debug.apk` 모두 `targetSdkVersion = 36`, 64비트 네이티브 라이브러리(`libandroidx.graphics.path.so`) `PT_LOAD` `0x4000`(`16384` bytes) 정렬 및 무압축 ZIP 오프셋 정렬(`mod 16384 = 0`)을 충족함을 확인하고 스토어 에셋 통합 패키지(`com.jjalddalmark.minigamerullet_Store_Assets_Package_v1.7.4.zip`)까지 `.\dist`에 갱신했습니다.

## Phase 16: 정식 Google AdMob 홈 하단 배너 · 앱 종료 및 최소화 전면 광고 연동 (테스트 ID 0건) [v1.7.5]
- **Google Mobile Ads SDK (`play-services-ads:25.5.0`) 및 무결성 빌드 검증 구축 (`admob.properties`, `app/build.gradle.kts`, `AndroidManifest.xml`, `proguard-rules.pro`)**:
  - `versionCode = 13`, `versionName = "1.7.5"` (`compileSdk = 36`, `targetSdk = 36`)로 버전을 상향하고 정식 게시자 접두사(`ca-app-pub-5019798166308009`) 정규식 검증을 빌드 타임과 런타임 양방향에 내장해 샘플·테스트 광고 ID 유입을 원천 차단했습니다.
  - `AndroidManifest.xml`에 `INTERNET`, `ACCESS_NETWORK_STATE`, `AD_ID` 권한과 `OPTIMIZE_INITIALIZATION` · `OPTIMIZE_AD_LOADING` 비동기 최적화 플래그를 추가하고 `bundle.language.enableSplit = false` 및 `useLegacyPackaging = false` 설정으로 한국어 단일 로케일과 `16KB` 네이티브 페이지 정렬을 함께 유지했습니다.
- **홈 화면 하단 배너 및 종료·최소화 전면 광고 + 카운트다운 UX 구현 (`AdMobCoordinator.kt`, `HomeScreen.kt`, `MainActivity.kt`, `AdMobCoordinatorTest.kt`)**:
  - `AdMobCoordinator.initializeAsync`가 `Dispatchers.IO`에서 `MobileAds`를 비동기 초기화(`setTestDeviceIds(emptyList())`)하고 종료용 `InterstitialAd`를 백그라운드 사전 로드하도록 구성해 메인 스레드 블로킹(`ANR`)을 방어했습니다.
  - `HomeScreen` 하단에 스크롤 목록과 분리된 `HomeBannerAdSlot`을 배치해 본 게임 11종 조작에 전혀 간섭하지 않으며 오프라인·미로드 상태에서는 빈 여백 없이 자동 접히도록 구현했습니다.
  - 홈 화면 상단 `최소화` 버튼 클릭 또는 홈 루트에서 뒤로가기(앱 종료) 입력 시 사전 로드된 전면 광고 노출(재생 중 어쿠스틱 배경음 자동 일시정지) 후 `ExitCountdownOverlay`(`2.5초` 카운트다운 진행 바, `계속 사용` · `최소화/종료` 전환 · `지금 종료/최소화`)가 표시되도록 연결했습니다.
- **개인정보처리방침 · 스토어 등록 원고 동기화 및 `.\dist` 산출물 갱신**:
  - `docs/privacy.html` (`dist/privacy.html`, `dist/store_listing/06_privacy_policy.html`)과 `dist/store_listing/01_store_listing_texts_ko.md` · `01_store_listing_copy_paste.txt`에 Google AdMob 광고 포함(`예`) 및 `AD_ID` 처리·재설정 안내를 반영하고 `.\dist` 내 정식 서명 `AAB`, `APK`, 디버그 `APK`, 스토어 통합 `ZIP`을 `v1.7.5` 기준으로 최신화했습니다.
