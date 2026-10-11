import { useState } from 'react';
import { ADMIN, describeError } from './adminMessages';
import {
  useMenuItems,
  useMenuCategories,
  useMarkItemUnavailable,
  useMarkItemAvailable,
  MenuItem,
} from './useAdminMenu';
import { ItemForm } from './ItemForm';

type FormMode = null | 'create' | 'edit';

export function AdminMenuPage() {
  const itemsQuery = useMenuItems();
  const categoriesQuery = useMenuCategories();

  const [formMode, setFormMode] = useState<FormMode>(null);
  const [selectedItem, setSelectedItem] = useState<MenuItem | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const items = itemsQuery.data ?? [];
  const categories = categoriesQuery.data ?? [];

  const unavailableMutation = useMarkItemUnavailable(selectedItem?.id ?? '');
  const availableMutation = useMarkItemAvailable(selectedItem?.id ?? '');

  async function handleToggleAvailability(item: MenuItem) {
    try {
      if (item.isAvailable) {
        await unavailableMutation.mutateAsync();
      } else {
        await availableMutation.mutateAsync();
      }
      setNotice(`Item atualizado`);
      setTimeout(() => setNotice(null), 2000);
    } catch (err) {
      setError(describeError(err));
    }
  }

  function handleCreateSuccess() {
    setFormMode(null);
    setSelectedItem(null);
    setNotice(ADMIN.itemCreated);
    setTimeout(() => setNotice(null), 2000);
  }

  function handleEditSuccess() {
    setFormMode(null);
    setSelectedItem(null);
    setNotice(ADMIN.itemUpdated);
    setTimeout(() => setNotice(null), 2000);
  }

  if (itemsQuery.isPending || categoriesQuery.isPending) {
    return <div className="admin-page">{ADMIN.loadingMenu}</div>;
  }

  if (itemsQuery.isError) {
    return (
      <div className="admin-page">
        <p className="error-message">{describeError(itemsQuery.error)}</p>
        <button onClick={() => itemsQuery.refetch()}>{ADMIN.retry}</button>
      </div>
    );
  }

  if (formMode === 'create' || formMode === 'edit') {
    return (
      <div className="admin-page">
        <div className="form-container">
          <h2>{formMode === 'create' ? ADMIN.createItemTitle : ADMIN.editItemTitle}</h2>
          {error && <div className="form-error">{error}</div>}
          <ItemForm
            item={formMode === 'edit' ? selectedItem : null}
            categories={categories}
            onSuccess={formMode === 'create' ? handleCreateSuccess : handleEditSuccess}
            onCancel={() => {
              setFormMode(null);
              setSelectedItem(null);
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
        <h2>{ADMIN.menu}</h2>
        <button className="btn-primary" onClick={() => setFormMode('create')}>
          {ADMIN.newItem}
        </button>
      </div>

      {notice && <div className="notice">{notice}</div>}
      {error && <div className="error-message">{error}</div>}

      {items.length === 0 ? (
        <p className="empty-state">{ADMIN.noMenuItems}</p>
      ) : (
        <div className="items-table">
          <table>
            <thead>
              <tr>
                <th>{ADMIN.itemName}</th>
                <th>{ADMIN.itemPrice}</th>
                <th>{ADMIN.itemPrepStation}</th>
                <th>{ADMIN.itemActive}</th>
                <th>{ADMIN.itemAvailable}</th>
                <th className="actions-col">{ADMIN.editItem}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id} className={!item.isActive ? 'inactive' : ''}>
                  <td>{item.name}</td>
                  <td>{item.price}</td>
                  <td>{item.prepStation}</td>
                  <td className="center">
                    {item.isActive ? '✓' : '✗'}
                  </td>
                  <td className="center">
                    <input
                      type="checkbox"
                      checked={item.isAvailable}
                      onChange={() => handleToggleAvailability(item)}
                      disabled={!item.isActive}
                      aria-label={`Disponibilidade: ${item.name}`}
                    />
                  </td>
                  <td className="actions-col">
                    <button
                      className="btn-secondary"
                      onClick={() => {
                        setSelectedItem(item);
                        setFormMode('edit');
                      }}
                    >
                      {ADMIN.editItem}
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
