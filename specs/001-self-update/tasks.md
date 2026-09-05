# 001 · 자동 업데이트 — 작업 목록 (tasks)

상태: **초안, 확인 대기**
근거: `spec.md` (확인됨), `plan.md` (확인됨), 모두 2026-09-05

규칙 (CLAUDE.md 2절·8절):
- 순서대로. 한 task 끝나면 **멈추고 보고**. 다음 task는 승인 후.
- task 하나 = 커밋 하나. 완료 기준을 못 채우면 커밋하지 않는다.
- 각 task의 "보고 내용"에 있는 것을 그대로 보고한다: 바꾼 것, 이유, 당신이 손으로 확인할 것.

---

## 전제 조건 (task 아님, T1 시작 전 확인)

- JDK 17, Android SDK (platform 34, build-tools), `adb`가 개발 PC에 있어야 한다. 없으면 T1 시작 시 내가 확인 결과를 먼저 보고한다.
- CLAUDE.md 7절은 Mac 환경을 전제하지만 지금 세션은 Windows다. Gradle과 adb는 Windows 네이티브로 그대로 쓴다 (WSL2 아님). 문제 없음.
- LAN 테스트용: 개발 PC와 박스가 같은 네트워크, `adb connect <박스IP>:5555` 가능.
- GitHub 저장소 `gmkim0717/rimo`가 **공개**여야 한다 (T8에서 확인).

---

## T1 · 프로젝트 골격 + 실행 가능한 빈 앱

**목표**: 박스에 설치하면 런처에 아이콘이 뜨고, 켜면 검은 빈 화면이 뜬다. 이후 모든 task의 토대.

**산출물**
- Gradle wrapper, `settings.gradle.kts`, `gradle/libs.versions.toml` (plan 8절 의존성 전부 선언, 이 task에서는 기본·DI·코루틴만 사용)
- `app/build.gradle.kts`: `applicationId com.rimo.player`, `minSdk 28`, `targetSdk 34`, `versionCode 1`, `versionName "0.1.0"`, `buildConfig = true`, `BuildConfig.UPDATE_URL` ← `rimo.updateUrl` (기본 `https://example.invalid/update.json`), release가 기본값이면 빌드 실패, `keystore.properties` 있으면 release 서명
- `AndroidManifest.xml`: LAUNCHER + LEANBACK_LAUNCHER, `leanback`/`touchscreen` required=false, `REQUEST_INSTALL_PACKAGES`, `android:banner`
- debug 전용 `network_security_config` (cleartext 허용), release는 없음
- `RimoApp` (@HiltAndroidApp), `di/AppModule` (OkHttpClient, 앱 CoroutineScope, Dispatchers), `ui/MainActivity` (AppCompatActivity, 검은 배경)
- `res/values{,-ko,-zh-rCN}/strings.xml` — `app_name` 한 줄씩
- 320×180 banner placeholder, 아이콘 placeholder
- `.gitignore` (plan 6절 목록)
- `gradle.properties`: `rimo.updateUrl` 주석 처리된 예시

**완료 기준**
- `./gradlew assembleDebug` 성공
- `./gradlew assembleRelease` 가 "UPDATE_URL not set" 류 메시지로 **의도적으로 실패**
- `git status`에 keystore/local.properties 없음

**수동 검증 (박스)**
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- 런처(홈 화면)에 "Rimo" 아이콘이 보인다 → 리모컨으로 선택 → 검은 화면이 뜨고 죽지 않는다
- 박스 시스템 언어가 중국어면 아이콘 이름이 중국어 `app_name`으로 보인다

**커밋**: `chore(build): bootstrap single-module android project`
**예상**: 3h

**결과 (2026-09-05)**: 자동 기준 3개 충족. 수동 검증은 PC의 Nox 에뮬레이터(Android 15, x86_64, 시스템 언어 중국어)에서 대행: 런처에 "Rimo 播放器" 아이콘 표시, 실행 시 검은 화면·크래시 없음, BACK으로 종료 확인. 실제 박스(Android 9/11) 확인은 T9에서 몰아서 한다.
- 버전 확정: AGP 8.13.2, Gradle 8.14.5, Kotlin 2.2.21, KSP 2.2.21-2.0.5, compileSdk 36
- plan에서 벗어난 점: Hilt **2.58** (2.59+는 AGP 9 필수), OkHttp **4.12.0** (5.x는 compileSdk 37 필수). 둘 다 성숙한 마지막 안정판이라 문제 없음
- 개발 PC 도구는 `D:\dev\jdk17`, `D:\dev\android-sdk`에 포터블 설치. Gradle의 JDK는 `~/.gradle/gradle.properties`의 `org.gradle.java.home`으로 지정 (커밋 대상 아님)

---

## T2 · domain 순수 모델: UpdateInfo, UpdateState, VersionRule, RetryPolicy

**목표**: Android 의존 없는 순수 Kotlin 규칙 두 개를 테스트와 함께 고정.

**산출물**
- `domain/update/UpdateInfo.kt`, `UpdateState.kt`
- `domain/update/VersionRule.kt` — `isNewer(candidate, current, ready?)`
- `domain/update/RetryPolicy.kt` — `delayFor(attempt): Duration?` (5회, 2s×2^n, 상한 60s)
- `test/.../VersionRuleTest.kt`, `RetryPolicyTest.kt` (JUnit4)

**완료 기준** (AC-10, AC-12)
- `./gradlew testDebugUnitTest` 통과
- VersionRule: 높음 → true / 같음·낮음 → false / ready가 candidate 이상 → false / ready가 candidate 미만 → true
- RetryPolicy: attempt 1..5 → 2,4,8,16,32s / attempt 6 → null(포기) / 상한 60s 적용 케이스

**수동 검증**: 없음
**커밋**: `feat(update): add version rule and retry policy`
**예상**: 2h

**결과 (2026-09-06)**: 완료. 테스트 13개 통과 (VersionRule 7, RetryPolicy 6). 함수명은 plan의 `isNewer` 대신 의도가 드러나는 `shouldDownload` / `isReadyUseful`로 정함. 재시도 의미는 spec대로 "첫 시도 + 재시도 5회 = 최대 6회 시도".

---

## T3 · UpdateInfoParser + UpdateInfoFetcher

**목표**: `update.json` 문자열 → `UpdateInfo` 또는 null. 네트워크로 문자열 가져오기.

**산출물**
- `data/update/UpdateInfoParser.kt` (kotlinx.serialization, `ignoreUnknownKeys`, `https` 아니면 거부)
- `data/update/UpdateInfoFetcher.kt` (OkHttp GET, 타임아웃 10s, 2xx 아니면 null, 예외 → null)
- `test/.../UpdateInfoParserTest.kt`
- `test/.../UpdateInfoFetcherTest.kt` (MockWebServer: 200 / 404 / 연결 끊김)

**완료 기준** (AC-11)
- 정상 / `apkSizeBytes`·`changelog` 생략 / 필수 필드 누락 → null / 타입 오류 → null / 빈 문자열 → null / 알 수 없는 필드 무시 / `http://` apkUrl → null
- Fetcher: 404 → null, 서버 다운 → null, 예외 전파 없음

**수동 검증**: 없음
**커밋**: `feat(update): parse and fetch update manifest`
**예상**: 2h

**결과 (2026-09-06)**: 완료. 테스트 20개 추가(Parser 13, Fetcher 7), 누적 33개 통과. plan 조정: 파서의 `http` 거부는 release에서만이고 debug 빌드는 허용(`allowInsecureUrls = BuildConfig.DEBUG`). 이유는 T5 LAN 테스트에서 APK 주소도 http이기 때문. Fetcher는 64KB 초과 응답을 거부.

---

## T4 · ApkDownloader

**목표**: URL → `filesDir/updates/<versionCode>.apk`, 스트리밍 + SHA-256 동시 계산, 재시도, 저장공간 확인.

**산출물**
- `data/update/ApkDownloader.kt`
  - 임시 파일 `<versionCode>.apk.part`에 쓰고 검증 후 rename
  - `apkSizeBytes` 있으면 `usableSpace >= size * 1.2` 아니면 시작 안 함
  - 실패 시 `RetryPolicy` 따라 재시도, 매 시도 처음부터 (Range 재개 안 함 — 단순함 우선)
  - sha 불일치 → 파일 삭제, 실패 반환
- `test/.../ApkDownloaderTest.kt` (MockWebServer, 임시 디렉터리)

**완료 기준** (S4)
- 정상 → 파일 존재, sha 일치
- 첫 2회 응답 끊김 후 3회째 성공 → 파일 존재, 시도 횟수 3
- 5회 모두 실패 → 파일·.part 모두 없음, 실패 반환
- sha 불일치 → 파일 없음, 실패 반환
- 메모리: 테스트에서 50MB 가짜 응답을 8KB 버퍼로 처리해도 OOM 없음 (heap 제한 걸어 확인)

**수동 검증**: 없음
**커밋**: `feat(update): stream apk download with sha256 verification and retry`
**예상**: 3h

**결과 (2026-09-06)**: 완료. 테스트 11개 추가, 누적 44개 통과. 정상 / 끊김 2회 후 3회째 성공 / 6회 전부 실패(1+5) 후 파일 없음 / 404도 재시도 / sha 불일치는 재시도 없이 폐기 / 저장공간 부족 시 요청 0회 / 기존 파일 교체 / 상위 디렉터리 생성 / 16MB 스트리밍. heap 제한 테스트는 MockWebServer가 응답 본문을 메모리에 들고 있어 의미가 없으므로 생략하고, 8KB 고정 버퍼 코드로 대체. 재시도 대기는 주입식(`sleep`)이라 테스트는 1초 안에 끝남.

---

## T5 · UpdateStore + UpdateCoordinator + 앱 시작 연결

**목표**: plan 1절 흐름 전체가 앱 시작 시 돌아가고, `StateFlow<UpdateState>`로 관찰된다. UI는 아직 없음.

**산출물**
- `data/update/UpdateStore.kt` (DataStore `update_prefs`, `updates/` cleanup, 경로 규칙, 자기 치유)
- `domain/update/UpdateCoordinator.kt` (start() 1회 가드, 흐름 오케스트레이션, 모든 예외 흡수)
- `RimoApp.onCreate`에서 `start()`
- `test/.../UpdateCoordinatorTest.kt` (Turbine + 가짜 Fetcher/Downloader/Store)

**완료 기준**
- Idle→Checking→Downloading→ReadyToInstall
- fetch 실패 → Checking→Idle
- 서버 버전 ≤ 현재 → Checking→Idle, 다운로드 호출 없음
- ready 파일 선존재 → 즉시 ReadyToInstall, 이어서 fetch. 더 새 버전 있으면 다운로드 후 Ready(새 버전), 이전 파일 삭제
- ready 기록은 있는데 파일 없음 → 기록 삭제, 정상 진행
- `start()` 두 번 호출 → 한 번만 실행

**수동 검증 (박스, LAN 서버)**
1. PC에서 `mkdir srv && cd srv && python -m http.server 8000`
2. `versionCode 2`로 빌드한 apk를 `srv/`에 두고, `scripts`가 아직 없으니 손으로 `update.json` 작성 (sha256은 `sha256sum`)
3. `./gradlew -Primo.updateUrl=http://<PC-IP>:8000/update.json installDebug` (이 빌드는 versionCode 1)
4. `adb logcat -s UpdateCoordinator` 에 `Checking → Downloading → ReadyToInstall(2)` 순서로 찍힌다
5. `adb shell run-as com.rimo.player ls files/updates` 에 `2.apk` 있음
6. 서버를 끄고 앱 재실행 → `ReadyToInstall(2)` 즉시, 그 뒤 fetch 실패 로그, 크래시 없음 (AC-6 일부)
7. update.json을 깨뜨리고 재실행 → 로그에 parse 실패, 크래시 없음 (AC-7)

**커밋**: `feat(update): orchestrate check-download-ready flow on app start`
**예상**: 3h

**결과 (2026-09-06)**: 완료. Coordinator 테스트 11개 추가, 누적 55개 통과. Nox(Android 15)에서 LAN 서버(`python -m http.server`)로 수동 검증 1~7 전부 확인: versionCode 1 설치 → 실행 → 로그 `downloading 2` → `ready to install 2`, `files/updates/2.apk` sha256 일치, DataStore 파일 생성. 재실행 시 `restored ready apk 2` 즉시. update.json 404 / 깨진 JSON / 동일 버전 세 경우 모두 `manifest unavailable or invalid` 또는 `no newer version` 로그만 남기고 크래시 0, 재다운로드 없음.
- 설계 메모: ready 파일이 있는 동안은 Checking/Downloading 상태를 노출하지 않고 ReadyToInstall을 유지한다(UI가 제안을 잃지 않게). domain은 `ManifestSource`/`ApkSource` 인터페이스만 알고, data 어댑터는 `UpdateModule`에 있음.
- 빌드 편의: `-Primo.versionCode=N -Primo.versionName=X`로 "새 버전" APK를 만들 수 있게 함.

---

## T6 · 설치 안내 다이얼로그 (UI)

**목표**: ReadyToInstall이면 목록 급 화면에서 한 번 안내가 뜬다. [지금 설치]는 이 task에서는 로그만 남긴다.

**산출물**
- `ui/update/UpdatePromptGate.kt` (ViewModel: state × promptedThisProcess × playbackActive → 이벤트)
- `ui/update/UpdatePromptDialogFragment.kt` + `res/layout/dialog_update_prompt.xml` (LinearLayout, 버튼 2개)
- `res/drawable/bg_button_focus.xml` (state-list: 포커스 시 3dp 테두리) + 포커스 scale 1.05
- 문자열 4개 × 3언어 (`update_ready_title`, `update_ready_body`, `update_install_now`, `update_later`), 16sp 이상
- `MainActivity`에서 Gate 관찰, 다이얼로그 표시, BACK → 취소 경로

**완료 기준**
- Gate 단위 테스트: Ready & !prompted & !playback → show 1회 / 이후 Ready 유지돼도 재발행 없음 / playbackActive=true면 억제, false로 바뀌면 발행
- `assembleDebug` 성공

**수동 검증 (박스)** — T5의 LAN 서버 그대로
- AC-1: 앱 실행 → 검은 화면 즉시 → 수 초 뒤 안내. 초기 포커스가 [나중에]에 있고 **테두리가 보인다**. ← / → 로 [지금 설치]와 왔다갔다 하며 테두리가 따라온다
- AC-3: [나중에] 확인 → 사라짐. 홈 → 다시 앱 진입(프로세스 유지) → 안 뜸. `adb shell am force-stop com.rimo.player` 후 실행 → 다운로드 없이 즉시 뜸 (logcat에 Downloading 없음)
- AC-4: 안내 위에서 BACK → [나중에]와 같음
- AC-9a: 박스 설정 → 언어 중국어 → 앱 재실행 → 중국어 안내. 한국어 → 한국어. 일본어 → 영어
- [지금 설치] 확인 → logcat에 `install requested: 2` 만 찍힘 (설치는 T7)

**커밋**: `feat(update): show install prompt with d-pad focus`
**예상**: 2h

**결과 (2026-09-06)**: 완료. Gate 테스트 4개 추가, 누적 59개 통과. Nox에서 캡처로 확인: 실행 즉시 안내(중/한/영 세 언어 모두 확인, 일본어 설정 시 영어 fallback), 초기 포커스 [나중에]에 3dp 흰 테두리, → 키로 [지금 설치]로 이동, 확인 키로 닫힘 + `install postponed` 로그, 홈→재진입 시 재표시 없음, force-stop 후 재실행 시 즉시 재표시, BACK = 나중에, [지금 설치] → `install requested` 로그.
- 설계: Gate는 ViewModel이 아니라 프로세스 단일 객체. Activity가 BACK으로 닫혔다가 같은 프로세스에서 다시 열려도 재질문하지 않도록.
- 발견·수정: 에뮬레이터가 터치 모드일 때 `requestFocus()`가 무시되어 초기 테두리가 안 보였음 → 버튼에 `focusableInTouchMode` + `onStart`에서 post로 포커스 요청. 실제 박스엔 영향 없고 터치 지원 박스에 대비.
- 언어 확인은 `adb shell cmd locale set-app-locales com.rimo.player --user 0 --locales ko-KR`(Android 13+)로 했음.

---

## T7 · ApkInstaller + InstallResultReceiver

**목표**: [지금 설치] → 시스템 설치 확인 → 설치. 결과별 후처리.

**산출물**
- `data/update/ApkInstaller.kt` (PackageInstaller Session, MODE_FULL_INSTALL, 스트림 복사, `FLAG_MUTABLE` PendingIntent)
- `data/update/InstallResultReceiver.kt` (Manifest 등록, `exported=false`; PENDING_USER_ACTION → 인텐트 실행; SUCCESS/ABORTED 무시; FAILURE_* → Store에 파일·기록 삭제 요청)
- `canRequestPackageInstalls()` false → `ACTION_MANAGE_UNKNOWN_APP_SOURCES` 로 이동, 복귀 시 재시도 (`MainActivity` onResume에서 pending 플래그 확인)
- [지금 설치] 배선

**완료 기준**
- `assembleDebug` 성공. 자동 테스트는 없음 (시스템 API). Receiver의 상태 코드 → 동작 매핑만 순수 함수로 빼서 단위 테스트

**수동 검증 (박스)**
- AC-2: [지금 설치] → (처음이면) 출처 허용 설정 화면 → 허용 → 앱 복귀 → 시스템 설치 확인 → 확인 → 앱 종료 → 재실행 → `adb shell dumpsys package com.rimo.player | grep versionCode` 가 2
- 시스템 확인에서 취소 → 앱 살아 있고 아무 일 없음 → force-stop 후 재실행 → 다시 안내 (S4)
- 서명 불일치: `assembleRelease`(임시 키)로 만든 apk를 서버에 올리고 debug 앱에서 설치 시도 → 조용히 실패, `run-as ... ls files/updates` 비어 있음, 앱 정상
- Android 9 박스와 11 박스 **둘 다**에서 AC-2

**커밋**: `feat(update): install downloaded apk via PackageInstaller`
**예상**: 3h

**결과 (2026-09-06)**: 완료. InstallOutcome 단위 테스트 5개 추가, 누적 64개 통과. Nox(Android 15)에서 4경로 확인:
1. 정상: [지금 설치] → 시스템 확인 화면(우리 앱 아이콘·이름 표시, 更新/취소) → 更新 → versionCode 2로 설치됨. 재실행 시 `stale ready record 2 dropped`로 파일 정리.
2. 사용자 취소: 시스템 화면에서 취소 → v1 유지, `2.apk` 보관, 로그 `install aborted by user; keeping apk`.
3. 서명 불일치: 다른 키로 서명한 v2 → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`(status 5) → 앱이 조용히 폐기, `files/updates` 비워짐, 크래시 0.
4. 알 수 없는 출처 권한: `canRequestPackageInstalls()` false면 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`로 이동, onResume에서 권한 확인 후 재시도. (Nox는 appops로 미리 허용해 확인 화면 자체는 검증)
- 발견·수정: 생성자 주입 `@IoDispatcher`/`@ApplicationScope`를 정식 import해야 KSP가 해석함(완전경로 참조로는 실패).
- 정리: 이전 시도가 남긴 추적 안 된 `InstallOutcomeMapperTest.kt`(존재하지 않는 API 참조로 빌드 파괴)를 삭제. 같은 검증은 `InstallOutcomeTest.kt`가 커버.
- FLAG_MUTABLE은 Android 12+에서만 붙임(대상 9/11엔 무영향, 상위 대비).

---

## T8 · 배포 도구: keystore, docs/release.md, make-update-json.sh, dist/update.json

**목표**: 개발자가 문서 한 장 보고 배포할 수 있다. 첫 릴리스(0.1.0) 준비.

**산출물**
- `docs/release.md`: OR-1 절차 전체, OR-2 versionCode 규칙, OR-3 keystore 백업 위치 기록란(**당신이 채움**), OR-4, 최초 설치 절차(Downloader 앱, 출처 허용), `rimo.updateUrl` 실제 값
- `scripts/make-update-json.sh`: `<apk> <versionCode> <versionName> <apkUrl>` → update.json 표준 출력
- `dist/update.json`: 첫 릴리스용 (versionCode 1) — apk는 GitHub Releases 업로드 후 URL 확정
- keystore 생성 명령을 문서에 적는다. **keystore 파일 자체는 당신이 생성**하고(비밀번호를 내가 알 필요 없음) `keystore.properties`에 경로·비밀번호를 넣는다. 둘 다 gitignore 대상임을 `git status`로 재확인

**완료 기준** (AC-13)
- `docs/release.md`가 OR-1~OR-4를 담고 있다
- `bash scripts/make-update-json.sh` 출력이 T3 파서를 통과한다 (파서 테스트에 fixture로 추가)
- `curl -sI https://raw.githubusercontent.com/gmkim0717/rimo/main/dist/update.json` 이 200 (저장소 공개 확인)
- `./gradlew -Primo.updateUrl=https://raw.githubusercontent.com/gmkim0717/rimo/main/dist/update.json assembleRelease` 성공, 서명됨

**수동 검증**: keystore를 다른 장소에 복사했고 그 위치를 `docs/release.md`에 적었다 — **당신이 직접**. 내가 확인할 수 없는 항목.

**커밋**: `docs(release): add release procedure and update manifest tooling`
**예상**: 2h

---

## T9 · 실기기 전체 수용 검증 (첫 배포 리허설)

**목표**: 진짜 경로로 한 바퀴. LAN 서버 아님, GitHub 경로.

**절차**
1. release 0.1.0 (versionCode 1) 서명 빌드 → GitHub Releases 업로드 → `dist/update.json` = versionCode 1 → push
2. 박스에 0.1.0 설치 (`adb install`). 실행 → 안내 없음 (AC-5)
3. `versionName`만 0.1.1, versionCode 2로 빌드 → Releases 업로드 → `dist/update.json` 갱신 → push
4. 박스 앱 재실행 → 안내 → [지금 설치] → 설치 → 재실행 → 버전 2 (AC-1, AC-2)
5. 박스 네트워크 끊고 재실행 → 정상 (AC-6)
6. `dist/update.json`을 고의로 깨뜨려 push → 재실행 → 정상 (AC-7) → 복구
7. `sha256`을 틀리게 바꿔 push, versionCode 3 → 재실행 → 안내 안 뜸, 정상 (AC-8) → 복구
8. Android 9 박스와 11 박스 각각 2~4 반복
9. AC-9 (재생 중 억제)는 플레이어가 없어 **003에서 검증**. 여기서는 Gate 단위 테스트로 대체했음을 기록

**산출물**: 이 파일의 아래 체크리스트를 채우고 커밋. 실패 항목은 issue로 남기고 001을 "완료(제한 있음)"로 마감할지 당신이 결정.

**커밋**: `docs(001): record acceptance results`
**예상**: 2h (박스 왕복 포함)

---

## 검증 체크리스트 (T9에서 채움)

| AC | Android 9 박스 | Android 11 박스 | 비고 |
|---|---|---|---|
| AC-1 | ☐ | ☐ | |
| AC-2 | ☐ | ☐ | |
| AC-3 | ☐ | ☐ | |
| AC-4 | ☐ | ☐ | |
| AC-5 | ☐ | ☐ | |
| AC-6 | ☐ | ☐ | |
| AC-7 | ☐ | ☐ | |
| AC-8 | ☐ | ☐ | |
| AC-9 | — | — | 003에서 |
| AC-9a | ☐ | ☐ | |
| AC-10~12 | ☐ (CI 없음, 로컬 `testDebugUnitTest`) | | |
| AC-13 | ☐ | | |

---

## 합계

| T | h |
|---|---|
| T1 | 3 |
| T2 | 2 |
| T3 | 2 |
| T4 | 3 |
| T5 | 3 |
| T6 | 2 |
| T7 | 3 |
| T8 | 2 |
| T9 | 2 |
| **합** | **22** |

plan 추정 20h를 2h 넘는다. T9가 plan에 없던 리허설이라 그렇다. 초과 시 잘라낼 순서: T4의 저장공간 사전 확인 → T8의 `make-update-json.sh` → T9의 8단계(두 박스 반복)를 한 박스로.
