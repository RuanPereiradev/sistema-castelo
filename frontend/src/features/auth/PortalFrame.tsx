/**
 * B · Portal — a chapter house. Sealing-wax wine ground, the shield as
 * monogram, and the form inside a romanesque arch drawn in a double salmon
 * rule. Darker and more solemn, which is what the kitchen monitor wants.
 */
export function PortalFrame({
  brandName,
  children,
}: {
  readonly brandName: string;
  readonly children: React.ReactNode;
}) {
  return (
    <div className="login-root portal-root" data-frame="portal">
      <main className="portal-wall">
        <div className="portal-arch">
          <div className="portal-arch-inner">
            <div className="portal-crest">
              <div className="portal-shield" aria-hidden="true">
                {brandName.charAt(0)}
              </div>
              <div className="portal-brand">{brandName}</div>
              <div className="portal-hedera" aria-hidden="true">
                <span />
                <span className="portal-hedera-mark">❧</span>
                <span />
              </div>
            </div>
            <div className="login-panel">{children}</div>
          </div>
        </div>
      </main>
    </div>
  );
}
