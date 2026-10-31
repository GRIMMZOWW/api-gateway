import { api } from "./client";
export const apisApi = {
  list: () => api.get("/api/apis"),
  get: (id) => api.get(`/api/apis/${id}`),
  create: (payload) => api.post("/api/apis", payload),
  update: (id, payload) => api.patch(`/api/apis/${id}`, payload),
  remove: (id) => api.del(`/api/apis/${id}`),
};
