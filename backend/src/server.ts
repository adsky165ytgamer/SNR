import { createApp } from "./app.js";
import { FirebaseIdTokenVerifier } from "./auth.js";
import { createFirebaseDependencies } from "./firebase.js";

const port = Number.parseInt(process.env.PORT ?? "8080", 10);
const app = await createApp({ ...createFirebaseDependencies(), auth: new FirebaseIdTokenVerifier() });
await app.listen({ host: "0.0.0.0", port });
