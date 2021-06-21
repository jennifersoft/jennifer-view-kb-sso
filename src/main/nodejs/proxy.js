const http = require('http');
const url = require('url');

const HOST_NAME = 'localhost';
const JENNIFER_PORT = 7900;
const PROXY_PORT = 3000;

function onRequest(req, res) {
    console.log('serve: ' + req.url);
    const queryData = url.parse(req.url, true).query;

    // // 제니퍼 인증하기
    const proxy = http.request({
        hostname: HOST_NAME,
        port: JENNIFER_PORT,
        path: '/login/sso',
        method: 'GET',
        headers: {
            ...req.headers,
            kb_user_id: queryData.user_id || '12345',
            kb_device_id: queryData.device_id || '67890',
            kb_auth_key: queryData.auth_key || 'Twc256uN9N4QrdCYHOoo1w==',
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
}

http.createServer(onRequest).listen(PROXY_PORT);
console.log('Listening on port ' + PROXY_PORT);

// [테스트 하는 방법]
// 1. 제니퍼 플러그인 API 호출
// -> http://localhost:7900/plugin/kbapi/authkey?user_id=12345&device_id=67890&token=6gCRegQCukG
// 2. 응답 값을 프록시 auth_key로 넘기기
// -> http://localhost:3000/?user_id=12345&device_id=67890&auth_key=Twc256uN9N4QrdCYHOoo1w==