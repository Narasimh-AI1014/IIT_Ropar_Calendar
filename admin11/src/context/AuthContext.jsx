import { createContext, useContext, useState } from "react";
import axios from "axios";

const AuthContext = createContext(null);

function loadUser() {
  try {
    const saved = localStorage.getItem("user");
    return saved ? JSON.parse(saved) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(loadUser);

  // Called by the Login page with the Google ID token.
  // The backend checks the token and our database, then gives us its own token.
  const loginWithGoogle = async (idToken) => {
    const base = import.meta.env.VITE_API_URL || "/api";
    const res = await axios.post(`${base}/auth/google`, {
      idToken,
      callingSite: window.location.origin,
    });
    const { token, role, email, name } = res.data;
    if (role !== "ADMIN") {
      throw new Error("This account is not an admin.");
    }
    const u = { name, email, role };
    localStorage.setItem("user", JSON.stringify(u));
    localStorage.setItem("token", token);
    setUser(u);
  };

  const logout = () => {
    localStorage.removeItem("user");
    localStorage.removeItem("token");
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, loginWithGoogle, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
