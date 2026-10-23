import { Link, Navigate, Route, Routes } from 'react-router-dom';

import { GuestOnly, RequireAuth } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { AdminPage } from './pages/AdminPage';
import { ForgotPasswordPage, LoginPage, RegisterPage, ResetPasswordPage } from './pages/AuthPages';
import { ChatPage } from './pages/ChatPage';
import { DocumentsPage } from './pages/DocumentsPage';
import { MemoryPage } from './pages/MemoryPage';
import { SettingsPage } from './pages/SettingsPage';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><RegisterPage /></GuestOnly>} />
      <Route path="/forgot-password" element={<GuestOnly><ForgotPasswordPage /></GuestOnly>} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route path="/chat/:conversationId?" element={<ChatPage />} />
        <Route path="/documents" element={<DocumentsPage />} />
        <Route path="/memory" element={<MemoryPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/admin" element={<RequireAuth admin><AdminPage /></RequireAuth>} />
      </Route>
      <Route path="/" element={<Navigate to="/chat" replace />} />
      <Route path="*" element={
        <div className="flex h-full flex-col items-center justify-center gap-3">
          <p className="text-lg font-medium">Page not found</p>
          <Link className="btn-secondary" to="/chat">Go to chat</Link>
        </div>
      } />
    </Routes>
  );
}
