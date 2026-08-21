import { GoogleAuth } from "google-auth-library";

const projectId = "school-notics";
const packageName = "app.receiver";
const auth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/cloud-platform"] });
const client = await auth.getClient();
const response = await client.request({
  url: `https://firebase.googleapis.com/v1beta1/projects/${projectId}/androidApps`,
  method: "POST",
  data: { displayName: "School Notice Receiver", packageName }
});

console.log(JSON.stringify({
  projectId,
  packageName,
  firebaseAppId: response.data.appId,
  resourceName: response.data.name
}, null, 2));
