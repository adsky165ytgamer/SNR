import { GoogleAuth } from "google-auth-library";

const auth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/cloud-platform"] });
const client = await auth.getClient();
const response = await client.request({ url: "https://serviceusage.googleapis.com/v1/projects/school-notics/services/fcm.googleapis.com" });
console.log(JSON.stringify({ service: response.data.config?.name, state: response.data.state }, null, 2));
