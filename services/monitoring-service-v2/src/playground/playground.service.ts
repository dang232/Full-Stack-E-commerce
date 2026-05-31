import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import axios, { AxiosRequestConfig } from 'axios';
import { DiscoveredEndpoint } from '../discovery/discovery.types.js';

export interface PlaygroundRequest {
  method: string;
  path: string;
  headers?: Record<string, string>;
  body?: unknown;
  queryParams?: Record<string, string>;
}

export interface PlaygroundResponse {
  status: number;
  statusText: string;
  headers: Record<string, string>;
  body: unknown;
  timeMs: number;
}

@Injectable()
export class PlaygroundService {
  private readonly gatewayUrl: string;

  constructor(private readonly config: ConfigService) {
    this.gatewayUrl = this.config.get<string>('app.gatewayUrl') ?? 'http://localhost:8080';
  }

  async executeRequest(req: PlaygroundRequest, authToken?: string): Promise<PlaygroundResponse> {
    const url = `${this.gatewayUrl}${req.path}`;

    const headers: Record<string, string> = { ...(req.headers ?? {}) };
    if (authToken) {
      headers['Authorization'] = `Bearer ${authToken}`;
    }

    const params = req.queryParams;

    const axiosConfig: AxiosRequestConfig = {
      method: req.method as AxiosRequestConfig['method'],
      url,
      headers,
      params,
      data: req.body,
      validateStatus: () => true,
    };

    const start = Date.now();
    try {
      const response = await axios(axiosConfig);
      const timeMs = Date.now() - start;

      return {
        status: response.status,
        statusText: response.statusText,
        headers: response.headers as Record<string, string>,
        body: response.data,
        timeMs,
      };
    } catch {
      const timeMs = Date.now() - start;
      return {
        status: 0,
        statusText: 'Network Error',
        headers: {},
        body: null,
        timeMs,
      };
    }
  }
}
