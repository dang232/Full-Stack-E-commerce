export class ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
  errorCode: string | null;
  timestamp: string;

  static ok<T>(data: T): ApiResponse<T>;
  static ok<T>(message: string, data: T): ApiResponse<T>;
  static ok<T>(messageOrData: string | T, maybeData?: T): ApiResponse<T> {
    const hasMessage =
      typeof messageOrData === 'string' && arguments.length === 2;

    return {
      success: true,
      message: hasMessage ? messageOrData : 'Success',
      data: hasMessage ? (maybeData as T) : (messageOrData as T),
      errorCode: null,
      timestamp: new Date().toISOString(),
    };
  }

  static error(message: string, errorCode: string): ApiResponse<null> {
    return {
      success: false,
      message,
      data: null,
      errorCode,
      timestamp: new Date().toISOString(),
    };
  }
}
