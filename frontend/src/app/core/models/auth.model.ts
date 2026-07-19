/** Authenticated application user. */
export interface User {
  email: string;
  id?: string;
  role?: string;
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

/** Response carrying the JWT, the user's email, and role. */
export interface AuthResponse {
  email: string;
  token: string;
  role: string;
}

/** User item for admin list. */
export interface UserListItem {
  id: string;
  email: string;
  role: string;
  createdAt: string;
  lastLogin?: string;
}
