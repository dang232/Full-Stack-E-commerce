import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import axios from 'axios';
import { PlaygroundService } from './playground.service.js';

jest.mock('axios');
const mockedAxios = axios as jest.MockedFunction<typeof axios>;

describe('PlaygroundService', () => {
  let service: PlaygroundService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        PlaygroundService,
        {
          provide: ConfigService,
          useValue: {
            get: (key: string) => {
              if (key === 'app.gatewayUrl') return 'http://gateway:8080';
              return undefined;
            },
          },
        },
      ],
    }).compile();

    service = module.get<PlaygroundService>(PlaygroundService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('proxies request correctly and returns response', async () => {
    (mockedAxios as unknown as jest.Mock).mockResolvedValueOnce({
      status: 200,
      statusText: 'OK',
      headers: { 'content-type': 'application/json' },
      data: { result: 'ok' },
    });

    const result = await service.executeRequest(
      { method: 'GET', path: '/api/products', queryParams: { page: '1' } },
      'my-token',
    );

    expect(mockedAxios).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'GET',
        url: 'http://gateway:8080/api/products',
        headers: expect.objectContaining({ Authorization: 'Bearer my-token' }),
        params: { page: '1' },
        validateStatus: expect.any(Function),
      }),
    );

    expect(result.status).toBe(200);
    expect(result.statusText).toBe('OK');
    expect(result.body).toEqual({ result: 'ok' });
    expect(result.timeMs).toBeGreaterThanOrEqual(0);
  });

  it('returns status 0 on network failure', async () => {
    (mockedAxios as unknown as jest.Mock).mockRejectedValueOnce(new Error('ECONNREFUSED'));

    const result = await service.executeRequest({ method: 'POST', path: '/api/orders' });

    expect(result.status).toBe(0);
    expect(result.statusText).toBe('Network Error');
    expect(result.body).toBeNull();
    expect(result.headers).toEqual({});
  });
});
