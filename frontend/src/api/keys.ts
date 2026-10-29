import { api } from "./client";

export interface ApiKey {
  id: number;
  name: string;
  prefix: string;
  capacity: number;
  refillPerSec: number | string;
  status: "ACTIVE" | "REVOKED";
  expiresAt: string | null;
  lastUsedAt: string | null;
  createdAt: string;
}

export interface CreateKeyResponse {
  key: ApiKey;
  fullKey: string;
}

export interface CreateKeyPayload {
  name: string;
  capacity?: number;
  refillPerSec?: number;
  expiresAt?: string;
}

export interface UpdateKeyPayload {
  name?: string;
  capacity?: number;
  refillPerSec?: number;
}

export const keysApi = {
  list: (apiId: number | string) => api.get<ApiKey[]>(`/api/apis/${apiId}/keys`),
  create: (apiId: number | string, payload: CreateKeyPayload) =>
    api.post<CreateKeyResponse>(`/api/apis/${apiId}/keys`, payload),
  update: (keyId: number | string, payload: UpdateKeyPayload) =>
    api.patch<ApiKey>(`/api/keys/${keyId}`, payload),
  revoke: (keyId: number | string) => api.del<void>(`/api/keys/${keyId}/revoke`),
};
