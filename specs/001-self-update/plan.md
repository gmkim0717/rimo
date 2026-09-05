# 001 · 자동 업데이트 — 기술 계획 (plan)

상태: **확인됨 (2026-09-05), tasks 진행 가능**
근거 spec: `specs/001-self-update/spec.md` (확인됨 2026-09-05)

---

## 0. 이 plan에서 확정하는 프로젝트 전역 결정

001이 첫 feature이므로 앱 골격과 함께 아래가 여기서 정해진다. 이후 feature는 이걸 따른다.

| 항목 | 결정 | 이유 |
|---|---|---|
| 지원 OS | Android 9 (API 28) ~ Android 11 (API 30) 실사용. `minSdk 28`, `targetSdk 34` | 사용자 확인. 명단의 박스가 9와 11 |
| UI 스택 | **View 체계** (AppCompat + RecyclerView, 목록 화면부터 Leanback 추가). Compose 사용 안 함 | 1GB 박스 존재. CLAUDE.md 4절 기준 |
| 최약 기기 기준 | S905L3A, 1GB RAM, Android 9 | 헌법 7조 |
| applicationId | `com.rimo.player` (첫 배포 후 변경 불가) | 소속 도메인과 무관하게 하라는 요청. 프로젝트명 기반 중립 id |
| 빌드 | AGP 8.x, Kotlin 2.x, Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`), 단일 `app` 모듈 | 생태 기본값 |
| 패키지 구조 | `com.rimo.player.{data,domain,ui}` 3층 분리, 모듈 분리 없음 | CLAUDE.md 4절 |
| 언어 리소스 | `values/` = 영어(기본), `values-ko/`, `values-zh-rCN/` | FR-10의 "시스템 언어 → 없으면 영어"는 Android 리소스 해석이 그대로 해 줌 |
| 앱 내 언어 override 방식 | AppCompat 1.6+ `setApplicationLocales` (per-app locale, API 28에서도 동작). 001은 리소스만 준비, 호출은 002에서 | 직접 Locale 갈아끼우는 코드 안 짬 |

> AC-12a(언어 선택 규칙 단위 테스트)는 플랫폼 리소스 해석에 위임되므로 우리 코드가 없다. spec에서 삭제하고 AC-9a 실기기 확인으로 대체함 (2026-09-05 반영).

---

## 1. 전체 흐름

```
Application.onCreate
  └─ UpdateCoordinator.start()   (앱 스코프 코루틴, IO, 프로세스당 1회)
        ├─ 1. cleanup: updates/ 안의 versionCode <= 현재 인 apk 삭제
        ├─ 2. ready 파일 있으면 → state = ReadyToInstall(그 버전)
        ├─ 3. UPDATE_URL GET → UpdateInfoParser → UpdateInfo
        │       실패/형식오류 → 종료 (재시도 없음, 다음 실행)
        ├─ 4. info.versionCode > max(현재, ready) 이면
        │       ApkDownloader.download(info)  — RetryPolicy(5회, 지수 백오프)
        │       → sha256 검증 → 실패 시 파일 삭제, 종료
        │       → 성공 시 이전 ready 파일 삭제, ready 기록, state = ReadyToInstall
        └─ 끝

MainActivity (채널 목록 급 화면)
  └─ UpdatePromptGate 관찰: ReadyToInstall && !promptedThisProcess && !playbackActive
        → UpdatePromptDialogFragment 표시 (1회, promptedThisProcess = true)
             [지금 설치] → ApkInstaller.install(file)
             [나중에] / BACK → 닫기, 아무것도 안 함

ApkInstaller (PackageInstaller Session)
  → 시스템 확인 UI → InstallResultReceiver
       SUCCESS            → 시스템이 앱 종료 (다음 실행 때 cleanup이 파일 삭제)
       ABORTED(사용자 취소) → 아무것도 안 함 (다음 실행 때 다시 안내)
       FAILURE_*          → 파일 삭제 + ready 기록 삭제 (S4 마지막 줄)
```

**"이번 실행 중 다시 묻지 않음"** 은 메모리 플래그(프로세스 수명)로만 관리. 영속화하지 않는다. 프로세스가 죽으면 새 실행이다.

---

## 2. 데이터 모델

### 2.1 서버측 `update.json`

```json
{
  "versionCode": 2,
  "versionName": "0.2.0",
  "apkUrl": "https://example.invalid/rimo-0.2.0.apk",
  "sha256": "hex 64자",
  "apkSizeBytes": 12345678,
  "changelog": "text"
}
```

- 필수: `versionCode`(Long), `versionName`, `apkUrl`, `sha256`
- 선택: `apkSizeBytes`(저장공간 사전 확인용, 없으면 확인 생략), `changelog`(001에서 미표시)
- 알 수 없는 필드는 무시 (앞으로 필드를 추가해도 구버전 앱이 안 깨짐)
- `apkUrl`은 `https`만 허용. release 빌드는 cleartext 차단(Android 기본), debug 빌드만 LAN 테스트용 http 허용

### 2.2 앱 내

```
domain/update/
  UpdateInfo(versionCode, versionName, apkUrl, sha256, apkSizeBytes?, changelog)
  UpdateState = Idle | Checking | Downloading | ReadyToInstall(versionCode, versionName, file)
  RetryPolicy(maxAttempts=5, baseDelay=2s, factor=2, maxDelay=60s)   -- 순수 함수: attempt → delay 또는 포기
  VersionRule.isNewer(candidate, current, ready?)                     -- 순수 함수
```

### 2.3 영속 상태 — DataStore Preferences (`update_prefs`)

| key | 타입 | 의미 |
|---|---|---|
| `ready_version_code` | Long | 검증 완료된 설치 파일의 versionCode |
| `ready_version_name` | String | 안내 문구 표시용 |

파일 경로는 저장 안 함. 규칙으로 정한다: `filesDir/updates/<versionCode>.apk`.
`ready_version_code`가 있는데 파일이 없으면 → 기록 삭제, 정상 진행 (자기 치유).

`cacheDir` 대신 `filesDir`인 이유: S2("다음 실행 때 다시 받지 않고 안내")를 지키려면 시스템이 임의로 지우면 안 됨.

### 2.4 Room

001에서는 **사용 안 함**. 추가하지 않는다.

---

## 3. 모듈/클래스 경계

```
com.rimo.player
├── RimoApp                      @HiltAndroidApp, onCreate에서 UpdateCoordinator.start()
├── di/AppModule                 OkHttpClient, DataStore, 앱 스코프 CoroutineScope, Dispatchers 제공
├── domain/update/
│   ├── UpdateInfo, UpdateState
│   ├── RetryPolicy              순수. 단위 테스트 (AC-12)
│   ├── VersionRule              순수. 단위 테스트 (AC-10)
│   └── UpdateCoordinator        1절 흐름 오케스트레이션. StateFlow<UpdateState> 노출. Turbine 테스트
├── data/update/
│   ├── UpdateInfoParser         kotlinx.serialization. 단위 테스트 (AC-11)
│   ├── UpdateInfoFetcher        OkHttp GET → String
│   ├── ApkDownloader            OkHttp 스트리밍 → 파일, 동시에 SHA-256 계산, 저장공간 사전 확인. MockWebServer 테스트
│   ├── UpdateStore              DataStore 읽기/쓰기 + updates/ 디렉터리 관리 (cleanup, 경로 규칙)
│   ├── ApkInstaller             PackageInstaller Session 생성/쓰기/commit
│   └── InstallResultReceiver    BroadcastReceiver. STATUS_PENDING_USER_ACTION이면 시스템 UI 인텐트 실행
└── ui/
    ├── MainActivity             AppCompatActivity. 빈 화면 + UpdatePromptGate 관찰
    ├── update/UpdatePromptGate  ViewModel. (state, promptedThisProcess, playbackActive) → showPrompt 이벤트
    └── update/UpdatePromptDialogFragment
```

**의존 방향**: `ui → domain ← data`. `domain`은 Android 클래스에 의존하지 않는다 (`java.io.File`까지만). 그래서 AC-10/11/12 테스트가 Robolectric 없이 돈다.

**002 이후와의 계약**:
- 플레이어 화면은 `UpdatePromptGate.playbackActive`만 true/false로 갱신한다. 그 외 업데이트 코드를 몰라도 된다 (S3, AC-9).
- 언어 설정 화면(002)은 `AppCompatDelegate.setApplicationLocales`만 호출한다. 001은 리소스 3벌만 준비.

---

## 4. 설치 (PackageInstaller) 세부

- Manifest: `REQUEST_INSTALL_PACKAGES`
- Session: `MODE_FULL_INSTALL`. apk를 세션 스트림으로 복사한 뒤 `commit(PendingIntent)`
- PendingIntent: targetSdk 34이므로 **`FLAG_MUTABLE` 필수** (시스템이 extras를 채움). 대상 박스(9/11)에서는 안 터지지만 처음부터 맞게 간다.
- `canRequestPackageInstalls()`가 false면 [지금 설치] 시 `ACTION_MANAGE_UNKNOWN_APP_SOURCES` 설정 화면으로 보내고, 돌아오면 다시 시도. 이 설정 화면이 **없는** 박스가 있을 수 있음 → 7절.
- Android 9 vs 11: 11은 scoped storage지만 `filesDir`는 앱 전용이고 세션 스트림으로 복사하므로 FileProvider 불필요. 두 버전 코드 경로 동일.
- 서명 불일치 → `INSTALL_FAILED_UPDATE_INCOMPATIBLE` → FAILURE → 파일 삭제. 앱은 조용히, 개발자가 배포 확인 단계(OR-1 마지막)에서 발견.

---

## 5. UI 세부 (안내 다이얼로그)

- `DialogFragment` + 커스텀 레이아웃 (`LinearLayout`만, ConstraintLayout 안 씀). 시스템 `AlertDialog` 버튼은 박스마다 포커스 표시가 안 보이는 경우가 있어 직접 그린다.
- 버튼 2개: 포커스 시 3dp 테두리 + 1.05 scale (state-list drawable). 초기 포커스 = [나중에] (실수로 설치 시작 방지).
- 문구 최소 16sp. 다이얼로그 여백은 화면의 5% overscan 안쪽.
- BACK → `onCancel` → [나중에]와 동일 경로.
- 문자열: `update_ready_title`, `update_ready_body`(버전 이름 삽입), `update_install_now`, `update_later` × 3개 언어.

---

## 6. 골격 · 빌드 · 배포

- Manifest: `LAUNCHER` + `LEANBACK_LAUNCHER` 두 category, `leanback`/`touchscreen` `required=false`, `android:banner` 320×180 placeholder (못생겨도 됨).
- `BuildConfig.UPDATE_URL`: `gradle.properties`의 `rimo.updateUrl`에서 주입. 기본값 `https://example.invalid/update.json`. release 빌드가 기본값이면 빌드 실패시킴 (실수 방지, gradle 5줄).
- debug 빌드 전용 `network_security_config`로 cleartext 허용 → LAN에서 `python -m http.server`로 update.json/apk 서빙해 AC 검증.
- release 서명: `keystore.properties`(gitignore) → `signingConfigs.release`. keystore 파일도 gitignore.
- `.gitignore`: `local.properties`, `keystore.properties`, `*.jks`, `*.keystore`, `.gradle/`, `build/`, `.idea/`.
- `docs/release.md`: 버전 올리기(`versionCode`/`versionName`은 `app/build.gradle.kts` 한 곳) → `assembleRelease` → sha256 계산 → apk 업로드 → update.json 갱신 → 박스 1대 확인. keystore 백업 위치 기록란 포함.
- `scripts/make-update-json.sh`: apk 경로를 받아 sha256/size를 채운 update.json 출력. 10줄짜리 셸이며 gradle 플러그인이 아님.
- **호스팅 (확정)**: GitHub Releases에 apk, 저장소 `main`의 `dist/update.json` raw URL (`https://raw.githubusercontent.com/gmkim0717/rimo/main/dist/update.json`). 무료, HTTPS, 용량 제한 없음. **전제: 저장소가 공개여야 함.** 비공개면 raw URL에 인증이 필요해 앱이 받을 수 없다. 첫 배포 전 T-마지막 task에서 실제로 curl로 확인.

---

## 7. 위험과 대응

| 위험 | 대응 |
|---|---|
| 중국 ROM에서 PackageInstaller 확인 UI가 리모컨으로 조작 불가 / unknown-sources 설정 화면 부재 | 첫 배포 전 전 기종 AC-2 확인. 안 되는 기종은 plan B: `ACTION_VIEW` + FileProvider 방식 (별도 task, 001 범위 외) |
| 1GB 박스에서 다운로드 중 메모리 | 스트리밍 복사(8KB 버퍼), apk를 메모리에 올리지 않음 |
| 시청 중 다운로드가 대역폭 잠식 | spec 8절대로 감수. 문제되면 명단에 묻기 |
| Android 11 백그라운드 제한으로 다운로드 중 프로세스 종료 | 실패 처리. 다음 실행 때 재시도. WorkManager 도입 안 함 |
| 오래된 박스 CA 저장소로 HTTPS 실패 | Android 9+는 ISRG Root X1 포함. 발생하면 호스팅 CA를 바꿔 대응, 앱은 안 건드림 |

---

## 8. 의존성 (추가 전 승인 필요 — CLAUDE.md 8절)

**플러그인**: `com.android.application`, `org.jetbrains.kotlin.android`, `com.google.devtools.ksp`, `com.google.dagger.hilt.android`, `org.jetbrains.kotlin.plugin.serialization`

**라이브러리**:

| 용도 | 아티팩트 |
|---|---|
| 기본 | `androidx.core:core-ktx`, `androidx.appcompat:appcompat` (1.6+), `androidx.activity:activity-ktx`, `androidx.fragment:fragment-ktx`, `androidx.lifecycle:lifecycle-runtime-ktx`, `lifecycle-viewmodel-ktx` |
| DI | `com.google.dagger:hilt-android` + `hilt-compiler` (KSP) |
| 비동기 | `kotlinx-coroutines-android` |
| 네트워크 | `com.squareup.okhttp3:okhttp` |
| JSON | `kotlinx-serialization-json` ← 스택 표에 없음. Android 내장 `org.json`은 JVM 단위 테스트가 안 되고, Moshi보다 Kotlin 기본값에 가까움 |
| 설정 | `androidx.datastore:datastore-preferences` |
| 테스트 | `junit:junit` (4.13.2), `kotlinx-coroutines-test`, `app.cash.turbine:turbine`, `com.squareup.okhttp3:mockwebserver` |

**의도적으로 안 넣는 것**: Room, Media3, Leanback, WorkManager, Robolectric, ConstraintLayout, Material Components. 각각 필요한 feature에서 추가.

**테스트 프레임워크**: JUnit4로 확정 (2026-09-05). JUnit5는 서드파티 gradle 플러그인이 필요해 제외. CLAUDE.md 표도 갱신함.

---

## 9. 테스트 계획

| 대상 | 방식 | spec |
|---|---|---|
| `UpdateInfoParser` | JUnit: 정상 / 필수 필드 누락 / 타입 오류 / 빈 문자열 / 알 수 없는 필드 무시 / http URL 거부 | AC-11 |
| `VersionRule.isNewer` | JUnit: 높음·같음·낮음, ready 있는 경우 | AC-10 |
| `RetryPolicy` | JUnit: 5회 후 포기, 지연 2·4·8·16·32s, 상한 60s | AC-12 |
| `ApkDownloader` | MockWebServer: 정상 / 중간 끊김 후 재시도 성공 / sha 불일치 → 파일 없음 / 5회 실패 | S4 |
| `UpdateCoordinator` | Turbine: Idle→Checking→Downloading→Ready, fetch 실패→Idle, ready 파일 선존재 | 상태 흐름 |
| UI, PackageInstaller | 실기기 수동 (AC-1~9a). 절차는 tasks.md 마지막 task에 adb 명령까지 적음 | AC-1~9a |

---

## 10. 시간 추정 (상한 20h)

| 작업 | h |
|---|---|
| 프로젝트 골격, manifest, 서명 설정, gitignore, 빌드 확인 | 3 |
| domain: 모델, VersionRule, RetryPolicy + 테스트 | 2 |
| Parser + Fetcher + 테스트 | 2 |
| ApkDownloader (스트리밍, sha, 저장공간, 재시도) + MockWebServer 테스트 | 3 |
| UpdateStore + UpdateCoordinator + Turbine 테스트 | 3 |
| ApkInstaller + Receiver + unknown-sources 처리 | 3 |
| 다이얼로그 UI, 포커스, 문자열 3벌 | 2 |
| docs/release.md, make-update-json.sh, 실기기 AC 검증 | 2 |
| **합계** | **20** |

초과 시 먼저 잘라낼 것: `apkSizeBytes` 저장공간 사전 확인(IOException 처리로 대체), `make-update-json.sh`(수동 `sha256sum`).

---

## 11. 결정 기록

| 날짜 | 질문 | 결정 |
|---|---|---|
| 2026-09-05 | applicationId | `com.rimo.player`. 소속 도메인과 무관하게 (사용자 요청), 이름은 내가 정함 |
| 2026-09-05 | 호스팅 | GitHub Releases + 공개 저장소 raw `dist/update.json` |
| 2026-09-05 | 의존성 8절 | 승인 |
| 2026-09-05 | 테스트 프레임워크 | JUnit4 |
| 2026-09-05 | spec AC-12a | 삭제, AC-9a로 대체 (내 판단에 위임됨) |
| 2026-09-05 | CLAUDE.md 4절 갱신 | 승인, 반영 완료 |
