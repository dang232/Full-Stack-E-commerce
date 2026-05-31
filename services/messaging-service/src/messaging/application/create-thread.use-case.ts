import type { ThreadRepository } from "../domain/thread.repository";
import { THREAD_REPOSITORY } from "../domain/thread.repository";
import { BadRequestException, Inject, Injectable } from "@nestjs/common";
import { Thread } from "../domain/thread";

export interface CreateThreadInput {
  callerId: string;
  /** The other party. Must be a Keycloak `sub` (string id). */
  recipientId: string;
  productId?: string | null;
}

/**
 * Creates a thread or returns the existing one. The caller is always the
 * "buyer" half of the (buyer, seller) pair on first creation — we don't have
 * role info at this layer, so we settle on a deterministic ordering rule:
 * `buyerId` is the lexicographically smaller of the two `sub`s, so opening
 * a chat from either side resolves to the same row.
 */
@Injectable()
export class CreateThreadUseCase {
  constructor(
    @Inject(THREAD_REPOSITORY) private readonly threads: ThreadRepository,
  ) {}

  async execute(input: CreateThreadInput): Promise<Thread> {
    if (input.callerId === input.recipientId) {
      throw new BadRequestException("Cannot start a thread with yourself");
    }
    const [buyerId, sellerId] =
      input.callerId < input.recipientId
        ? [input.callerId, input.recipientId]
        : [input.recipientId, input.callerId];

    const productId = input.productId ?? null;

    const fresh = Thread.create({ buyerId, sellerId, productId });
    return this.threads.findOrCreate(fresh);
  }
}
