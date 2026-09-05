# 릴리스 절차

이 문서 하나만 보고 배포할 수 있어야 한다. 앱스토어를 쓰지 않으므로 업데이트 통로는 이 절차가 전부다.

호스팅: GitHub Releases에 APK를 올리고, 저장소 `main` 브랜치의 `dist/update.json`을 앱이 읽는다.
앱이 보는 주소:
```
https://raw.githubusercontent.com/gmkim0717/rimo/main/dist/update.json
```

---

## 0. 최초 1회만 (아직 안 했으면 먼저)

### 0-1. GitHub 저장소를 공개로 만든다

현재 원격은 `https://github.com/gmkim0717/rimo.git`로 설정돼 있지만 **저장소가 아직 GitHub에 없다.**

1. GitHub에서 `rimo` 저장소를 만든다. **Public**으로 만든다.
   - Private이면 `raw.githubusercontent.com` 주소에 인증 토큰이 필요해서 앱이 update.json을 못 읽는다. 반드시 Public.
   - 소스가 공개돼도 문제없다. 이 저장소에는 실제 채널 소스나 계정이 들어가지 않는다 (헌법 제1조, CLAUDE.md 3절).
2. 로컬을 밀어 올린다:
   ```
   git push -u origin main
   ```
3. 공개 확인 (200이 나와야 함):
   ```
   curl -sI https://raw.githubusercontent.com/gmkim0717/rimo/main/dist/update.json
   ```

### 0-2. 서명 키(keystore)를 만들고 **다른 곳에 백업한다**

키를 잃으면 열 명 전원이 삭제 후 재설치해야 한다. 모든 배포판은 같은 키로 서명한다.

1. 키 생성 (Windows 개발 PC 기준, JDK 17):
   ```
   "D:\dev\jdk17\bin\keytool.exe" -genkeypair ^
     -keystore rimo-release.jks -alias rimo -keyalg RSA -keysize 2048 -validity 10000 ^
     -storepass <STORE_PW> -keypass <KEY_PW> -dname "CN=Rimo"
   ```
   `<STORE_PW>` / `<KEY_PW>`는 직접 정한다. 이 문서에 비밀번호를 적지 마라.

2. **즉시 백업.** keystore 파일을 최소 두 곳에 복사한다 (예: USB, 클라우드 드라이브, 다른 PC).
   비밀번호는 비밀번호 관리자에 따로 저장한다.

3. 백업 위치를 아래에 기록한다 (**당신이 채운다**):

   | 항목 | 위치 |
   |---|---|
   | keystore 원본 | `D:\...\rimo-release.jks` (개발 PC) |
   | 백업 1 | ____________________ |
   | 백업 2 | ____________________ |
   | 비밀번호 보관 | ____________________ |

4. 프로젝트 루트에 `keystore.properties`를 만든다 (이 파일은 `.gitignore`에 있어 커밋되지 않는다):
   ```
   storeFile=../rimo-release.jks
   storePassword=<STORE_PW>
   keyAlias=rimo
   keyPassword=<KEY_PW>
   ```
   `storeFile` 경로는 프로젝트 루트 기준 상대경로다. keystore를 프로젝트 밖에 두면 그에 맞게 적는다.

### 0-3. 첫 설치 (열 명에게)

1. 박스에 [Downloader](https://www.aftvnews.com/downloader/) 앱을 설치한다 (Downloader 코드로 배포).
2. Downloader에서 `v0.1.0` 릴리스의 APK 짧은 주소를 입력해 받는다.
3. 설치 시 "알 수 없는 출처" 허용을 물으면 허용한다 (박스마다 앱별로 한 번 켜야 할 수 있다).
4. Downloader가 안 되는 박스는 USB로 APK를 옮기거나 `adb install`.

---

## 1. 매 배포 (버그 수정판 낼 때)

### 1-1. 버전 번호 올리기

`app/build.gradle.kts`의 `defaultConfig`에서 두 값을 올린다:

- `versionCode` = 정수. **직전 배포보다 반드시 크게.** 같거나 작으면 시스템이 설치를 거부한다.
- `versionName` = 표시용 문자열 (예: `0.2.0`).

한 곳만 고치면 된다.

### 1-2. 서명된 릴리스 APK 만들기

```
./gradlew clean assembleRelease
```

- `keystore.properties`가 있어야 서명된다. 없으면 릴리스가 서명되지 않는다.
- `rimo.updateUrl`이 자리표시자(`example.invalid`)면 빌드가 **의도적으로 실패**한다. `gradle.properties`에 실제 주소를 넣거나 다음처럼 넘긴다:
  ```
  ./gradlew clean assembleRelease -Primo.updateUrl=https://raw.githubusercontent.com/gmkim0717/rimo/main/dist/update.json
  ```
- 결과: `app/build/outputs/apk/release/app-release.apk`

배포용 파일명은 `rimo-<versionName>.apk`로 정한다 (예: `rimo-0.2.0.apk`).

### 1-3. GitHub Release 만들고 APK 올리기

1. GitHub에서 `v<versionName>` 태그로 릴리스를 만든다 (예: `v0.2.0`).
2. `app-release.apk`를 `rimo-0.2.0.apk` 이름으로 업로드한다.
3. 업로드된 파일의 다운로드 주소를 복사한다. 형태:
   ```
   https://github.com/gmkim0717/rimo/releases/download/v0.2.0/rimo-0.2.0.apk
   ```

### 1-4. update.json 갱신하고 push

스크립트로 만든다 (sha256과 크기를 자동으로 채운다):

```
scripts/make-update-json.sh \
  app/build/outputs/apk/release/app-release.apk \
  2 0.2.0 \
  https://github.com/gmkim0717/rimo/releases/download/v0.2.0/rimo-0.2.0.apk \
  "고친 내용 한 줄" \
  > dist/update.json

git add dist/update.json
git commit -m "release: 0.2.0"
git push
```

`versionCode`(위 예의 `2`)는 1-1에서 올린 값과 같아야 한다.

### 1-5. 박스 한 대에서 확인

배포를 끝내기 전에 반드시 실제 박스 한 대에서 확인한다:

1. 그 박스에 직전 버전이 깔려 있어야 한다.
2. 앱을 켠다 → 잠시 뒤 설치 안내가 뜬다 → [지금 설치] → 시스템 확인 → 새 버전으로 바뀐다.
3. 안내가 안 뜨거나 설치가 거부되면 배포하지 말고 원인을 찾는다. 흔한 원인:
   - `versionCode`를 안 올렸다 (같은 값이면 거부).
   - 서명 키가 이전 배포와 다르다 (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
   - `dist/update.json`의 `sha256`이나 `apkUrl`이 실제 파일과 안 맞는다.

---

## 참고: 확인 시점과 동작

- 앱은 **시작할 때 한 번** update.json을 확인한다. 박스를 며칠 켜둔 상태면 다음 재시작 때 업데이트를 받는다.
- 새 버전을 찾으면 조용히 받아두고, 다운로드가 끝난 뒤 [지금 설치]/[나중에]만 묻는다.
- [나중에]를 누르면 이번 실행엔 다시 안 묻고, 다음 실행 때 다시 받지 않고 바로 안내한다.
- 모든 실패(네트워크 없음, 깨진 update.json, 서명 불일치 등)는 조용히 처리되고 앱은 죽지 않는다.
