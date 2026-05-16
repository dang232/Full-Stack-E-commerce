import { z } from "zod";

export const apiResponseSchema = <T extends z.ZodType>(
  data: T,
): z.ZodType<ApiResponse<z.infer<T>>> =>
  z.object({
    success: z.boolean(),
    message: z.string(),
    data,
    errorCode: z.string().nullable(),
    timestamp: z.string(),
  }) as unknown as z.ZodType<ApiResponse<z.infer<T>>>;

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  errorCode: string | null;
  timestamp: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly errorCode: string | null;
  readonly correlationId: string | undefined;

  constructor(status: number, errorCode: string | null, message: string, correlationId?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.errorCode = errorCode;
    this.correlationId = correlationId;
  }
}
