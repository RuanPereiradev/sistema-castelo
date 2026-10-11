import { useState } from 'react';
import { ADMIN } from './adminMessages';
import {
  AdminDiningTable,
  useCreateDiningTable,
  useUpdateDiningTable,
  CreateDiningTableRequest,
  UpdateDiningTableRequest,
} from './useAdminTables';

interface TableFormProps {
  readonly table?: AdminDiningTable | null;
  readonly onSuccess: () => void;
  readonly onCancel: () => void;
}

export function TableForm({ table, onSuccess, onCancel }: TableFormProps) {
  const isEditing = !!table;
  const createMutation = useCreateDiningTable();
  const updateMutation = useUpdateDiningTable(table?.id ?? '');

  const [formData, setFormData] = useState({
    label: table?.label ?? '',
    seats: table?.seats ?? '',
    area: table?.area ?? '',
  });

  const [error, setError] = useState<string | null>(null);
  const isLoading = createMutation.isPending || updateMutation.isPending;

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    if (!formData.label.trim()) {
      setError(ADMIN.fieldRequired);
      return;
    }

    try {
      if (isEditing) {
        const updateReq: UpdateDiningTableRequest = {
          label: formData.label,
          seats: formData.seats ? parseInt(formData.seats, 10) : null,
          area: formData.area || null,
        };
        await updateMutation.mutateAsync(updateReq);
      } else {
        const createReq: CreateDiningTableRequest = {
          label: formData.label,
          seats: formData.seats ? parseInt(formData.seats, 10) : null,
          area: formData.area || null,
        };
        await createMutation.mutateAsync(createReq);
      }
      onSuccess();
    } catch (err) {
      setError(ADMIN.saveError);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="table-form">
      {error && <div className="form-error">{error}</div>}

      <div className="form-group">
        <label htmlFor="label">{ADMIN.tableLabel}</label>
        <input
          id="label"
          type="text"
          value={formData.label}
          onChange={(e) => setFormData({ ...formData, label: e.target.value })}
          disabled={isLoading}
          maxLength={50}
          placeholder="Ex: 1, A1, Terraço 3"
        />
      </div>

      <div className="form-group">
        <label htmlFor="seats">{ADMIN.tableSeats}</label>
        <input
          id="seats"
          type="number"
          min="1"
          max="99"
          value={formData.seats}
          onChange={(e) => setFormData({ ...formData, seats: e.target.value })}
          disabled={isLoading}
          placeholder="Ex: 4"
        />
      </div>

      <div className="form-group">
        <label htmlFor="area">{ADMIN.tableArea}</label>
        <input
          id="area"
          type="text"
          value={formData.area}
          onChange={(e) => setFormData({ ...formData, area: e.target.value })}
          disabled={isLoading}
          maxLength={50}
          placeholder="Ex: Salão, Terraço"
        />
      </div>

      <div className="form-actions">
        <button type="submit" disabled={isLoading}>
          {isLoading ? ADMIN.save : ADMIN.save}
        </button>
        <button type="button" onClick={onCancel} disabled={isLoading}>
          {ADMIN.cancel}
        </button>
      </div>
    </form>
  );
}
