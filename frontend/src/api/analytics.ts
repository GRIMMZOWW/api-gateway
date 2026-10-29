import { api } from "./client";

export interface AnalyticsSummary {
  totalRequests: number;
  successCount: number;
  rateLimitedCount: number;
  unauthorizedCount: number;
  errorCount: number;
  successRate: number;
  avgLatencyMs: number;
}

export interface TimeseriesPoint {
  bucketStart: string;
  total: number;
  success: number;
  rateLimited: number;
  errors: number;
}

export interface TopPath {
  path: string;
  method: string;
  count: number;
  avgLatencyMs: number;
}

export interface KeyUsage {
  keyId: number;
  name: string;
  prefix: string;
  total: number;
  rateLimited: number;
  lastUsedAt: string | null;
}

export interface RecentRequest {
  id: number;
  method: string;
  path: string;
  statusCode: number;
  outcome: string;
  latencyMs: number;
  createdAt: string;
  keyId: number | null;
}

export type Bucket = "hour" | "day";

export interface AnalyticsQuery {
  apiId?: string | number;
  from?: string;
  to?: string;
}

export const analyticsApi = {
  summary: (q: AnalyticsQuery) =>
    api.get<AnalyticsSummary>("/api/analytics/summary", {
      apiId: q.apiId?.toString(),
      from: q.from,
      to: q.to,
    }),
  timeseries: (q: AnalyticsQuery & { bucket?: Bucket }) =>
    api.get<TimeseriesPoint[]>("/api/analytics/timeseries", {
      apiId: q.apiId?.toString(),
      from: q.from,
      to: q.to,
      bucket: q.bucket,
    }),
  topPaths: (q: AnalyticsQuery & { limit?: number }) =>
    api.get<TopPath[]>("/api/analytics/top-paths", {
      apiId: q.apiId?.toString(),
      from: q.from,
      to: q.to,
      limit: q.limit?.toString(),
    }),
  keys: (q: AnalyticsQuery) =>
    api.get<KeyUsage[]>("/api/analytics/keys", {
      apiId: q.apiId?.toString(),
      from: q.from,
      to: q.to,
    }),
  recent: (q: AnalyticsQuery & { limit?: number }) =>
    api.get<RecentRequest[]>("/api/analytics/recent", {
      apiId: q.apiId?.toString(),
      from: q.from,
      to: q.to,
      limit: q.limit?.toString(),
    }),
};
