import { useState } from 'react';
import { ADMIN } from './adminMessages';
import {
  type MenuItem,
  type MenuCategory,
  useCreateMenuItem,
  useUpdateMenuItem,
  type CreateMenuItemRequest,
  type UpdateMenuItemRequest,
} from './useAdminMenu';

interface ItemFormProps {
  readonly item?: MenuItem | null;
  readonly categories: readonly MenuCategory[];
  readonly onSuccess: () => void;
  readonly onCancel: () => void;
}

export function ItemForm({ item, categories, onSuccess, onCancel }: ItemFormProps) {
  const isEditing = !!item;
  const createMutation = useCreateMenuItem();
  const updateMutation = useUpdateMenuItem(item?.id ?? '');

  const [formData, setFormData] = useState({
    categoryId: item?.categoryId ?? (categories[0]?.id ?? ''),
    name: item?.name ?? '',
    description: item?.description ?? '',
    prepStation: item?.prepStation ?? 'KITCHEN',
    isSoldByWeight: item?.soldByWeight ?? false,
    price: item?.price ?? '',
    serviceChargeEligible: item?.serviceChargeEligible ?? true,
  });

  const [error, setError] = useState<string | null>(null);
  const isLoading = createMutation.isPending || updateMutation.isPending;

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    if (!formData.name.trim()) {
      setError(ADMIN.fieldRequired);
      return;
    }

    if (!formData.categoryId) {
      setError(ADMIN.fieldRequired);
      return;
    }

    if (!formData.price.trim()) {
      setError(ADMIN.fieldRequired);
      return;
    }

    try {
      if (isEditing) {
        const updateReq: UpdateMenuItemRequest = {
          price: formData.price,
        };
        await updateMutation.mutateAsync(updateReq);
      } else {
        const createReq: CreateMenuItemRequest = {
          categoryId: formData.categoryId,
          name: formData.name,
          description: formData.description || null,
          prepStation: formData.prepStation,
          isSoldByWeight: formData.isSoldByWeight,
          price: formData.price,
          serviceChargeEligible: formData.serviceChargeEligible,
        };
        await createMutation.mutateAsync(createReq);
      }
      onSuccess();
    } catch (err) {
      setError(ADMIN.saveError);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="item-form">
      {error && <div className="form-error">{error}</div>}

      <div className="form-group">
        <label htmlFor="name">{ADMIN.itemName}</label>
        <input
          id="name"
          type="text"
          value={formData.name}
          onChange={(e) => setFormData({ ...formData, name: e.target.value })}
          disabled={isLoading}
          maxLength={100}
        />
      </div>

      <div className="form-group">
        <label htmlFor="category">{ADMIN.itemCategory}</label>
        <select
          id="category"
          value={formData.categoryId}
          onChange={(e) => setFormData({ ...formData, categoryId: e.target.value })}
          disabled={isLoading || isEditing}
        >
          {categories.map((cat) => (
            <option key={cat.id} value={cat.id}>
              {cat.name}
            </option>
          ))}
        </select>
      </div>

      <div className="form-group">
        <label htmlFor="description">{ADMIN.itemDescription}</label>
        <textarea
          id="description"
          value={formData.description}
          onChange={(e) => setFormData({ ...formData, description: e.target.value })}
          disabled={isLoading}
          rows={3}
          maxLength={500}
        />
      </div>

      <div className="form-row">
        <div className="form-group">
          <label htmlFor="prepStation">{ADMIN.itemPrepStation}</label>
          <select
            id="prepStation"
            value={formData.prepStation}
            onChange={(e) => setFormData({ ...formData, prepStation: e.target.value })}
            disabled={isLoading}
          >
            <option value="KITCHEN">{ADMIN.prepStationKitchen}</option>
            <option value="PIZZA">{ADMIN.prepStationPizza}</option>
            <option value="BAR">{ADMIN.prepStationBar}</option>
          </select>
        </div>

        <div className="form-group">
          <label htmlFor="price">{ADMIN.itemPrice}</label>
          <input
            id="price"
            type="number"
            step="0.01"
            min="0"
            value={formData.price}
            onChange={(e) => setFormData({ ...formData, price: e.target.value })}
            disabled={isLoading}
            placeholder="0.00"
          />
        </div>
      </div>

      <div className="form-row">
        <div className="form-group checkbox">
          <input
            id="soldByWeight"
            type="checkbox"
            checked={formData.isSoldByWeight}
            onChange={(e) => setFormData({ ...formData, isSoldByWeight: e.target.checked })}
            disabled={isLoading}
          />
          <label htmlFor="soldByWeight">{ADMIN.itemSoldByWeight}</label>
        </div>

        <div className="form-group checkbox">
          <input
            id="serviceChargeEligible"
            type="checkbox"
            checked={formData.serviceChargeEligible}
            onChange={(e) =>
              setFormData({ ...formData, serviceChargeEligible: e.target.checked })
            }
            disabled={isLoading}
          />
          <label htmlFor="serviceChargeEligible">{ADMIN.itemServiceChargeEligible}</label>
        </div>
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
