import axios from "axios";

export const http = axios.create({ baseURL: import.meta.env.VITE_API_URL });

// Attach the login token to every request
http.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Return only the data; if the token is expired, go back to login
http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    if (err.response?.status === 401 && window.location.pathname !== "/login") {
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      window.location.href = "/login";
    }
    return Promise.reject(err);
  }
);