/**
 * The label the admin typed for a dining table may already start with "Mesa" ("Mesa 1"), and the
 * screens write "Mesa" before it. This is the label without that leading word, so "Mesa 1" and "1"
 * both read "Mesa 1" and never "Mesa Mesa 1". A name like "V1" or "Varanda" is left as it is.
 */
export function bareTableLabel(label: string): string {
  const bare = label.trim().replace(/^mesa\s+/i, '');
  return bare === '' ? label : bare;
}
