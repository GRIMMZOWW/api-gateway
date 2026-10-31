import { api } from "./client";
export const analyticsApi = {
  summary: (q) =>
    api.get("/api/analytics/summary", {
      apiId: q.apiId?.toString(),
      from: q.from,
      to: q.to,
    }),
  timeseries: (q) =>
    api.get("/api/analytics/timeseries", {
      apiId: q.apiId?.toString(),
      from: q.from,
      to: q.to,
      bucket: q.bucket,
    }),
  topPaths: (q) =>
    api.get("/api/analytics/top-paths", {
      apiId: q.apiId?.toString(),
      from: q.from,
      to: q.to,
      limit: q.limit?.toString(),
    }),
  keys: (q) =>
    api.get("/api/analytics/keys", {
      apiId: q.apiId?.toString(),
      from: q.from,
      to: q.to,
    }),
  recent: (q) =>
    api.get("/api/analytics/recent", {
      apiId: q.apiId?.toString(),
      from: q.from,
      to: q.to,
      limit: q.limit?.toString(),
    }),
};
