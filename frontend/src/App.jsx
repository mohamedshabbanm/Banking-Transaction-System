import { Navigate, NavLink, Outlet, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth';
import Icon from './components/Icon';
import Login from './pages/Login';
import Register from './pages/Register';
import ChangePassword from './pages/ChangePassword';
import Home from './pages/Home';
import Transfer from './pages/Transfer';
import Move from './pages/Move';
import Activity from './pages/Activity';
import TransactionDetail from './pages/TransactionDetail';
import Goals from './pages/Goals';
import Notifications from './pages/Notifications';
import Profile from './pages/Profile';
import AdminLayout from './pages/admin/AdminLayout';
import Overview from './pages/admin/Overview';
import Reviews from './pages/admin/Reviews';
import Customers from './pages/admin/Customers';
import AuditLog from './pages/admin/AuditLog';
import Rules from './pages/admin/Rules';

function Splash() {
  return (
    <div className="auth" style={{ justifyContent: 'center', alignItems: 'center' }}>
      <img src="/icon.png" alt="Ershak" width="64" height="64" />
    </div>
  );
}

/** Guards a group of routes: must be logged in, must have changed a forced password, right role. */
function Guard({ role }) {
  const { user, ready } = useAuth();
  if (!ready) return <Splash />;
  if (!user) return <Navigate to="/login" replace />;
  if (user.mustChangePassword) return <Navigate to="/change-password" replace />;
  if (role === 'ADMIN' && user.role !== 'ADMIN') return <Navigate to="/" replace />;
  if (role === 'CUSTOMER' && user.role === 'ADMIN') return <Navigate to="/admin" replace />;
  return <Outlet />;
}

function CustomerLayout() {
  const tabs = [
    { to: '/', icon: 'home', label: 'Home', end: true },
    { to: '/activity', icon: 'list', label: 'Activity' },
    { to: '/goals', icon: 'goal', label: 'Goals' },
    { to: '/profile', icon: 'user', label: 'Profile' },
  ];
  return (
    <div className="app">
      <Outlet />
      <nav className="tabbar" aria-label="Main">
        {tabs.map((t) => (
          <NavLink key={t.to} to={t.to} end={t.end}>
            <Icon name={t.icon} size={22} />
            {t.label}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/change-password" element={<ChangePassword />} />

      <Route element={<Guard role="CUSTOMER" />}>
        <Route element={<CustomerLayout />}>
          <Route index element={<Home />} />
          <Route path="activity" element={<Activity />} />
          <Route path="goals" element={<Goals />} />
          <Route path="profile" element={<Profile />} />
        </Route>
        <Route element={<div className="app"><Outlet /></div>}>
          <Route path="transfer" element={<Transfer />} />
          <Route path="move/:mode" element={<Move />} />
          <Route path="tx/:id" element={<TransactionDetail />} />
          <Route path="notifications" element={<Notifications />} />
        </Route>
      </Route>

      <Route path="/admin" element={<Guard role="ADMIN" />}>
        <Route element={<AdminLayout />}>
          <Route index element={<Overview />} />
          <Route path="reviews" element={<Reviews />} />
          <Route path="customers" element={<Customers />} />
          <Route path="audit" element={<AuditLog />} />
          <Route path="rules" element={<Rules />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
