import Keycloak, { type KeycloakInstance } from "keycloak-js";

const url = import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8085";
const realm = import.meta.env.VITE_KEYCLOAK_REALM ?? "vnshop";
const clientId = import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? "vnshop-web";

let instance: KeycloakInstance | null = null;

export function getKeycloak(): KeycloakInstance {
  if (!instance) {
    instance = new Keycloak({ url, realm, clientId });
  }
  return instance;
}

export async function refreshToken(minValiditySeconds = 30): Promise<boolean> {
  const kc = getKeycloak();
  if (!kc.authenticated) return false;
  try {
    return await kc.updateToken(minValiditySeconds);
  } catch {
    return false;
  }
}
