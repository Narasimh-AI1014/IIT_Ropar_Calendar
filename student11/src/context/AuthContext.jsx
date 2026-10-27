import { createContext, useContext, useState } from "react";
import { googleLogout } from "@react-oauth/google";
import { authApi } from "../api/authApi";

const AuthContext = createContext(null);

function loadUser() {
  try {
    if (!localStorage.getItem("token")) return null; // old demo logins have no token
    const saved = localStorage.getItem("user");
    return saved ? JSON.parse(saved) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(loadUser);

  const loginWithGoogle = async (credential) => {
    const { token, user: u } = await authApi.googleLogin(credential);
    localStorage.setItem("token", token);
    localStorage.setItem("user", JSON.stringify(u));
    setUser(u);
  };

  const logout = () => {
    googleLogout();
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