/**
 * Strips anything that could identify a person or a place from free text before it is stored
 * (crash messages and stack traces). Pure function; order matters: emails, then coordinates,
 * then long digit runs, so a phone-like number inside an email or coordinate is not double-handled.
 */
const EMAIL = /[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}/g;
// Two decimal numbers with 4+ decimals separated by a comma, e.g. "12.97167,77.59456" or "-12.9716, 77.5946".
const COORDS = /-?\d{1,3}\.\d{4,}\s*,\s*-?\d{1,3}\.\d{4,}/g;
// 6+ digits, optionally separated by single spaces or dashes, optionally led by "+". Stack-frame line numbers are <= 5 digits.
const DIGITS = /\+?\d(?:[ -]?\d){5,}/g;

export function scrub(s: string): string {
  return s.replace(EMAIL, "[email]").replace(COORDS, "[place]").replace(DIGITS, "[number]");
}
