// Credentials come from the local environment and never from committed defaults.
export function demoPassword(name) {
  const value = process.env[name];
  if (!value || value.length < 12 || value.length > 72 || !/[a-z]/.test(value) ||
      !/[A-Z]/.test(value) || !/[0-9]/.test(value) || !/[^a-zA-Z0-9]/.test(value)) {
    throw new Error(`Set ${name} to a 12–72 character password containing uppercase, lowercase, a number and a symbol.`);
  }
  return value;
}
