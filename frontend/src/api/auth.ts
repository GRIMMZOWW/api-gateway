import { api } from "./client";

export interface UserResponse {
  id: number;
  email: string;
  displayName: string;
  role: string;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  user: UserResponse;
}

export interface RegisterPayload {
  email: string;
  password: string;
  displayName: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export const authApi = {
  register: (payload: RegisterPayload) =>
    api.post<AuthResponse>("/api/auth/register", payload),
  login: (payload: LoginPayload) =>
    api.post<AuthResponse>("/api/auth/login", payload),
  me: () => api.get<UserResponse>("/api/auth/me"),
};
