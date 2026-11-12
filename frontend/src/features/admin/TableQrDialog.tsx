import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import QRCode from 'qrcode';
import { ADMIN, describeError } from './adminMessages';
import { useRegenerateOrderingToken, type AdminDiningTable } from './useAdminTables';
import { orderingUrlProblem, tableOrderingUrl } from './tableQr';
import '../../styles/confirm-dialog.css';

/** Marks the body while the card is mounted, so the print rule only applies here (decision #44). */
const PRINTING_CLASS = 'table-qr-printing';

type QrImage =
  | { readonly state: 'drawing' }
  | { readonly state: 'ready'; readonly url: string; readonly dataUrl: string }
  | { readonly state: 'failed' };

/** Black on white at a size that stays sharp on paper; the screen shrinks it with CSS. */
function useQrImage(url: string): QrImage {
  const [image, setImage] = useState<QrImage>({ state: 'drawing' });

  useEffect(() => {
    let current = true;
    setImage({ state: 'drawing' });
    QRCode.toDataURL(url, {
      width: 600,
      margin: 2,
      errorCorrectionLevel: 'M',
      color: { dark: '#000000', light: '#FFFFFF' },
    }).then(
      (dataUrl) => current && setImage({ state: 'ready', url, dataUrl }),
      () => current && setImage({ state: 'failed' }),
    );
    return () => {
      current = false;
    };
  }, [url]);

  return image;
}

/** The card that goes on the table: rendered straight into `body`, outside the app. */
function PrintedCard({ label, dataUrl }: { readonly label: string; readonly dataUrl: string }) {
  useEffect(() => {
    document.body.classList.add(PRINTING_CLASS);
    return () => document.body.classList.remove(PRINTING_CLASS);
  }, []);

  return createPortal(
    <div className="table-qr-print-sheet">
      <div className="table-qr-card">
        <h1 className="table-qr-card-title">{ADMIN.printedTableTitle(label)}</h1>
        <img className="table-qr-card-image" src={dataUrl} alt={ADMIN.tableQrImage(label)} />
        <p className="table-qr-card-instruction">{ADMIN.printedTableInstruction}</p>
      </div>
    </div>,
    document.body,
  );
}

interface Props {
  readonly table: AdminDiningTable;
  readonly onClose: () => void;
}

/**
 * See, print and replace the QR of one table (decisions #15, #16, #42–#44). The table comes
 * from the list query, so after a new code the dialog redraws from what the server holds.
 */
export function TableQrDialog({ table, onClose }: Props) {
  const url = tableOrderingUrl(table.orderingToken);
  const problem = orderingUrlProblem(url);
  const image = useQrImage(url);
  const regenerate = useRegenerateOrderingToken();

  const [confirming, setConfirming] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // The image of an older code must never be printed under the new one.
  const ready = image.state === 'ready' && image.url === url ? image : null;
  const busy = regenerate.isPending;

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape' && !busy) {
        onClose();
      }
    }
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [busy, onClose]);

  async function handleRegenerate() {
    setError(null);
    setNotice(null);
    try {
      await regenerate.mutateAsync(table.id);
      setNotice(ADMIN.tableQrRegenerated);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setConfirming(false);
    }
  }

  return (
    <>
      <div className="dialog-overlay" onClick={busy ? undefined : onClose} />
      <div
        className="dialog table-qr-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="table-qr-dialog-title"
      >
        <div className="dialog-header">
          <h3 className="dialog-title" id="table-qr-dialog-title">
            {confirming ? ADMIN.regenerateTableQrTitle : ADMIN.tableQrTitle(table.label)}
          </h3>
        </div>

        {confirming ? (
          <>
            <div className="dialog-content">
              <p className="dialog-message">{ADMIN.regenerateTableQrMessage(table.label)}</p>
            </div>
            <div className="dialog-actions">
              <button className="btn btn-secondary" onClick={() => setConfirming(false)} disabled={busy}>
                {ADMIN.cancel}
              </button>
              <button className="btn btn-danger" onClick={handleRegenerate} disabled={busy}>
                {busy ? ADMIN.regeneratingTableQr : ADMIN.regenerateTableQrConfirm}
              </button>
            </div>
          </>
        ) : (
          <>
            <div className="dialog-content table-qr-content">
              {notice && <div className="notice">{notice}</div>}
              {error && <div className="error-message">{error}</div>}

              {image.state === 'failed' ? (
                <p className="error-message">{ADMIN.tableQrFailed}</p>
              ) : ready ? (
                <img className="table-qr-preview" src={ready.dataUrl} alt={ADMIN.tableQrImage(table.label)} />
              ) : (
                <p className="table-qr-drawing">{ADMIN.tableQrDrawing}</p>
              )}

              <p className="table-qr-address">
                <span>{ADMIN.tableQrAddressCaption}</span>
                <code>{url}</code>
              </p>

              {problem === 'THIS_MACHINE_ONLY' && (
                <p className="table-qr-warning" role="alert">
                  {ADMIN.tableQrThisMachineOnly}
                </p>
              )}
              {problem === 'NOT_A_WEB_ADDRESS' && (
                <p className="table-qr-warning" role="alert">
                  {ADMIN.tableQrNotAWebAddress}
                </p>
              )}
            </div>

            <div className="dialog-actions table-qr-actions">
              <button className="btn btn-secondary" onClick={onClose}>
                {ADMIN.close}
              </button>
              <button className="btn btn-danger" onClick={() => setConfirming(true)}>
                {ADMIN.regenerateTableQr}
              </button>
              <button className="btn btn-primary" onClick={() => window.print()} disabled={!ready}>
                {ADMIN.printTableQr}
              </button>
            </div>
          </>
        )}
      </div>

      {ready && <PrintedCard label={table.label} dataUrl={ready.dataUrl} />}
    </>
  );
}
