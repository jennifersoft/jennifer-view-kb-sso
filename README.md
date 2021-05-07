# jennifer-view-kb-sso

## 1. 인증키 발급 API 설정하기

 1. 관리 > 어댑터 및 실험실 > 실험실 탭 클릭
 2. 추가 버튼을 누르고, 종류는 API를 선택
 3. ID는 kb_plugin로 설정
 4. 경로에 파일선택을 해서 업로드 하지 말고, 절대 경로를 입력해야 함 (dist/kb-sso_jennifer-1.0.0.jar)
 5. 테이블에 설정이 추가되면, 해당 설정 로우를 선택하고, 옵션을 클릭
 6. KB_PASSWORD_SALT 옵션 추가 (기본값은 jennifer5, 인증키 생성시 보안을 위해 변경해주는게 좋음)
 7. KB_VALIDATE_TIMEOUT 옵션 추가 (기본값은 3000, 인증키 생성 후, 설정된 시간 안에 인증을 해야 함)
