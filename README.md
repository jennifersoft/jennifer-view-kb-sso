# jennifer-view-kb-sso

## 인증키 발급 API 설정하기

 1. 관리 > 어댑터 및 실험실 > 실험실 탭 클릭
 2. 추가 버튼을 누르고, 종류는 API를 선택
 3. ID는 kb_plugin로 설정
 4. 경로에 파일선택을 해서 업로드 하지 말고, 절대 경로를 입력해야 함 (dist/kb-sso_jennifer-3.0.0.jar)
 5. 테이블에 설정이 추가되면, 해당 설정 로우를 선택하고, 옵션을 클릭

### 인증키 발급 API 사용하기

 1. 관리 > 인증 토큰 관리 > 추가 버튼 클릭
 2. 종류를 Plugin API를 선택하고, 저장 버튼 클릭
 3. http://${제니퍼5_호스트}/plugin/kbapi/authkey?user_id=${KB_사용자_아이디}&device_id=${KB_디바이스_아이디}&token=${제니퍼_인증_토큰}
 4. 제니퍼 인증 토큰은 [설정 > 인증 토큰 관리 > 추가 > Plugin API]로 생성할 수 있음
 5. 발급된 인증키는 10초 동안 한 번만 사용할 수 있으며, 같은 사용자와 디바이스로 새 인증키를 발급하면 이전 인증키는 무효화됨

`auth_key`는 `user_id`, `device_id`, 발급 시각(밀리초), 발급 순번, 보안 난수를 입력으로 HMAC-SHA256을 계산한 43자 URL-safe 문자열이다. 사용자와 디바이스는 길이를 구분해 입력하므로 단순 문자열 연결로 인한 혼동을 방지한다. HMAC 비밀키는 서버에서 자동 생성하며 고객사 추가 설정은 필요 없다. 같은 사용자·디바이스가 같은 밀리초에 재요청해도 발급 순번과 난수로 새 키를 발급한다. 키 만료는 시스템 시각 조정의 영향을 받지 않는 경과 시간으로 판단한다.


## 로그인 어댑터 설정하기

 1. 관리 > 어댑터 및 실험실 > 로그인 탭 클릭
 2. 추가 버튼을 누르고, 종류는 SSO를 선택
 3. ID는 kb_login로 설정
 4. 경로에 파일선택을 해서 업로드 하지 말고, 절대 경로를 입력해야 함 (dist/kb-sso_jennifer-3.0.0.jar)
 5. 클래스 란에는 com.aries.kb.login.KbLoginAdapter를 입력
 6. 테이블에 설정이 추가되면, 해당 설정 로우를 선택하고, 옵션을 클릭
 7. 사용자 아이디는 인증키 발급 API 호출시 user_id 매개변수의 값인 ${KB_사용자_아이디}로 지정됨
 8. KB_JENNIFER_PASSWORD 옵션 추가 (기본값은 guest, 위와 동일)

### URL로 제니퍼 로그인하기

 1. 일단 인증키 발급 API를 호출하여, 인증키를 발급 받는다.
 2. http://${제니퍼5_호스트}/login/sso?user_id=${KB_사용자_아이디}&device_id=${KB_디바이스_아이디}&auth_key=${플러그인_생성_인증키}를 호출해야 한다. (제한시간 10초)

로그아웃 후 다시 로그인할 때도 인증키 발급 API를 새로 호출한다. 같은 `user_id`와 `device_id`라도 매번 새 `auth_key`가 발급되며, 사용한 인증키는 재사용할 수 없다. 10초는 발급된 키의 유효시간이며, 새 키를 받기 위해 10초를 기다릴 필요는 없다.

발급 API URL의 `token`은 제니퍼의 Plugin API 호출 자격 증명이다. 매번 달라져야 하는 값은 발급 API 응답으로 받는 `auth_key`이다.

### 새 버전 적용 확인

API 실험실의 `kb_plugin`과 로그인 어댑터의 `kb_login`에서 사용하는 JAR를 모두 최신 `kb-sso_jennifer-3.0.0.jar`로 교체하고 새 JAR가 로드되도록 뷰서버를 재시작한다. 기존에 발급한 키는 재시작 후 사용할 수 없으므로 API를 다시 호출한다. 발급과 로그인은 같은 뷰서버의 인증키 저장소를 사용해야 한다.

새 버전의 발급 API 응답에는 다음 헤더가 포함된다.

- `X-KB-SSO-Version: 3.0.0`
- `X-KB-SSO-Issuance-Id`: 호출마다 생성되는 발급 추적 ID
- `Cache-Control: no-store`, `Pragma: no-cache`

발급 시 `AUTH_KEY_ISSUED version=3.0.0 issuance_id=...` INFO 로그를 남긴다. 이 로그에는 `token`이나 `auth_key`를 기록하지 않는다. 반복 호출에서 응답의 발급 추적 ID도 동일하면 응답 재사용 여부를 확인하고, 버전 헤더가 없거나 다른 버전이면 `kb_plugin`의 JAR 경로와 로딩 상태를 확인한다.

## 빌드하기

JDK 17 환경에서 다음 명령을 실행한다.

```shell
mvn clean test package -Pjennifer
```

빌드된 플러그인은 `dist/kb-sso_jennifer-3.0.0.jar`에 생성된다.
