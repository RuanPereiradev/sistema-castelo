import { useState } from 'react';
import { ADMIN, describeError } from './adminMessages';
import {
  useAdminDiningTables,
  useDeactivateDiningTable,
  useActivateDiningTable,
  type AdminDiningTable,
} from './useAdminTables';
import { TableForm } from './TableForm';

type FormMode = null | 'create' | 'edit';

export function AdminTablesPage() {
  const tablesQuery = useAdminDiningTables();

  const [formMode, setFormMode] = useState<FormMode>(null);
  const [selectedTable, setSelectedTable] = useState<AdminDiningTable | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const tables = tablesQuery.data ?? [];

  const deactivateMutation = useDeactivateDiningTable(selectedTable?.id ?? '');
  const activateMutation = useActivateDiningTable(selectedTable?.id ?? '');

  async function handleToggleActive(table: AdminDiningTable) {
    try {
      if (table.isActive) {
        await deactivateMutation.mutateAsync();
      } else {
        await activateMutation.mutateAsync();
      }
      setNotice(`Mesa atualizada`);
      setTimeout(() => setNotice(null), 2000);
    } catch (err) {
      const errorMsg = describeError(err);
      setError(errorMsg);
      setTimeout(() => setError(null), 5000);
    }
  }

  function handleCreateSuccess() {
    setFormMode(null);
    setSelectedTable(null);
    setNotice(ADMIN.tableCreated);
    setTimeout(() => setNotice(null), 2000);
  }

  function handleEditSuccess() {
    setFormMode(null);
    setSelectedTable(null);
    setNotice(ADMIN.tableUpdated);
    setTimeout(() => setNotice(null), 2000);
  }

  if (tablesQuery.isPending) {
    return <div className="admin-page">{ADMIN.loadingTables}</div>;
  }

  if (tablesQuery.isError) {
    return (
      <div className="admin-page">
        <p className="error-message">{describeError(tablesQuery.error)}</p>
        <button onClick={() => tablesQuery.refetch()}>{ADMIN.retry}</button>
      </div>
    );
  }

  if (formMode === 'create' || formMode === 'edit') {
    return (
      <div className="admin-page">
        <div className="form-container">
          <h2>{formMode === 'create' ? ADMIN.createTableTitle : ADMIN.editTableTitle}</h2>
          {error && <div className="form-error">{error}</div>}
          <TableForm
            table={formMode === 'edit' ? selectedTable : null}
            onSuccess={formMode === 'create' ? handleCreateSuccess : handleEditSuccess}
            onCancel={() => {
              setFormMode(null);
              setSelectedTable(null);
              setError(null);
            }}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="admin-page">
      <div className="admin-header">
        <h2>{ADMIN.tables}</h2>
        <button className="btn-primary" onClick={() => setFormMode('create')}>
          {ADMIN.newTable}
        </button>
      </div>

      {notice && <div className="notice">{notice}</div>}
      {error && <div className="error-message">{error}</div>}

      {tables.length === 0 ? (
        <p className="empty-state">{ADMIN.noTables}</p>
      ) : (
        <div className="tables-table">
          <table>
            <thead>
              <tr>
                <th>{ADMIN.tableLabel}</th>
                <th>{ADMIN.tableSeats}</th>
                <th>{ADMIN.tableArea}</th>
                <th>{ADMIN.tableActive}</th>
                <th className="actions-col">{ADMIN.editTable}</th>
              </tr>
            </thead>
            <tbody>
              {tables.map((table) => (
                <tr key={table.id} className={!table.isActive ? 'inactive' : ''}>
                  <td>{table.label}</td>
                  <td className="center">{table.seats ?? '—'}</td>
                  <td>{table.area || '—'}</td>
                  <td className="center">
                    <input
                      type="checkbox"
                      checked={table.isActive}
                      onChange={() => handleToggleActive(table)}
                      aria-label={`Ativar/desativar mesa ${table.label}`}
                    />
                  </td>
                  <td className="actions-col">
                    <button
                      className="btn-secondary"
                      onClick={() => {
                        setSelectedTable(table);
                        setFormMode('edit');
                      }}
                    >
                      {ADMIN.editTable}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
