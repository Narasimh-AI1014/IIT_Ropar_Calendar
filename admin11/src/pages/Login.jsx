import { useEffect, useRef, useState } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID;

export default function Login() {
  const { user, loginWithGoogle } = useAuth();
  const buttonRef = useRef(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (user) return;

    const start = () => {
      if (!window.google || !buttonRef.current) return;
      window.google.accounts.id.initialize({
        client_id: CLIENT_ID,
        callback: async (response) => {
          try {
            setError("");
            await loginWithGoogle(response.credential);
          } catch (e) {
            const msg =
              e?.response?.data?.message ||
              e?.response?.data?.error ||
              e?.message ||
              "Sign-in failed.";
            setError(msg);
          }
        },
      });
      window.google.accounts.id.renderButton(buttonRef.current, {
        theme: "outline",
        size: "large",
        text: "signin_with",
        width: 280,
      });
    };

    if (window.google) {
      start();
      return;
    }
    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.defer = true;
    script.onload = start;
    document.body.appendChild(script);
  }, [user]);

  if (user) return <Navigate to="/" replace />;

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-100 p-4">
      <div className="w-full max-w-sm rounded-2xl bg-white p-8 shadow-md text-center">
        <h1 className="text-2xl font-bold text-slate-900">Admin Office</h1>
        <p className="mt-1 mb-6 text-sm text-slate-500">
          Sign in with your IIT Ropar Google account
        </p>
        <div className="flex justify-center" ref={buttonRef} />
        {error && <p className="mt-4 text-sm text-red-600">{error}</p>}
      </div>
    </div>
  );
}
