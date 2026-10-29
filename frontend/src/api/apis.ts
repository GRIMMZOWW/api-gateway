import { api } from "./client";

export interface ApiResource {
  id: number;
  name: string;
  slug: string;
  upstreamBaseUrl: string;
  timeoutMs: number;
  enabled: boolean;
  createdAt: string;
}

export interface CreateApiPayload {
  name: string;
  slug: string;
  upstreamBaseUrl: string;
  timeoutMs?: number;
}

export interface UpdateApiPayload {
  name?: string;
  upstreamBaseUrl?: string;
  timeoutMs?: number;
  enabled?: boolean;
}

export const apisApi = {
  list: () => api.get<ApiResource[]>("/api/apis"),
  get: (id: number | string) => api.get<ApiResource>(`/api/apis/${id}`),
  create: (payload: CreateApiPayload) => api.post<ApiResource>("/api/apis", payload),
  update: (id: number | string, payload: UpdateApiPayload) =>
    api.patch<ApiResource>(`/api/apis/${id}`, payload),
  remove: (id: number | string) => api.del<void>(`/api/apis/${id}`),
};
