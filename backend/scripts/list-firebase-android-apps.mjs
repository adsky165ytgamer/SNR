import { GoogleAuth } from "google-auth-library";

const projectId = "school-notics";
const auth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/cloud-platform"] });
const client = await auth.getClient();
const response = await client.request({ url: `https://firebase.googleapis.com/v1beta1/projects/${projectId}/androidApps` });
const apps = (response.data.apps ?? []).map((app) => ({ appId: app.appId, packageName: app.packageName, displayName: app.displayName }));
console.log(JSON.stringify({ projectId, androidApps: apps }, null, 2));
