import { AppController } from './app.controller';

describe('AppController', () => {
  it('returns health response', () => {
    const controller = new AppController();

    const result = controller.health();

    expect(result.success).toBe(true);
    expect(result.message).toBe('Success');
    expect(result.errorCode).toBeNull();
    expect(result.data).toEqual({ status: 'ok' });
    expect(typeof result.timestamp).toBe('string');
  });
});
