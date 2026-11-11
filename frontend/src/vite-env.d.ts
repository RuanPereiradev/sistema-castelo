/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * The address a customer's phone reaches the front at, without a trailing slash
   * (`https://castel.example`). The table QR encodes it; absent, the QR falls back to the
   * origin the admin opened the page from (decision #42 of task qr-table-ordering).
   */
  readonly VITE_PUBLIC_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
