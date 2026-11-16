/**
 * The mark of the house: a shield with a tower in the middle, in the wine and salmon of the
 * login. The same drawing as `public/favicon.svg`; a change to one is a change to both.
 */
export function BrandCrest({ size, className }: { readonly size: number; readonly className?: string }) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 64 64"
      fill="none"
      aria-hidden="true"
      focusable="false"
    >
      <path
        d="M32 3.5 56 10.5v21c0 14.2-10.4 24.2-24 29-13.6-4.8-24-14.8-24-29v-21z"
        fill="#4A1522"
        stroke="#E8957F"
        strokeWidth="3"
        strokeLinejoin="round"
      />
      <path d="M23.5 20H28v5h2.5v-5h3v5H36v-5h4.5v8.5H25V49h14V28.5" fill="#E8957F" />
      <path d="M25 28.5h14V49H25z" fill="#E8957F" />
      <path d="M29 49v-6.5a3 3 0 0 1 6 0V49z" fill="#4A1522" />
      <rect x="31" y="32" width="2" height="5.5" rx="1" fill="#4A1522" />
    </svg>
  );
}
