import { GoogleAuth } from "google-auth-library";
import { mkdirSync, writeFileSync, chmodSync } from "node:fs";

const projectId = "school-notics";
const appId = "1:763216367314:android:9af9287a9df5aeddc4670b";
const destination = "/home/ubuntu/school-notice-broadcast-v01/android/receiver-app/google-services.json";
const auth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/cloud-platform"] });
const client = await auth.getClient();
const response = await client.request({ url: `https://firebase.googleapis.com/v1beta1/projects/${projectId}/androidApps/${appId}/config` });
const contents = response.data.configFileContents;
if (!contents) throw new Error("Firebase returned no Android configuration content.");
mkdirSync(destination.substring(0, destination.lastIndexOf("/")), { recursive: true });
writeFileSync(destination, Buffer.from(contents, "base64"));
chmodSync(destination, 0o600);
console.log(JSON.stringify({ projectId, appId, destination, written: true }));
