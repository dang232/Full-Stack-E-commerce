import { Injectable, Logger } from '@nestjs/common';
import axios from 'axios';
import { DiscoveredEndpoint } from './discovery.types.js';

@Injectable()
export class OpenApiFetcher {
  private readonly logger = new Logger(OpenApiFetcher.name);

  async fetchSchema(serviceUrl: string, serviceId: string): Promise<DiscoveredEndpoint[]> {
    const spec = await this.tryFetchSpec(serviceUrl);
    if (!spec) return [];
    return this.parseSpec(spec, serviceId);
  }

  private async tryFetchSpec(serviceUrl: string): Promise<Record<string, unknown> | null> {
    try {
      const res = await axios.get(`${serviceUrl}/v3/api-docs`, { timeout: 3000 });
      if (res.data?.paths) return res.data;
    } catch { /* fall through */ }

    try {
      const res = await axios.get(`${serviceUrl}/api-json`, { timeout: 3000 });
      if (res.data?.paths) return res.data;
    } catch { /* fall through */ }

    return null;
  }

  private parseSpec(spec: Record<string, unknown>, serviceId: string): DiscoveredEndpoint[] {
    const endpoints: DiscoveredEndpoint[] = [];
    const paths = spec.paths as Record<string, Record<string, unknown>> | undefined;
    if (!paths) return endpoints;

    for (const [path, methods] of Object.entries(paths)) {
      for (const [method, details] of Object.entries(methods)) {
        if (['get', 'post', 'put', 'patch', 'delete'].includes(method)) {
          const op = details as Record<string, unknown>;
          endpoints.push({
            id: `${serviceId}:${method.toUpperCase()}:${path}`,
            serviceId,
            method: method.toUpperCase(),
            path,
            summary: (op.summary as string) ?? undefined,
            schema: op.requestBody ? (op.requestBody as Record<string, unknown>) : undefined,
          });
        }
      }
    }

    return endpoints;
  }
}
