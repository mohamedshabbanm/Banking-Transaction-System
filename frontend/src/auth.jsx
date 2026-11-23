import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, get, post, setUnauthorizedHandler, tokenStore } from './api';
import { play, soundFor } from './sound';
import { useToast } from './components/Toast';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [unread, setUnread] = useState(0);
  const [ready, setReady] = useState(false);
  const navigate = useNavigate();
  const toast = useToast();
  const lastUnread = useRef(null);

  const refresh = useCallback(async () => {
    if (!tokenStore.get()) {
      setUser(null);
      return null;
    }
    const me = await get('/api/me');
    setUser(me.user);
    setUnread(me.unreadNotifications);
    return me.user;
  }, []);

  useEffect(() => {
    setUnauthorizedHandler((err) => {
      tokenStore.clear();
      setUser(null);
      toast(err.code === 'SESSION_EXPIRED' ? 'Your session expired. Log in again.' : 'Log in to continue.', 'bad');
      navigate('/login');
    });
    refresh().catch(() => tokenStore.clear()).finally(() => setReady(true));
  }, [refresh, navigate, toast]);

  // Every 8 seconds, ask quietly whether something new arrived and play its sound.
  // X-Passive tells the server this is not the user doing something, so it never keeps an idle session alive.
  useEffect(() => {
    if (!user || user.role !== 'CUSTOMER') {
      lastUnread.current = null;
      return undefined;
    }
    let stopped = false;
    const check = async () => {
      if (document.hidden || stopped) return;
      try {
        const me = await api('/api/me', { passive: true });
        const n = me.unreadNotifications;
        if (lastUnread.current !== null && n > lastUnread.current) {
          const list = await api('/api/notifications', { passive: true });
          const fresh = list.filter((x) => !x.read).slice(0, n - lastUnread.current);
          if (fresh.length) {
            play(soundFor(fresh[0].kind));
            toast(fresh[0].message || fresh[0].title, ['HOLD', 'LOCK'].includes(fresh[0].kind) ? 'bad' : 'ok');
          }
        }
        lastUnread.current = n;
        setUnread(n);
      } catch {
        /* the next check will try again */
      }
    };
    lastUnread.current = unread;
    const id = setInterval(check, 8000);
    return () => {
      stopped = true;
      clearInterval(id);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  const login = async (username, password) => {
    const res = await post('/api/auth/login', { username, password });
    tokenStore.set(res.token);
    setUser(res.user);
    refresh().catch(() => {});
    return res.user;
  };

  const logout = async () => {
    try {
      await post('/api/auth/logout');
    } catch {
      /* already logged out */
    }
    tokenStore.clear();
    setUser(null);
    navigate('/login');
  };

  return (
    <AuthContext.Provider value={{ user, ready, unread, setUnread, login, logout, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
