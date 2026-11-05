# 미니게임 - 만능 룰렛 & 골프 복불복 `[v1.7.5]` (`com.jjalddalmark.minigamerullet`)

일상 모임과 회식, 직장·가정에서 무엇이든 간편하게 정하는 **만능 결정 룰렛+**와 골프 라운딩 현장에서 1번 홀 티샷 순서·그늘집 커피 내기·주사위·윷놀이·타로 운세를 즐길 수 있는 안드로이드 종합 아케이드 게임 앱입니다. 수록된 **전 게임 11종 모두에서 고해상도 결과 카드 이미지(`JPEG`)와 셋팅부터 게임 진행·클라이맥스 슬로우모션·최종 결정까지 전 과정을 담은 `MP4` 영상 공유**를 지원합니다. 따뜻한 수채화 감성의 라이트 테마 UI, 귀가 편안한 중저음 서사형 어쿠스틱 배경음악, 결정적 순간의 슬로우모션과 반전 특수효과를 유료 결제 없이 무료로 담았습니다.

## `[v1.7.5]` 신규 업데이트 — 정식 Google AdMob 홈 하단 배너 · 종료/최소화 전면 광고 탑재 (테스트 ID 0건)

- **홈 화면 하단 배너 광고 (`HomeBannerAdSlot`)**:
  - 본 게임 11종의 터치·회전·퍼팅 몰입감을 해치지 않도록 홈 로비 하단에만 분리된 슬림형 배너 광고 슬롯을 배치했습니다.
  - 오프라인이나 광고 미로드 상태에서는 빈 여백 없이 자동 접힘 처리되며 스크롤 목록 영역과 분리되어 마지막 항목까지 가림 없이 표시됩니다.
- **앱 종료 및 최소화 전면 광고 · 카운트다운 안내 (`AdMobCoordinator`, `ExitCountdownOverlay`)**:
  - 홈 화면 상단 `최소화` 버튼을 누르거나 홈 루트에서 뒤로가기(앱 종료)를 누르면 사전 로드된 전면 광고 노출 뒤 `2.5초` 카운트다운 진행 바(`계속 사용` · `최소화/종료` 전환 · `지금 종료/최소화`)가 표시됩니다.
  - 메인 UI 스레드 블로킹(`ANR`) 방지를 위해 `MobileAds.initialize`는 `Dispatchers.IO`에서 비동기 실행되며 전면 광고 재생 중에는 어쿠스틱 배경음악이 자동 일시정지됩니다.
- **테스트 광고 ID 원천 배제 및 정식 게시자 형식 검증 (`admob.properties`, `app/build.gradle.kts`)**:
  - 코드·매니페스트·빌드 스크립트·단위 테스트 전 구간에서 샘플·테스트 광고 ID를 완전 배제하고 정식 Google AdMob 게시자 접두사(`ca-app-pub-5019798166308009`) 정규식 검증을 내장했습니다.

## `[v1.7.4]` 업데이트 — Android 16 (`API 수준 36`) 타겟팅 및 보안·성능 표준 최적화

- **최신 Android 16 (`API 36`) 기반 빌드 및 Play Console 요구사항 충족**:
  - `compileSdk = 36`, `targetSdk = 36` (`platformBuildVersionCode = 36`, Android 16) 타겟팅을 적용해 Google Play Console 최신 API 수준 정책(`API 36 이상`)을 충족했습니다.
  - 예측형 뒤로가기 제스처(`android:enableOnBackInvokedCallback="true"`), 대형 화면·폴더블·멀티윈도우 적응형 레이아웃(`android:resizeableActivity="true"` + 무중단 `configChanges`), 전면 `Edge-to-Edge`, `64-bit` 네이티브 라이브러리 `16KB` 페이지 정렬(`PT_LOAD 0x4000`)을 모두 검증했습니다.

## `.\dist` 단일 산출물 폴더 구성 및 자동 갱신 파이프라인 (`v1.7.5`)

- **자체 테스트 · 단말 배포 · 스토어 등록 전 산출물 `.\dist` 단일 통합**:
  - `dist/com.jjalddalmark.minigamerullet-v1.7.5-release.aab`: Google Play Console 업로드용 정식 Release 서명 App Bundle (`versionCode = 13`, `targetSdk = 36`)
  - `dist/com.jjalddalmark.minigamerullet-v1.7.5-release.apk`: 자체 배포 및 실기기 최종 검수용 정식 Release 서명 `APK` (`targetSdk = 36`)
  - `dist/com.jjalddalmark.minigamerullet-v1.7.5-debug.apk`: 개발·QA 자체 디버그 테스트용 `APK` (`targetSdk = 36`)
  - `dist/store_listing/`: 스토어 등록 텍스트(`01_store_listing_*.txt/.md`), 앱 아이콘(`02_app_icon_512x512.png`), 그래픽 이미지(`03_feature_graphic_1024x500.png`), Full HD 홍보 동영상 2종(`04_promo_video_*.mp4`), 휴대전화 스크린샷 `8`종 풀세트(`05_phone_screenshots_9x16/`), 무프레임 원본 캡처(`05_phone_screenshots_raw/`), 개인정보처리방침(`06_privacy_policy.html`)
  - `dist/com.jjalddalmark.minigamerullet_Store_Assets_Package_v1.7.5.zip`: `store_listing/` 전체 자료 통합 압축본
- **빌드 직후 `.\dist` 자동 동기화 내장 (`app/build.gradle.kts`)**:
  - `assembleDebug`, `assembleRelease`, `bundleRelease` 실행 시 각각 `syncDebugApkToDist`, `syncReleaseApkToDist`, `syncReleaseBundleToDist` 후처리 태스크(`finalizedBy`)가 자동 구동되어 구버전 중복 바이너리를 정리하고 `.\dist` 폴더를 최신 빌드본으로 즉시 갱신합니다.

## 수록 게임 전체 목록 (총 11종 · 전 게임 사진 및 영상 공유 지원)

### 만능 결정 룰렛 — 일상 범용 1종
| 게임명 | 핵심 규칙 및 공유 지원 |
| --- | --- |
| 만능 결정 룰렛+ | 언덕 위 원목 풍차 스테이지에서 맞춤 항목·가중치 비율·12종 반전 특수효과·영구 보관함 저장 및 결과 카드(`JPEG`)·회전 전 과정 `MP4` 영상 공유를 지원합니다. |

### 주사위 · 윷 · 타로 — 보드·운세 3종
| 게임명 | 핵심 규칙 및 공유 지원 |
| --- | --- |
| 행운의 더블 주사위 | 참가자별로 2개의 원목 주사위를 굴려 합계가 가장 높은 사람이 1위로 승리합니다. 스코어보드·주사위 굴림 전 과정 `MP4` 영상 및 결과 카드 공유를 지원합니다. |
| 잔디밭 민속 윷놀이 | 4개의 자작나무 윷가락을 던져 도·개·걸·윷·모·빽도와 앞선 말 잡기 보너스로 승자와 패자를 가립니다. 10칸 말판 레이스와 윷 투구 전 과정 `MP4` 영상 및 결과 카드 공유를 지원합니다. |
| 숲속 오두막 타로 운세 | 종합·라운딩·재물·애정·성취·건강운 6종 주제 중 하나를 골라 22장 메이저 아르카나 점괘 풀이와 행운 점수 등수를 확인하고 카드 오픈 전 과정 `MP4` 영상과 결과 카드를 공유합니다. |

### 1홀 티샷 순서 정하기 — 오너 추첨 3종
| 게임명 | 핵심 규칙 및 공유 지원 |
| --- | --- |
| 드라이버 티 바운스 | 원형으로 둘러선 동반자 중앙의 드라이버 헤드에 나무 티를 튕겨 티 끝이 가리킨 1번 오너와 타순을 정하며 티 회전 지목 전 과정 `MP4` 영상과 결과 카드를 공유합니다. |
| 나무 홈 뽑기 막대 | 가죽 통에 꽂힌 나무 막대를 뽑아 아래쪽에 파인 홈 개수로 타순을 결정하며 막대 상승 및 눈금 공개 전 과정 `MP4` 영상과 결과 카드를 공유합니다. |
| 숲속 오솔길 사다리 | 동반자 골프공이 통나무 다리 갈림길을 따라 홀컵으로 내려가며 안개 구간 통과 및 사다리 주행 전 과정 `MP4` 영상과 결과 카드를 공유합니다. |

### 커피 사기 · 꼴등 · 팀 정하기 — 라운딩 복불복 4종
| 게임명 | 핵심 규칙 및 공유 지원 |
| --- | --- |
| 니어핀 퍼팅 치킨게임 | 퍼터를 당겨 홀컵으로 공을 굴려 연못 해저드에 빠지거나 가장 먼 꼴등이 커피 당첨자가 되며 동반자별 퍼팅 궤적 전 과정 `MP4` 영상과 결과 카드를 공유합니다. |
| 숲속 골프공 바구니 | 버드나무 바구니 속 9개의 공 중 황금 도토리 도장이 찍힌 당첨 볼 하나를 피하는 복불복이며 순차 볼 오픈 전 과정 `MP4` 영상과 결과 카드를 공유합니다. |
| 언덕 위 풍차 룰렛 | 초원 언덕 위 원목 풍차 날개와 바퀴를 돌려 커피·간식·맞춤 미션을 뽑으며 선수입장 연동·풍차 보관함·풍차 회전 전 과정 `MP4` 영상과 결과 카드 공유를 지원합니다. |
| 행운의 나무 패 뒤집기 | 섞인 나무 패를 골라 뒤집어 커피 당첨자를 뽑거나 청솔 A팀과 단풍 B팀으로 편을 나누며 나무 패 3D 오픈 전 과정 `MP4` 영상과 결과 카드를 공유합니다. |

## 기술 구성 및 빌드 방법

- 앱 명칭: `미니게임 - 만능 룰렛 & 골프 복불복`
- 패키지 이름(`applicationId` & `namespace`): `com.jjalddalmark.minigamerullet`
- Android API 수준: `compileSdk = 36`, `targetSdk = 36` (Android 16), `minSdk = 26`
- Google AdMob 설정: `admob.properties` (`ADMOB_APP_ID`, `ADMOB_BANNER_HOME_UNIT_ID`, `ADMOB_INTERSTITIAL_EXIT_UNIT_ID`)
- 산출물 통합 디렉터리: `.\dist` (`assembleDebug` · `assembleRelease` · `bundleRelease` 완료 시 자동 동기화)
- 빌드 명령어: `./gradlew testDebugUnitTest lintRelease assembleDebug assembleRelease bundleRelease`
- 스토어 등록용 개인정보처리방침 URL: https://herosonsa1.github.io/acadeGame/privacy.html (로컬 사본: `dist/privacy.html`)
