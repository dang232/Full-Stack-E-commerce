import { AppController } from './app.controller';

describe('AppController', () => {
  it('returns health response', () => {
    const controller = new AppController();

    const result = controller.health();

    expect(result).toEqual({ status: 'ok' });
  });
});
