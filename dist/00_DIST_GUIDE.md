# 미니게임 - 만능 룰렛 & 골프 복불복 (`v1.7.5`) — `dist` 통합 산출물 안내서

자체 테스트, 단말 직접 배포, Google Play Console 및 원스토어 등록에 필요한 모든 최종 산출물과 스토어 에셋 자료가 본 `dist` 폴더에 통합 저장되어 있습니다. 본 빌드는 **Android 16 (`API 수준 36`, `compileSdk = 36`, `targetSdk = 36`)** 타겟팅과 보안·성능 최적화(예측형 뒤로가기 `enableOnBackInvokedCallback`, 대형 화면 적응형 멀티윈도우 `resizeableActivity`, `Edge-to-Edge`, `16KB` 네이티브 페이지 정렬) 및 **Google AdMob 홈 하단 배너 · 종료/최소화 전면 광고**(테스트 광고 ID 0건 검증)를 모두 적용한 정식 Release 서명본입니다.

## 1. 실행 · 설치 · 마켓 업로드 패키지 (`dist/` 직하위)

| 파일명 | 용도 및 API 36 · 서명 검증 상태 |
| --- | --- |
| `com.jjalddalmark.minigamerullet-v1.7.5-release.aab` | Google Play Console 프로덕션·테스트 트랙 업로드용 정식 Release 서명 App Bundle (`versionCode = 13`, `targetSdk = 36`, `compileSdk = 36`) |
| `com.jjalddalmark.minigamerullet-v1.7.5-release.apk` | 자체 배포 및 실기기 최종 검수용 정식 Release 서명 설치 패키지 (`APK Signature Scheme v2`, `targetSdk = 36`) |
| `com.jjalddalmark.minigamerullet-v1.7.5-debug.apk` | 개발·QA 자체 디버그 테스트용 설치 패키지 (`targetSdk = 36`) |
| `com.jjalddalmark.minigamerullet_Store_Assets_Package_v1.7.5.zip` | 아래 `store_listing/` 하위 모든 스토어 등록 자료를 하나로 묶은 통합 압축본 |
| `privacy.html` | 스토어 등록용 개인정보처리방침 로컬 원본 (라이브 URL: https://herosonsa1.github.io/acadeGame/privacy.html) |

## 2. 스토어 에셋 등록 자료 (`dist/store_listing/`)

| 파일 · 폴더명 | Google Play Console 등록 위치 및 규격 |
| --- | --- |
| `01_store_listing_copy_paste.txt` | 앱 이름(`20`자/`30`자), 간단한 설명(`69`자/`80`자), 자세한 설명, 출시 노트(`v1.7.5`) 복사용 순수 텍스트 |
| `01_store_listing_texts_ko.md` | 국·영문 스토어 등록 원고 및 광고 포함(`예`)·데이터 보안(`AD_ID`)·콘텐츠 등급(`IARC`) 콘솔 설문 입력 가이드 |
| `02_app_icon_512x512.png` | 기본 스토어 등록정보 -> 앱 아이콘 (`512 x 512 px`, `32-bit RGBA` Full-Bleed `PNG`) |
| `02_app_icon_rounded_preview_512x512.png` | `20%` 라운딩 마스크 미리보기 및 외부 홍보용 아이콘 (`512 x 512 px`) |
| `03_feature_graphic_1024x500.png` | 기본 스토어 등록정보 -> 그래픽 이미지 (`1024 x 500 px`, `PNG`) |
| `04_promo_video_16x9_1920x1080.mp4` | YouTube 업로드 후 동영상 URL 등록용 가로형 Full HD 홍보 영상 (`1920 x 1080 px`, `34.0`초) |
| `04_promo_video_9x16_1080x1920.mp4` | YouTube Shorts · 모바일 홍보용 세로형 Full HD 홍보 영상 (`1080 x 1920 px`, `34.0`초) |
| `05_phone_screenshots_9x16/` | 휴대전화 스크린샷 슬롯 등록용 `8`종 풀세트 (`01`~`08`, 각 `1080 x 1920 px` `9:16` `PNG`) |
| `05_phone_screenshots_raw/` | 보조 마켓 제출용 무프레임 순수 인앱 캡처 `8`종 (`1080 x 2400 px` `PNG`) |
| `06_privacy_policy.html` | Google AdMob 및 `AD_ID` 안내가 반영된 개인정보처리방침 HTML 문서 사본 |
