export enum DeliveryStatusValue {
  QUEUED = 'QUEUED',
  SENT = 'SENT',
  DELIVERED = 'DELIVERED',
  OPENED = 'OPENED',
  FAILED = 'FAILED',
  DLQ = 'DLQ',
}

const VALID_TRANSITIONS: Record<DeliveryStatusValue, DeliveryStatusValue[]> = {
  [DeliveryStatusValue.QUEUED]: [
    DeliveryStatusValue.SENT,
    DeliveryStatusValue.FAILED,
  ],
  [DeliveryStatusValue.SENT]: [
    DeliveryStatusValue.DELIVERED,
    DeliveryStatusValue.FAILED,
  ],
  [DeliveryStatusValue.DELIVERED]: [DeliveryStatusValue.OPENED],
  [DeliveryStatusValue.OPENED]: [],
  [DeliveryStatusValue.FAILED]: [
    DeliveryStatusValue.QUEUED,
    DeliveryStatusValue.DLQ,
  ],
  [DeliveryStatusValue.DLQ]: [],
};

export class DeliveryStatus {
  private constructor(private readonly value: DeliveryStatusValue) {}

  static queued(): DeliveryStatus {
    return new DeliveryStatus(DeliveryStatusValue.QUEUED);
  }

  static fromValue(value: DeliveryStatusValue): DeliveryStatus {
    return new DeliveryStatus(value);
  }

  transitionTo(next: DeliveryStatusValue): DeliveryStatus {
    if (!VALID_TRANSITIONS[this.value].includes(next)) {
      throw new Error(
        `Invalid delivery status transition: ${this.value} -> ${next}`,
      );
    }
    return new DeliveryStatus(next);
  }

  getValue(): DeliveryStatusValue {
    return this.value;
  }

  isQueued(): boolean {
    return this.value === DeliveryStatusValue.QUEUED;
  }

  isDelivered(): boolean {
    return this.value === DeliveryStatusValue.DELIVERED;
  }

  isFailed(): boolean {
    return this.value === DeliveryStatusValue.FAILED;
  }

  isTerminal(): boolean {
    return (
      this.value === DeliveryStatusValue.OPENED ||
      this.value === DeliveryStatusValue.DLQ
    );
  }
}
