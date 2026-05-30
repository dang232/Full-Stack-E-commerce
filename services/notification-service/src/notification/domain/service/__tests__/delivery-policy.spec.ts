import { DefaultDeliveryPolicy } from '../delivery-policy';
import { NotificationType } from '../../model/notification-type.enum';
import { Priority } from '../../model/priority.enum';

describe('DefaultDeliveryPolicy', () => {
  const policy = new DefaultDeliveryPolicy();

  it('marks order events as realtime', () => {
    expect(policy.shouldDeliverRealtime(NotificationType.ORDER_CREATED)).toBe(true);
    expect(policy.shouldDeliverRealtime(NotificationType.PAYMENT_COMPLETED)).toBe(true);
    expect(policy.shouldDeliverRealtime(NotificationType.SELLER_NEW_ORDER)).toBe(true);
  });

  it('marks low-priority types as non-realtime', () => {
    expect(policy.shouldDeliverRealtime(NotificationType.PRODUCT_APPROVED)).toBe(false);
    expect(policy.shouldDeliverRealtime(NotificationType.REVIEW_REPLIED)).toBe(false);
    expect(policy.shouldDeliverRealtime(NotificationType.PAYOUT_COMPLETED)).toBe(false);
  });

  it('uses exponential backoff for retry delay', () => {
    expect(policy.getRetryDelayMs(0)).toBe(1000);
    expect(policy.getRetryDelayMs(1)).toBe(2000);
    expect(policy.getRetryDelayMs(2)).toBe(4000);
  });

  it('caps retry delay at 5 minutes', () => {
    expect(policy.getRetryDelayMs(20)).toBe(300_000);
  });

  it('returns max retries based on priority', () => {
    expect(policy.getMaxRetries(Priority.HIGH)).toBe(3);
    expect(policy.getMaxRetries(Priority.MEDIUM)).toBe(2);
    expect(policy.getMaxRetries(Priority.LOW)).toBe(1);
  });
});
