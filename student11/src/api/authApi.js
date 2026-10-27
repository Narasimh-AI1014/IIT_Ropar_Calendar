import { http } from "./http";

// Reads the data inside a Google ID token (used only to show the picture).
export function decodeJwt(token) {
  const base64 = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
  const json = decodeURIComponent(
    atob(base64)
      .split("")
      .map((c) => "%" + c.charCodeAt(0).toString(16).padStart(2, "0"))
      .join("")
  );
  return JSON.parse(json);
}

export const authApi = {
  googleLogin: async (credential) => {
    const picture = decodeJwt(credential).picture;
    // The backend verifies the Google token and returns our own login token
    const res = await http.post("/auth/google", {
      idToken: credential,
      callingSite: window.location.origin,
    });
    const d = res.data ?? res;
    return {
      token: d.token,
      user: { name: d.name, email: d.email, role: d.role, picture },
    };
  },
};