import { useState } from 'react';
import { ADMIN } from '../features/admin/adminMessages';
import { AdminMenuPage } from '../features/admin/AdminMenuPage';
import { AdminTablesPage } from '../features/admin/AdminTablesPage';
import '../styles/admin.css';

type AdminTab = 'menu' | 'tables';

export function AdminPage() {
  const [activeTab, setActiveTab] = useState<AdminTab>('menu');

  return (
    <div className="admin-container">
      <nav className="admin-nav">
        <button
          className={`nav-btn ${activeTab === 'menu' ? 'active' : ''}`}
          onClick={() => setActiveTab('menu')}
        >
          {ADMIN.menu}
        </button>
        <button
          className={`nav-btn ${activeTab === 'tables' ? 'active' : ''}`}
          onClick={() => setActiveTab('tables')}
        >
          {ADMIN.tables}
        </button>
      </nav>

      <div className="admin-content">
        {activeTab === 'menu' && <AdminMenuPage />}
        {activeTab === 'tables' && <AdminTablesPage />}
      </div>
    </div>
  );
}
