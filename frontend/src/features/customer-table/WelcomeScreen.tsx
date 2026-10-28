import { useId, useState, type FormEvent, type ReactNode } from 'react';
import { CUSTOMER } from './customerMessages';
import { MAXIMUM_CUSTOMER_NAME_LENGTH } from './customerIdentity';
import { BrandShield, HederaRule } from './CustomerChrome';

/** The double frame with the Romanesque arch: entrance, loading and invalid QR share it. */
export function ArchFrame({ children }: { readonly children: ReactNode }) {
  return (
    <div className="ct-welcome">
      <div className="ct-welcome-frame">
        <div className="ct-arch">
          <div className="ct-arch-inner">
            <div className="ct-brand-block">
              <BrandShield large />
              <div className="ct-brand-name">{CUSTOMER.brand}</div>
              <HederaRule />
            </div>
            {children}
          </div>
        </div>
      </div>
    </div>
  );
}

export function WelcomeScreen({
  tableLabel,
  initialName,
  onEnter,
}: {
  readonly tableLabel: string;
  readonly initialName: string;
  readonly onEnter: (name: string) => void;
}) {
  const inputId = useId();
  const errorId = useId();
  const [name, setName] = useState(initialName);
  const [error, setError] = useState<string | null>(null);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = name.trim();
    if (trimmed.length === 0) {
      setError(CUSTOMER.welcome.nameRequired);
      return;
    }
    if (trimmed.length > MAXIMUM_CUSTOMER_NAME_LENGTH) {
      setError(CUSTOMER.welcome.nameTooLong(MAXIMUM_CUSTOMER_NAME_LENGTH));
      return;
    }
    onEnter(trimmed);
  }

  return (
    <ArchFrame>
      <div className="ct-welcome-table">
        <span className="ct-eyebrow">{CUSTOMER.welcome.youAreAt}</span>
        <h1 className="ct-welcome-title">
          {CUSTOMER.table} {tableLabel}
        </h1>
        <p className="ct-lead">{CUSTOMER.welcome.intro}</p>
      </div>
      <form className="ct-welcome-form" onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor={inputId} className="ct-welcome-label">
            {CUSTOMER.welcome.nameLabel}
          </label>
          <input
            id={inputId}
            className="input ct-welcome-input"
            type="text"
            name="nome"
            value={name}
            onChange={(event) => {
              setName(event.target.value);
              setError(null);
            }}
            autoComplete="given-name"
            enterKeyHint="go"
            maxLength={MAXIMUM_CUSTOMER_NAME_LENGTH}
            placeholder={CUSTOMER.welcome.namePlaceholder}
            aria-invalid={error !== null}
            aria-describedby={error ? errorId : undefined}
          />
          {error && (
            <p id={errorId} className="ct-field-error" role="alert">
              {error}
            </p>
          )}
        </div>
        <button type="submit" className="btn btn-primary btn-block ct-cta">
          {CUSTOMER.welcome.enter}
        </button>
      </form>
      <p className="ct-welcome-footnote">{CUSTOMER.welcome.footnote}</p>
    </ArchFrame>
  );
}
