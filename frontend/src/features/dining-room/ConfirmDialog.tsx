import '../../styles/confirm-dialog.css';

interface Props {
  readonly isOpen: boolean;
  readonly title: string;
  readonly message: string;
  readonly requiresReason?: boolean;
  readonly reason?: string;
  readonly onReasonChange?: (reason: string) => void;
  readonly onConfirm: () => void;
  readonly onCancel: () => void;
  readonly isLoading?: boolean;
  readonly confirmLabel?: string;
  readonly cancelLabel?: string;
  readonly isDanger?: boolean;
}

/**
 * Dialog genérico pra confirmar ações (cancelar, fechar, etc).
 * Pode pedir motivo do cancelamento.
 */
export function ConfirmDialog({
  isOpen,
  title,
  message,
  requiresReason = false,
  reason = '',
  onReasonChange,
  onConfirm,
  onCancel,
  isLoading = false,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  isDanger = false,
}: Props) {
  if (!isOpen) {
    return null;
  }

  const canConfirm = !requiresReason || (reason && reason.trim().length > 0);

  return (
    <>
      <div className="dialog-overlay" onClick={onCancel} />
      <div className="dialog confirm-dialog">
        <div className="dialog-header">
          <h3 className="dialog-title">{title}</h3>
        </div>

        <div className="dialog-content">
          <p className="dialog-message">{message}</p>

          {requiresReason && (
            <div className="dialog-field">
              <label className="dialog-label">Motivo</label>
              <textarea
                className="dialog-textarea"
                value={reason}
                onChange={(e) => onReasonChange?.(e.target.value)}
                placeholder="Explique o motivo do cancelamento..."
                disabled={isLoading}
                rows={3}
              />
            </div>
          )}
        </div>

        <div className="dialog-actions">
          <button
            className="btn btn-secondary"
            onClick={onCancel}
            disabled={isLoading}
          >
            {cancelLabel}
          </button>
          <button
            className={`btn ${isDanger ? 'btn-danger' : 'btn-primary'}`}
            onClick={onConfirm}
            disabled={isLoading || !canConfirm}
          >
            {isLoading ? 'Processando...' : confirmLabel}
          </button>
        </div>
      </div>
    </>
  );
}
