# Security

Every mistake here has the same shape: something the server knew ended up somewhere the server does not control. The fixes are cheap and almost entirely structural (a marker import, a prefix, a header), which is exactly why skipping them is inexcusable.

`data-layer.md` owns authorization: who may read or write what. This file owns everything else: keeping secrets on the server, keeping user input from executing, and keeping the dependencies honest.

## Secrets never cross the boundary

### The server-only marker

The problem it solves is an import, not a leak: nothing stops a developer from importing a DAL function into a file marked `"use client"`. Without a marker, that compiles, runs, and quietly ships the query logic, and whatever it closes over, to the browser. There is no error. Everything looks like it works.

Import the marker at the top of **every** file that touches data access, private services, or secrets:

```ts
// data/user/require-user.ts
import "server-only"

export async function requireUser() { /* session + database */ }
```

Now a client import is a build error naming the offending parent component. It is an automatic try/catch around the architecture, and it costs one line.

> **VERIFY:** the exact import specifier for the server-only marker in the current framework version, and whether it ships preinstalled with a new project.

### Environment variables and the leak everyone makes

Variables are server-side by default; a public prefix opts one into the client bundle. That part is well known and rarely goes wrong on its own.

The failure is passing a private variable **as a prop** to a client component:

```tsx
// page.tsx, a server component
<LeakyClient serverSecret={process.env.SERVER_SECRET} />   // shipped to the browser
```

The prefix rule was never enforced here. The value was read legitimately on the server and then handed across the boundary by hand. It renders fine, nothing warns, and the secret is in the page source. This shows up in real code from mid-level engineers, not just juniors, precisely because nothing fails.

> **VERIFY:** the current public-prefix convention and whether the framework now offers build-time detection of private values crossing into client components.

### Tainting, for the ones a prefix cannot catch

React can mark a value so that reaching a client component throws instead of rendering. Two functions, different targets:

- **`taintUniqueValue(message, lifetime, value)`** for a single sensitive string: an API key, a token, a password hash. The `lifetime` argument is the object whose life the taint follows. Pass `process` to keep the rule active for the whole server process.
- **`taintObjectReference(message, object)`** for a whole object that must never be handed over wholesale, such as a full user record with private columns.

```ts
import { experimental_taintUniqueValue as taintUniqueValue } from "react"

const apiKey = process.env.SECRET_API_KEY
if (apiKey) taintUniqueValue("Do not pass API keys to the client", process, apiKey)
```

Taint is a safety net under the DTO, not a replacement for it. The DTO decides what the client receives; taint catches the value that escaped anyway.

> **VERIFY:** whether the taint API is still experimental, its current export names, and the config flag that enables it. It has been behind a flag for several releases and both the flag and the names move.

## User input never executes

React escapes interpolated content automatically (`<` becomes `&lt;`), so a pasted `<script>` renders as text. That default covers almost everything.

It stops at the one place you opt out: rendering raw HTML. Nothing is escaped there, so a comment body containing `<img src=x onerror="...">` executes on every visitor's machine. That is stored XSS, and the payload is whatever the attacker wants: session theft, keylogging, a fake login form.

If the product genuinely needs user-authored HTML, sanitize before rendering with an allowlist:

```tsx
import DOMPurify from "dompurify"

function SafePreview({ content }: { content: string }) {
  const clean = DOMPurify.sanitize(content, {
    ALLOWED_TAGS: ["h1", "h2", "p", "strong", "em", "ul", "li", "a"],
    ALLOWED_ATTR: ["href", "class"],
  })
  return <div dangerouslySetInnerHTML={{ __html: clean }} />
}
```

Allowlist, never blocklist. Enumerate what is permitted and everything else disappears; enumerate what is forbidden and you are betting you thought of every encoding trick.

Sanitize at the point of render, and where the content is stored, sanitize on the way in too. Two passes cost nothing and mean neither a bad write nor a bad read can hurt you.

> **VERIFY:** the sanitizer's current package name and whether it needs a DOM shim to run during server rendering. Server-side usage has historically required extra setup.

## Validation runs on the server or it did not run

Client-side validation is a UX feature. It makes errors appear instantly and it stops nothing, because the client is the attacker's machine.

The point that catches people: **server actions are public POST endpoints.** The framework generates a URL for each one. An attacker sends a request straight to it and never sees your form, so every constraint expressed only in JSX (`required`, `type="email"`, `maxLength`) simply does not exist for them.

So the schema is the validation, and it runs inside the action:

```ts
export const contactSchema = z.object({
  name: z.string().min(2).max(50),
  email: z.email(),
  content: z.string().min(1).max(5000),
})
```

One schema, imported by the form for instant feedback and by the action for the check that counts. The order inside every mutation stays the one from `data-layer.md`: validate input, authorize, mutate, validate output.

## Where the session lives

A session token in `localStorage` is a session token that JavaScript can read, which means it is a session token that **any** script on the page can read: an XSS bug, a compromised dependency, a third-party analytics snippet that got hijacked. The token is exfiltrated and the attacker has the account without needing the password, without triggering a login, and without expiring when the user changes it.

The alternative removes the class of attack rather than mitigating it. Put the session in a cookie marked `httpOnly`, so JavaScript cannot read it at all; `secure`, so it never crosses plaintext HTTP; and with a `sameSite` policy, so it is not attached to requests originating from other sites. An XSS bug on a page that stores its session this way is still a bug, but it cannot walk away with the session.

This is also why the framework's auth conventions read the session from cookies on the server rather than handing it to the client: the token is never in a place a script can reach.

The same reasoning extends past tokens. A query string is not a secret — it is written to server logs, proxy logs and browser history, and leaks through the referrer header when the user clicks an outbound link. `list-views.md` puts view state in the URL deliberately, and the boundary of that advice is here: filters and page numbers yes, anything you would not want printed in a log, never.

## Security headers

A fresh app ships essentially none of the headers that matter. They cost one file in the request interceptor and they close whole attack classes:

| Header | Stops |
|---|---|
| **Content-Security-Policy** | Which scripts, styles, images and connections may load at all. The strongest single control, and the one that breaks third-party embeds if configured carelessly |
| **Strict-Transport-Security** | Downgrade to HTTP after the first visit |
| **X-Frame-Options** | Clickjacking: your app rendered inside someone else's iframe |
| **X-Content-Type-Options: nosniff** | MIME sniffing, where an upload is guessed into being executable |
| **Referrer-Policy** | How much of the current URL leaks to sites the user navigates to |
| **Permissions-Policy** | Camera, microphone, geolocation access |

Do not hand-write these. Use a maintained helper applied globally in the request interceptor, then extend its defaults for the third parties you actually use: analytics, video embeds, a payment iframe. Each one needs an explicit CSP allowance or it silently stops working.

Two consequences to plan for:

- Applying headers on every route can opt the app out of static generation. Check what the current version requires and what it costs before assuming it is free.
- A CSP that is never tested in production is a CSP that breaks in production. Load the real pages and read the console for violations.

> **VERIFY:** the current name of the request interceptor (Next.js renamed `middleware` to `proxy`), the header-helper library's current setup, and whether the framework now has first-class header configuration that makes the library unnecessary.

## Supply chain

You do not own your dependencies. In September 2025 a maintainer of packages with billions of weekly downloads was phished; the attacker published patch versions carrying wallet-draining code. Anyone who installed within that window shipped it.

Two settings turn that from a coin flip into a non-event:

- **Minimum release age.** Refuse to resolve any version published less than seven days ago. Compromised releases are caught and yanked within days, so the delay costs you nothing and skips the entire window of exposure.
- **Block install scripts by default.** Postinstall hooks are arbitrary code executing at install time. Deny them repo-wide and allowlist the handful of packages that genuinely need to compile something.

Both belong in the package manager's config, committed, so every machine and CI runner inherits them.

> **VERIFY:** the config keys for minimum release age and install-script allowlisting in the package manager this project uses. These are recent additions and the key names differ per manager.

## Common mistakes

| Mistake | Consequence |
|---|---|
| Private env var passed as a prop to a client component | Secret rendered into the page source; no error, no warning |
| Data-access file with no server-only marker | A single bad import ships query logic and secrets to the browser |
| Rendering user HTML without sanitizing | Stored XSS on every visitor |
| Blocklist sanitizer config | One unforeseen encoding and the allowlist you meant to write is bypassed |
| Validation only in the form | The generated endpoint accepts anything |
| Returning the raw ORM row | Password hashes and internal flags leak; see `data-layer.md` |
| No security headers | Clickjacking, MIME sniffing and injection all stay open |
| Session token in `localStorage` | Any script on the page can steal the session; XSS becomes account takeover |
| Authorization enforced in the request interceptor | An interceptor bypass is an authorization bypass; see `data-layer.md` |
| Sensitive values in the query string | Written to every log in the path and leaked through the referrer header |
| Installing latest immediately | Full exposure to the supply-chain attack window |
