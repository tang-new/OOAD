// Every credential-bearing acceptance request must fail before following ANY redirect.
// Put the enforced policy last so callers cannot accidentally override it with options.redirect.
export function fetchWithoutRedirect(url, options = {}) {
  return fetch(url, {...options, redirect:'error'});
}
