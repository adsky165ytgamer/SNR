import { createApp } from "./app.js";
import { createFirebaseDependencies } from "./firebase.js";

const port = Number.parseInt(process.env.PORT ?? "8080", 10);
const app = await createApp(createFirebaseDependencies());
await app.listen({ host: "0.0.0.0", port });
