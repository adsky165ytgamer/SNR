import { getAuth } from "firebase-admin/auth";
import { AuthenticatedUser, AuthVerifier } from "./domain.js";
import { firebaseAdminApp } from "./firebase.js";

type AuthError = Error & { code: string };

const authError = (message: string): AuthError => Object.assign(new Error(message), { code: "AUTH_REQUIRED" });

export class FirebaseIdTokenVerifier implements AuthVerifier {
  async verifyAuthorizationHeader(value: string | undefined): Promise<AuthenticatedUser> {
    if (!value?.startsWith("Bearer ")) throw authError("Authorization bearer token required");
    const token = value.slice("Bearer ".length).trim();
    if (!token) throw authError("Authorization bearer token required");
    try {
      const decoded = await getAuth(firebaseAdminApp()).verifyIdToken(token);
      return {
        uid: decoded.uid,
        email: typeof decoded.email === "string" ? decoded.email : null,
        displayName: typeof decoded.name === "string" ? decoded.name : null
      };
    } catch {
      throw authError("Firebase ID token is invalid or expired");
    }
  }
}
