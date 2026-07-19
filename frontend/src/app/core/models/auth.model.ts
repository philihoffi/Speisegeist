/** Authenticated application user. */
export interface User {
  email: string;
  id?: string;
}

/** Payload for a login request. */
export interface LoginRequest {
  email: string;
  password: string;
}

/** Payload for a registration request. */
export interface RegisterRequest {
  email: string;
  password: string;
  confirmPassword?: string;
}

/** Response carrying the JWT and the user's email. */
export interface AuthResponse {
  email: string;
  token: string;
}
