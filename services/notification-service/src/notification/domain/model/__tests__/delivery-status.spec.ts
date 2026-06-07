import { DeliveryStatus, DeliveryStatusValue } from '../delivery-status';

describe('DeliveryStatus value object', () => {
  it('starts as QUEUED', () => {
    const status = DeliveryStatus.queued();
    expect(status.getValue()).toBe(DeliveryStatusValue.QUEUED);
    expect(status.isQueued()).toBe(true);
  });

  it('reconstitutes from value', () => {
    const status = DeliveryStatus.fromValue(DeliveryStatusValue.DELIVERED);
    expect(status.isDelivered()).toBe(true);
  });

  it('allows QUEUED -> SENT', () => {
    const status = DeliveryStatus.queued().transitionTo(
      DeliveryStatusValue.SENT,
    );
    expect(status.getValue()).toBe(DeliveryStatusValue.SENT);
  });

  it('allows QUEUED -> FAILED', () => {
    const status = DeliveryStatus.queued().transitionTo(
      DeliveryStatusValue.FAILED,
    );
    expect(status.isFailed()).toBe(true);
  });

  it('allows FAILED -> QUEUED (retry)', () => {
    const failed = DeliveryStatus.fromValue(DeliveryStatusValue.FAILED);
    const retried = failed.transitionTo(DeliveryStatusValue.QUEUED);
    expect(retried.isQueued()).toBe(true);
  });

  it('allows FAILED -> DLQ', () => {
    const failed = DeliveryStatus.fromValue(DeliveryStatusValue.FAILED);
    const dlq = failed.transitionTo(DeliveryStatusValue.DLQ);
    expect(dlq.isTerminal()).toBe(true);
  });

  it('rejects QUEUED -> DELIVERED (must go through SENT)', () => {
    expect(() =>
      DeliveryStatus.queued().transitionTo(DeliveryStatusValue.DELIVERED),
    ).toThrow('Invalid delivery status transition');
  });

  it('rejects OPENED -> anything (terminal)', () => {
    const opened = DeliveryStatus.fromValue(DeliveryStatusValue.OPENED);
    expect(() => opened.transitionTo(DeliveryStatusValue.QUEUED)).toThrow();
    expect(opened.isTerminal()).toBe(true);
  });

  it('rejects DLQ -> anything (terminal)', () => {
    const dlq = DeliveryStatus.fromValue(DeliveryStatusValue.DLQ);
    expect(() => dlq.transitionTo(DeliveryStatusValue.QUEUED)).toThrow();
    expect(dlq.isTerminal()).toBe(true);
  });
});
