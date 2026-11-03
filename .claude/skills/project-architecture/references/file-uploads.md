# File uploads

An upload is the one feature where a user hands you a file and you agree to store it, serve it, and often render it. Every assumption you make about that file is an assumption an attacker gets to choose the value of.

Two rules carry most of the weight. The binary does not go through your server, and nothing the client says about the file is true.

## Direct to storage, not through the server

Routing the bytes through your application costs memory and bandwidth for work that adds nothing, and on serverless it usually does not work at all: request body limits are small, often a few megabytes, and the function timeout ends a slow upload from a bad connection partway through.

The default pattern is a **presigned URL**:

1. The client asks your server for permission to upload, describing the file.
2. The server **authenticates and authorizes**, then asks the storage provider for a short-lived signed URL.
3. The client uploads straight to storage.
4. The client tells your server it finished, and the server records it.

The server never touches the binary, and permanent storage credentials never leave it. Keep the storage client behind the server-only marker from `security.md`, as it holds those credentials.

Step 2 is a real authorization decision, not a formality. Issuing a presigned URL is granting write access to your bucket. Ask the same questions you would for any mutation: is this user authenticated, may they upload to this resource, are they within quota, and in a multi-tenant product, which tenant's prefix is this.

Constrain the signature itself, since the client controls everything else:

- **A short expiry.** Minutes, not hours. It is a permission slip for one upload happening now.
- **The exact content type**, so storage rejects a binary that does not match what was declared.
- **A maximum size**, enforced by the upload policy rather than by asking nicely.
- **A key you generate**, never a client-supplied filename.

> **VERIFY:** the current signing API for the storage provider in use, how to attach size and content-type conditions to the policy, the platform's request body limit if you route uploads through the server anyway, and current CORS requirements for browser uploads. These differ per provider and the condition syntax is easy to get subtly wrong.

## The client is lying about the file

The `Content-Type` header and the file extension are both attacker-controlled strings. Renaming `payload.html` to `photo.jpg` and declaring `image/jpeg` costs nothing. This is **MIME spoofing**, and trusting the header is a real, catalogued vulnerability class, not a theoretical one.

Validate by **content**. Read the leading bytes and check the actual file signature, the magic bytes, with a detection library on the server. Ignore the declared type entirely and store what you detected, because that stored value is what browsers and CDNs will later act on.

Detection has to happen server-side after the bytes exist, which with presigned uploads means in the confirmation step or a job triggered by it. Do not mark a file usable until it has been verified.

Two more things you generate rather than accept:

- **The storage key.** Derive it yourself, scoped by tenant and resource, ideally a UUID. A client filename can contain path traversal sequences or characters that break whatever consumes the key later. Keep the original name as metadata for display, never as a path.
- **The size check.** Enforce it in the signed policy and verify it after, since a "1MB" claim is worth exactly nothing.

## What can be in the file

| Threat | Why it works |
|---|---|
| **SVG or HTML with a script** | Both are executable documents. Served from your domain, a script inside one runs with your origin's cookies, which is stored XSS with a full session handoff |
| **Path traversal in the filename** | `../../` sequences escape the intended prefix when the name is used as a path |
| **Decompression bombs** | A small archive that expands to fill memory or disk during processing |
| **Malware in a document** | The file is never rendered by you, only downloaded by a colleague who opens it |
| **Polyglot files** | Valid as two formats at once, passing an image check while remaining executable as something else |

The structural defense is the same for all of them: **serve user files from a different origin than the application**. A separate storage domain or a dedicated subdomain means a script that runs has no access to your cookies or your origin's storage. Add a download-forcing content disposition for anything that is not deliberately meant to render inline.

Beyond that, allowlist the formats you accept, and treat SVG as user-authored HTML rather than as an image: either sanitize it exactly as in `security.md`, rasterize it, or do not accept it.

For products taking documents from strangers, run a malware scan before the file enters the normal flow. Files stay quarantined until it passes.

## Database and storage each hold half

- **Object storage** holds the binary. That is all it is good at.
- **The database** holds the metadata: id, storage key, original display name, detected content type, size, owner, tenant, status, timestamps.

Never store binaries in the database, and never treat the bucket as the record. The database is the source of truth for what exists; storage is where the bytes happen to live. Listing a user's files means querying your tables, not listing a bucket.

A `status` column is what makes the two-step flow honest: `pending` when the URL is issued, `ready` after confirmation and validation. Nothing reads a row that is not ready.

## Orphans

The two systems can disagree in both directions, and both need handling:

- **Upload succeeded, record failed.** Bytes in the bucket that nothing references. Pure cost, growing forever.
- **Record deleted, object remained.** The user believes the file is gone and it is still retrievable by key.

Three defenses that compose:

1. **A lifecycle rule that aborts incomplete multipart uploads** after a few days. Every bucket should have this; an upload unfinished after a week is abandoned.
2. **A reconciliation job** comparing keys in storage against rows in the database, deleting what nothing references. Only sweep objects older than the longest plausible pending window, or it will delete uploads in flight.
3. **Delete the object when the row is deleted**, enqueued as a job so a storage outage does not fail the user's delete.

The `pending` status makes reconciliation safe to automate: an old pending row with no confirmation is unambiguously garbage.

## Processing afterward

Resizing, thumbnails, transcoding, text extraction, and virus scanning are all slow and all fail sometimes. They belong in background jobs, per `async-work.md`, with the same rules: idempotent, tenant context passed explicitly in the payload, retried with backoff.

The upload confirmation returns immediately with the file marked ready-but-unprocessed. Derived artifacts appear as the jobs finish.

## Common mistakes

| Mistake | Consequence |
|---|---|
| Uploading through the server on serverless | Body limits and timeouts break large or slow uploads |
| Trusting the client `Content-Type` | Executable content stored and later served as an image |
| Validating by file extension | Renaming the file defeats the entire check |
| Presigned URL issued without authorization | Anyone who can call the endpoint can write to your bucket |
| Long-lived or unconstrained signature | A reusable permission slip for arbitrary uploads |
| Client filename used as the storage key | Path traversal and collisions |
| User files served from the application origin | A stored SVG or HTML becomes XSS with session access |
| Bucket treated as the source of truth | Listing, permissions and metadata all become guesswork |
| No pending status | No way to distinguish an in-flight upload from an orphan |
| No lifecycle rule or reconciliation | The bucket accumulates unreferenced objects forever |
