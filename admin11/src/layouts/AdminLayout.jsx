import { NavLink, Outlet } from "react-router-dom";
import { Upload, CalendarPlus, Landmark, LogOut, ShieldCheck } from "lucide-react";
import { useAuth } from "../context/AuthContext";

const NAV = [
  { to: "/import", label: "Import data", icon: Upload },
  { to: "/campus", label: "Campus calendar", icon: Landmark },
  { to: "/events/new", label: "Create event", icon: CalendarPlus },
];

const linkClass = ({ isActive }) =>
  `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${
    isActive ? "bg-white/10 text-white" : "text-slate-400 hover:bg-white/5 hover:text-white"
  }`;

export default function AdminLayout() {
  const { user, logout } = useAuth();

  return (
    <div className="min-h-screen lg:flex">
      <aside className="hidden w-60 shrink-0 flex-col bg-slate-900 p-4 lg:flex">
        <div className="mb-6 flex items-center gap-3 px-2 py-2">
          <div className="grid h-10 w-10 place-items-center rounded-xl bg-brand-600 text-white">
            <ShieldCheck className="h-5 w-5" />
          </div>
          <div className="leading-tight">
            <p className="text-[11px] font-semibold tracking-widest text-slate-400">IIT ROPAR</p>
            <p className="font-bold text-white">Admin</p>
          </div>
        </div>

        <nav className="flex-1 space-y-1">
          {NAV.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} className={linkClass}>
              <Icon className="h-4 w-4" /> {label}
            </NavLink>
          ))}
        </nav>

        <div className="mt-4 border-t border-white/10 pt-4">
          <p className="truncate px-2 text-sm font-medium text-white">{user?.name}</p>
          <p className="truncate px-2 text-xs text-slate-400">{user?.email}</p>
          <button
            onClick={logout}
            className="mt-3 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-slate-400 hover:bg-white/5 hover:text-white"
          >
            <LogOut className="h-4 w-4" /> Logout
          </button>
        </div>
      </aside>

      <div className="min-w-0 flex-1">
        <div className="bg-slate-900 p-3 lg:hidden">
          <div className="flex items-center justify-between">
            <p className="font-bold text-white">Admin</p>
            <button onClick={logout} className="text-sm text-slate-300">Logout</button>
          </div>
          <nav className="mt-3 flex gap-2 overflow-x-auto">
            {NAV.map(({ to, label }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  `shrink-0 rounded-full px-3 py-1 text-xs font-medium ${isActive ? "bg-white text-slate-900" : "bg-white/10 text-slate-300"}`
                }
              >
                {label}
              </NavLink>
            ))}
          </nav>
        </div>

        <main className="mx-auto max-w-5xl p-4 sm:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}