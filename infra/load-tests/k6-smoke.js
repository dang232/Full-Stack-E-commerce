import http from 'k6/http';
import { check, group } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
  },
};

function expectOk(response, name) {
  return check(response, {
    [`${name} returns 2xx`]: (res) => res.status >= 200 && res.status < 300,
  });
}

export default function () {
  group('gateway smoke', () => {
    expectOk(http.get(`${BASE_URL}/health`), 'GET /health');
    expectOk(http.get(`${BASE_URL}/products`), 'GET /products');
    expectOk(http.get(`${BASE_URL}/search?q=ao`), 'GET /search?q=ao');
  });
}
