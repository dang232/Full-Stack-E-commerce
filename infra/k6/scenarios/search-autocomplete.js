import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, thresholds } from '../config.js';

export const options = {
  scenarios: {
    autocomplete_burst: {
      executor: 'constant-vus',
      vus: 50,
      duration: '1m',
    },
  },
  thresholds: {
    ...thresholds,
    'http_req_duration{name:autocomplete}': ['p(95)<200'],
  },
};

const prefixes = ['ao', 'quan', 'giay', 'tui', 'dien', 'laptop', 'iphone', 'samsung'];

export default function () {
  const prefix = prefixes[Math.floor(Math.random() * prefixes.length)];

  const res = http.get(`${BASE_URL}/search?q=${prefix}&page=0&size=10`, {
    tags: { name: 'autocomplete' },
  });
  check(res, {
    'search 200': (r) => r.status === 200,
    'has results': (r) => JSON.parse(r.body).data !== null,
  });

  sleep(0.5);
}
