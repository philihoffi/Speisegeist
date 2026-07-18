export interface User {
  email: string;
  id?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  confirmPassword?: string;
}

export interface AuthResponse {
  email: string;
  token: string;
}
