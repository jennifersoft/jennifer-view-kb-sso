const http = require('http');
const url = require('url');

const HOST_NAME = 'localhost';
const JENNIFER_PORT = 7900;
const JENNIFER_TOKEN = '6gCRegQCukG';
const PROXY_PORT = 3000;

function onRequest(req, res) {
    console.log('serve: ' + req.url);

    const queryData = url.parse(req.url, true).query;
    const apiPath = `/plugin/kbapi/authkey?user_id=${queryData.user_id}&device_id=${queryData.device_id}&token=${JENNIFER_TOKEN}`;
    const options = {
        hostname: HOST_NAME,
        port: JENNIFER_PORT,
        method: 'GET',
    };

    // 1. 제니퍼 플러그인을 통해 KB 인증 토큰 가져오기
    http.request({
        ...options,
        path: apiPath,
    }, r => {
        r.setEncoding('utf8');

        r.on('data', auth_key => {
            console.log('auth_key : ' + auth_key);

            // 2. 제니퍼로 라우팅하여, SSO 어댑터를 통해 인증하기
            const proxy = http.request({
                ...options,
                path: '/login/sso',
                headers: {
                    ...req.headers,
                    kb_user_id: queryData.user_id || '12345',
                    kb_device_id: queryData.device_id || '67890',
                    kb_auth_key: auth_key || 'Twc256uN9N4QrdCYHOoo1w==',
                    host: `${HOST_NAME}:${JENNIFER_PORT}`,
                },
            }, r => {
                res.writeHead(r.statusCode, r.headers);
                r.pipe(res, {
                    end: true,
                });
            });

            req.pipe(proxy, {
                end: true,
            });
        });
    }).end();
}

http.createServer(onRequest).listen(PROXY_PORT);
console.log('Listening on port ' + PROXY_PORT);

// [테스트 하는 방법]
// 1. 로컬 제니퍼에 KB 관련 플러그인과 어댑터가 설정되어 있어야 함
// 2. 응답 값을 프록시 auth_key로 넘기기
// -> http://localhost:3000/?user_id=12345&device_id=67890