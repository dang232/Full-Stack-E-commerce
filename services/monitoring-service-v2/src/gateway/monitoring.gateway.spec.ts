import { MonitoringGateway } from './monitoring.gateway.js';
import { ConfigService } from '@nestjs/config';

jest.mock('jwks-rsa', () => () => ({
  getSigningKey: jest.fn(),
}));

describe('MonitoringGateway', () => {
  let gateway: MonitoringGateway;

  beforeEach(() => {
    const config = {
      get: (key: string, def: string) => def,
    } as unknown as ConfigService;
    gateway = new MonitoringGateway(config);
    gateway.server = { emit: jest.fn() } as unknown as any;
  });

  it('emits service:status on event', () => {
    const payload = { serviceId: 'x', status: 'up', responseMs: 50, timestamp: new Date() };
    gateway.handleServiceStatus(payload);
    expect(gateway.server.emit).toHaveBeenCalledWith('service:status', payload);
  });

  it('emits service:alert on event', () => {
    const payload = { serviceId: 'x', type: 'down', message: 'down', timestamp: new Date() };
    gateway.handleServiceAlert(payload);
    expect(gateway.server.emit).toHaveBeenCalledWith('service:alert', payload);
  });

  it('disconnects client with no token', async () => {
    const client = {
      handshake: { auth: {}, query: {} },
      disconnect: jest.fn(),
      id: 'test',
    } as unknown as any;

    await gateway.handleConnection(client);
    expect(client.disconnect).toHaveBeenCalledWith(true);
  });
});
