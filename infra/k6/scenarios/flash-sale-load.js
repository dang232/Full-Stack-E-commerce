import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, defaultHeaders, thresholds } from '../config.js';

export const options = {
  scenarios: {
    flash_sale_spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 100 },
        { duration: '30s', target: 1000 },
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds,
};

export default function () {
  const productId = 'flash-sale-product-1';

  const stockRes = http.get(`${BASE_URL}/flash-sale/stock/${productId}`);
  check(stockRes, { 'stock check 200': (r) => r.status === 200 });

  const reserveRes = http.post(
    `${BASE_URL}/flash-sale/reserve`,
    JSON.stringify({ productId, quantity: 1 }),
    { headers: defaultHeaders }
  );
  check(reserveRes, {
    'reserve 200 or 409': (r) => r.status === 200 || r.status === 409,
  });

  sleep(0.1);
}
