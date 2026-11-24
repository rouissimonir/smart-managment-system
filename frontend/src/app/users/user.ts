import { UserRole } from '../core/auth.service';

/** Assignment directory entry. The API never returns a password hash. */
export interface UserSummary {
  id: string;
  email: string;
  fullName: string | null;
  role: UserRole;
}
