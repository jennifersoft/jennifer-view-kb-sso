# jennifer-view-kb-sso

## 인증키 발급 API 설정하기

 1. 관리 > 어댑터 및 실험실 > 실험실 탭 클릭
 2. 추가 버튼을 누르고, 종류는 API를 선택
 3. ID는 kb_plugin로 설정
 4. 경로에 파일선택을 해서 업로드 하지 말고, 절대 경로를 입력해야 함 (dist/kb-sso_jennifer-1.1.0.jar)
 5. 테이블에 설정이 추가되면, 해당 설정 로우를 선택하고, 옵션을 클릭
 6. KB_PASSWORD_SALT 옵션 추가 (기본값은 jennifer5, 인증키 생성시 보안을 위해 변경해주는게 좋음)
 7. KB_VALIDATE_TIMEOUT 옵션 추가 (기본값은 5000, 인증키 생성 후, 설정된 시간 안에 인증을 해야 함)

### 인증키 발급 API 사용하기

 1. 관리 > 인증 토큰 관리 > 추가 버튼 클릭
 2. 종류를 Plugin API를 선택하고, 저장 버튼 클릭
 3. http://${제니퍼5_호스트}/plugin/kbapi/authkey?user_id=${KB_사용자_아이디}&device_id=${KB_디바이스_아이디}&token=${제니퍼_인증_토큰}
 4. 제니퍼 인증 토큰은 [설정 > 인증 토큰 관리 > 추가 > Plugin API]로 생성할 수 있음


## 로그인 어댑터 설정하기

 1. 관리 > 어댑터 및 실험실 > 로그인 탭 클릭
 2. 추가 버튼을 누르고, 종류는 SSO를 선택
 3. ID는 kb_login로 설정
 4. 경로에 파일선택을 해서 업로드 하지 말고, 절대 경로를 입력해야 함 (dist/kb-sso_jennifer-1.1.0.jar)
 5. 클래스 란에는 com.aries.kb.login.KbLoginAdapter를 입력
 6. 테이블에 설정이 추가되면, 해당 설정 로우를 선택하고, 옵션을 클릭
 7. KB_JENNIFER_ID 옵션 추가 (기본값은 guest, 인증 성공시 제니퍼에 로그인하는 유저 아이디이며 제니퍼에 미리 생성되어 있어야 함)
 8. KB_JENNIFER_PASSWORD 옵션 추가 (기본값은 guest, 위와 동일)

### URL로 제니퍼 로그인하기

 1. 일단 인증키 발급 API를 호출하여, 인증키를 발급 받는다.
 2. KB_VALIDATE_TIMEOUT 옵션에 저장된 시간 이전에 URL로 제니퍼에 로그인한다
 3. http://${제니퍼5_호스트}/login/sso?user_id=${KB_사용자_아이디}&device_id=${KB_디바이스_아이디}&auth_key=${플러그인_생성_인증키}를 호출해야 한다.
