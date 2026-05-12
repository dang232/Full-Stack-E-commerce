import { ApiResponse } from './api-response';

describe('ApiResponse', () => {
  it('creates success response from data only', () => {
    const response = ApiResponse.ok({ value: 42 });

    expect(response.success).toBe(true);
    expect(response.message).toBe('Success');
    expect(response.data).toEqual({ value: 42 });
    expect(response.errorCode).toBeNull();
    expect(typeof response.timestamp).toBe('string');
  });

  it('creates success response from message and data', () => {
    const response = ApiResponse.ok('Custom message', { value: 1 });

    expect(response.success).toBe(true);
    expect(response.message).toBe('Custom message');
    expect(response.data).toEqual({ value: 1 });
    expect(response.errorCode).toBeNull();
  });

  it('creates error response', () => {
    const response = ApiResponse.error('Something failed', 'ERR_CODE');

    expect(response.success).toBe(false);
    expect(response.message).toBe('Something failed');
    expect(response.data).toBeNull();
    expect(response.errorCode).toBe('ERR_CODE');
    expect(typeof response.timestamp).toBe('string');
  });

  it('serializes timestamp as ISO string', () => {
    const response = ApiResponse.ok(null);

    expect(new Date(response.timestamp).toISOString()).toBe(response.timestamp);
  });
});
