import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, thresholds } from '../config.js';

export const options = {
  scenarios: {
    webhook_flood: {
      executor: 'constant-arrival-rate',
      rate: 200,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 50,
      maxVUs: 100,
    },
  },
  thresholds,
};

export default function () {
  const payload = JSON.stringify({
    type: 'payment_intent.succeeded',
    data: {
      object: {
        id: `pi_test_${__VU}_${__ITER}`,
        amount: 100000,
        currency: 'vnd',
        metadata: { orderId: `order-${__VU}-${__ITER}` },
      },
    },
  });

  const res = http.post(`${BASE_URL}/payment/stripe/webhook`, payload, {
    headers: { 'Content-Type': 'application/json', 'Stripe-Signature': 'test' },
  });
  check(res, { 'webhook accepted': (r) => [200, 400].includes(r.status) });

  sleep(0.05);
}
