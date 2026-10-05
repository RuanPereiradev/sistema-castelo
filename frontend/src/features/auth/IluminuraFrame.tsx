/**
 * A · Iluminura — a page of an illuminated manuscript. Parchment ground, a
 * rubricated capital on a salmon mat, double rules. On wide devices a wine
 * panel makes the left folio; on the phone there is no panel, and the seal sits
 * above the form instead.
 */
export function IluminuraFrame({
  brandName,
  children,
}: {
  readonly brandName: string;
  readonly children: React.ReactNode;
}) {
  const initial = brandName.charAt(0);
  const rest = brandName.slice(1);

  return (
    <div className="login-root" data-frame="iluminura">
      <aside className="folio">
        <div className="folio-plate">
          <div className="folio-kicker">Livro da casa</div>
          <div className="folio-brand">
            <div className="folio-wordmark">
              <span className="folio-capital">{initial}</span>
              <span className="folio-rest">{rest}</span>
            </div>
            <div className="folio-areas">Salão · Cozinha · Caixa</div>
          </div>
          <div className="folio-hedera" aria-hidden="true">
            ❧
          </div>
        </div>
      </aside>

      <main className="iluminura-main">
        <div className="iluminura-column">
          <div className="iluminura-mark">
            <div className="iluminura-seal" aria-hidden="true">
              {initial}
            </div>
            <div className="iluminura-brand">{brandName}</div>
          </div>
          <div className="login-panel">{children}</div>
        </div>
      </main>
    </div>
  );
}
