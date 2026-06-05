export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';

export const defaultHeaders = {
  'Content-Type': 'application/json',
  'Authorization': `Bearer ${AUTH_TOKEN}`,
};

export const thresholds = {
  http_req_duration: ['p(95)<500', 'p(99)<2000'],
  http_req_failed: ['rate<0.01'],
};
