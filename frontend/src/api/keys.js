import { api } from "./client";
export const keysApi = {
  list: (apiId) => api.get(`/api/apis/${apiId}/keys`),
  create: (apiId, payload) => api.post(`/api/apis/${apiId}/keys`, payload),
  update: (keyId, payload) => api.patch(`/api/keys/${keyId}`, payload),
  revoke: (keyId) => api.post(`/api/keys/${keyId}/revoke`),
};
