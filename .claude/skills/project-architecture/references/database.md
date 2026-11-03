# Database design

Before a single line of code, the schema decides whether the application is solid or falls apart. The usual approach is to open an agent, type "generate a production ready schema", and ship whatever comes back. That is how you get missing relationships, wrong field types, and tables that should not exist.

A model can generate tables. It cannot decide what your product is. **A production-ready database is designed, not generated.**

## Which kind of database

Choose from the access patterns, not from the fashion. A relational database is the default for a product's core data: users, organizations, orders, anything with relationships and transactions. It handles flexible fields too (a JSON column), so "the schema might change" is not a reason to leave it.

Add another store only when an access pattern clearly diverges from what the core database does well, and name the pattern when you do: full-text relevance ranking (a search index), similarity search (vectors), large files (object storage, per `file-uploads.md`), high-volume timestamped events or heavy historical aggregation (analytics storage). Each extra store is a second thing to back up, secure, keep consistent and delete personal data from, so it is a decision for an ADR (`architecture.md`), not a dependency added in passing. Many of these begin as an extension of the relational database and graduate only when volume demands it.

## The order of steps

Do not reorder these. Each one produces the input to the next.

### 1. Idea and scope

One paragraph. What is it, who uses it, what does it do.

### 2. Mini-PRD

Not a thirty-page document. A short list of the features the product needs, in plain language. Example shape:

> Workspaces represent individual companies. Users can join multiple workspaces. Channels live inside a workspace and are public or private. Channel memberships determine access. Messages are posted by users inside channels.

Every sentence there is a table and a relationship. That is the point of writing it.

### 3. Extract the entities

Read the PRD and pull out the nouns. Each becomes a table. Include the ones the PRD implies rather than names: "users can join multiple workspaces" is not two tables, it is three, because a join table has to exist.

List them all before designing any of them.

### 4. Fields and conventions

Give each table its own attributes only. Relationships come later.

Conventions, applied without exception:

- **Table names: lowercase and plural.** `users`, not `User`. A table holds many rows.
- **snake_case everywhere.** `first_name`, `avatar_url`, `is_private`. camelCase belongs to JavaScript, not to SQL. Mixing the two is the most common inconsistency there is, and it makes joins harder to read.
- **`created_at` and `updated_at` on every table.** Every one, even when you are sure you will not need them.

That last rule earns its own paragraph. Say a bot spams your waitlist with five thousand fake signups. Without timestamps, you have a table of emails and no way to tell fake from real. With them, you see five thousand rows created inside a one-minute window and you delete exactly those. The same field gives you auditability, analytics, and debugging. And you cannot add it retroactively to data that already exists.

### 5. Primary keys

Every row must be uniquely and stably identifiable. A table with duplicate values and no key is a table where you cannot tell two rows apart, which means you cannot reliably update or delete either one.

A primary key must be:

- **unique**
- **never null**
- **stable**, never mutated
- **not business logic**

That last one rules out the tempting choices. An email is unique and non-null, so it looks like a fine key, but the user can change it in settings, which breaks stability. Worse, business requirements change: drop the email field in favor of a phone number six months from now and every key in the database is gone.

So: a dedicated `id` column on every table, carrying no meaning.

> **VERIFY:** the id type your database and ORM recommend today (uuid, cuid, ulid, or auto-increment), how to generate it at the database or ORM level, and the current index implications of each. Look this up for the specific ORM version being installed. Sortable string ids are common now for a reason, but the recommendation moves.

### 6. Relationships and the ERD

Draw it. A diagram makes a wrong relationship obvious in a way a schema file never does. Any ERD tool works.

**One to one.** One user has one preferences record. Foreign key on one side, **marked unique**. Without the unique constraint you have accidentally built a one-to-many.

**One to many.** The most common. One user creates many portfolios; each portfolio belongs to one user. The "many" side holds the foreign key pointing at the "one" side's primary key.

**Many to many.** Users belong to many channels; channels have many users. You cannot express this with two columns. It requires a **join table** (also called a junction or bridge table) holding a foreign key to each side.

The join table is not plumbing to hide. It is usually a real entity with real fields. `channel_memberships` carries the member's `role`. `watchlist_items` carries when the item was added. If a join table has attributes, that confirms it deserved to exist.

Give the join table a **composite primary key** made of its two foreign keys. That is what makes "this user is in this channel" true at most once. Without it nothing stops the same pair being inserted twice, and you discover it as duplicated rows in a UI months later. If the row needs its own identity for other reasons, keep a surrogate `id` but still add a compound unique constraint on the pair. The uniqueness is the requirement; which mechanism expresses it is secondary.

Foreign keys are not optional decoration. They are what keeps referential integrity from drifting.

### What happens when the parent is deleted

Every foreign key answers this whether you decide it or not, so decide it. Three behaviors, and the right one is a product question, not a technical one:

| Action | Effect | Use when |
|---|---|---|
| **CASCADE** | Deleting the parent deletes the children | The child cannot exist alone: a post's comments, an order's line items, a membership when its channel is gone |
| **RESTRICT** | The delete fails while children exist | Losing the children would be data loss you would regret: a customer with invoices, a category with products still in it |
| **SET NULL** | Children survive with a null reference | The relationship is optional and the record has value without it: posts keep existing when their author's account is removed |

Ask "if this row disappears, what should happen to the rows pointing at it?" for every relationship in the diagram. CASCADE on the wrong edge deletes a customer's entire history because someone removed a workspace. RESTRICT everywhere makes ordinary cleanup impossible. SET NULL on a column declared non-null is a constraint violation waiting for the first delete.

Soft deletion (a `deleted_at` column instead of removing the row) sidesteps the question for the records you cannot afford to lose, at the cost of every query needing to filter on it. Decide that per table, at design time.

### 7. Optimization pass

Go back over the finished diagram:

- **Enums** for fields with a fixed, known set of values: `role` as owner/admin/member, `status`, `visibility`. A string column here is an invitation to typos.
- **Indexes** on foreign keys and on any column you will filter or sort by regularly.
- **Nullability**, deliberately. Which fields are genuinely optional?
- **Uniqueness**, deliberately. Slugs, emails, and the FK on the one-to-one side.

> **VERIFY:** current schema syntax for the ORM, how it wants enums declared, how indexes, composite primary keys and compound unique constraints are expressed, how referential actions are declared on a relation, and the current migration workflow. Look this up before writing the schema file, not after the first error.

## Changing the schema after launch

Everything above is design before traffic exists. Once the table is live the rules change, because a migration and a deploy are two separate events and you do not get to make them one.

**Deploys are rolling.** For seconds or minutes, old and new application code run against the same database. Even with a single instance, either the schema or the code lands first. So the schema must be valid for **both versions at every moment**, which rules out the entire category of change-it-in-place migrations.

Rename a column in one statement and, for thirty seconds, half your servers query a column that no longer exists. That is not a slow migration, it is an outage.

### Expand and contract

Add before you remove, and never remove in the release that adds. Renaming `user_name` to `username`:

1. **Expand.** Add `username`, nullable. Old code ignores it entirely.
2. **Dual-write.** Deploy code writing both columns, still reading the old one. Live traffic keeps both current, and this deploy is safely reversible.
3. **Backfill.** Populate `username` from `user_name` in batches.
4. **Switch reads.** Deploy code reading `username`. The old column is still there as a safety net; verify in production before trusting it.
5. **Contract.** In a later release, once no deployed code references it, drop `user_name`.

Five small boring pull requests instead of one risky one. The release boundary between adding and removing is exactly what keeps the change reversible, and every step but the last rolls back by redeploying the application without touching the database.

### The operations that lock the table

Some statements take an exclusive lock and rewrite the whole table, blocking every read and write for the duration. On a large table that is minutes of downtime from one line of SQL.

| Instead of | Do |
|---|---|
| `SET NOT NULL` directly | Add nullable, backfill, add a `CHECK (col IS NOT NULL) NOT VALID`, `VALIDATE CONSTRAINT` online, then `SET NOT NULL`, which now skips the scan |
| `CREATE INDEX` | `CREATE INDEX CONCURRENTLY`. Slower, cannot run inside a transaction, and check afterward that the index is valid since a concurrent build can fail |
| Adding a column with a volatile default | Add it nullable. A non-volatile default is cheap on modern Postgres, stored in the catalog and applied lazily; a volatile one rewrites the table |
| `ALTER TYPE` in place | Treat it as a rename: new column, dual-write, backfill, switch, drop |
| `RENAME` on a live table | Expand and contract. A rename is a remove-and-add wearing a disguise |

Adding a constraint is two steps for the same reason: `NOT VALID` first, which is fast and skips the full scan, then validate it online.

### Backfills

Never one `UPDATE` across millions of rows. It holds locks on every row it touches until the transaction commits, bloats the table, and floods replication. That is the outage you were avoiding.

Walk the table in ordered batches using the primary key as a cursor, each batch in its own short transaction, pausing between them. Log progress so it can resume after a crash, and throttle if replication lag or pool pressure builds.

Batching trades away transactionality, so the backfill can stop halfway. Make it **reentrant**: running it twice produces the same result as running it once. Copying a value from one column to another on the same row is naturally reentrant; incrementing is not.

Run it as a one-off job, never inside a request handler.

### Timeouts that keep a mistake from becoming an outage

Set these at the database level and they will save you at least once:

- **`lock_timeout`.** A pending exclusive lock does not merely wait for current readers, it **blocks every new query behind it**, because the lock queue is FIFO across conflicting levels. The table goes fully inaccessible before the migration has executed a single statement. With a short lock timeout the migration fails fast, the queue drains, and the deploy script retries in a quieter moment.
- **`statement_timeout`.** Aborts any runaway query. A statement running thirty minutes is either wrong or belongs in a background job.
- **`idle_in_transaction_session_timeout`.** Kills the forgotten `BEGIN`, someone's psql session left open when a meeting started, that blocks vacuum and every DDL statement behind it. The single highest-value setting here.

### Rolling back

Do not count on a down migration. Reversing a dropped column means restoring data, not running the inverse DDL, so the file is theatre.

Roll forward instead. Expand and contract already gives you the rollback: every phase except the final drop is undone by redeploying the previous application version. Make the drop its own migration, deployed separately from any code change, with a fresh backup taken immediately before, and accept that recovering from it means point-in-time restore.

> **VERIFY:** how the ORM's migration tool lets you edit generated SQL before applying it, whether it can run statements outside a transaction (concurrent index builds require this), and how it orders migrations against deploys in your pipeline. Migration tools differ sharply here and the generated SQL frequently needs hand-editing to be safe.

## The pieces

Everything above is schema design and holds whatever you use to talk to the database. The choice of client is narrower than the debate around it suggests, because the two mature options differ mostly in one axis: **how much sits between your query and the SQL.**

A **generated-client ORM** owns a schema file in its own language, generates a typed client from it, and brings a mature migration toolchain. A **thin SQL layer** declares the schema in TypeScript, infers types immediately, and produces queries that look like the SQL they become.

Three things actually decide it:

- **Does the deployment care about cold starts and bundle size?** Edge runtimes and cold serverless functions do; a long-lived container does not. The thin layer wins by an order of magnitude here, though the gap narrowed considerably when the heavier engine was rewritten.
- **How close to SQL is the work?** Several patterns in this skill — keyset pagination with a tuple comparison, setting a session variable so a policy can read it, a batched backfill walking the primary key — are SQL-shaped. A thin layer expresses them directly; a generated client makes you drop to raw queries, which is fine but means the type safety you paid for stops exactly where the difficulty starts.
- **Is row-level security in the plan?** Then the deciding question is mechanical: can the client set a session variable per transaction, and does it guarantee that variable cannot leak across pooled connections? Get that answered before committing, because it is the difference between policies that work and policies that intermittently see the wrong tenant. `multi-tenancy.md` covers what depends on it.

Neither is a wrong answer. What is wrong is choosing on benchmark headlines and discovering the third question afterwards.

### Three mistakes both of them let you make

**Pushing the schema straight to a production database.** Every tool ships a command that syncs the schema without generating a migration file. It is genuinely useful while prototyping and it is destructive in production: no reviewable SQL, no history, no rollback, and a column drop the tool inferred on its own. Production changes go through generated, reviewed, committed migrations — the process in the section above.

**Assuming foreign keys are indexed.** They are not, by either tool. Declaring a relation creates a constraint, not an index. The symptom arrives months later as a join that got slow, and the fix is the rule already stated above: index every foreign key, explicitly.

**Trusting a generated migration for a rename.** Migration generators compare two schemas; they cannot see that a column was renamed rather than one dropped and another added. The generated SQL will happily drop the old column with its data. Always read the SQL before applying it, and treat a rename as the expand-and-contract sequence described above.

> **VERIFY:** the current major of the client in use, its schema syntax, how it wants relations and indexes declared, whether it now creates foreign key indexes by default, and its command for editing generated migration SQL before it runs. Both mature options have had significant releases recently, including one that replaced its query engine entirely.

## Common mistakes

| Mistake | Consequence |
|---|---|
| Business logic as the primary key | Key becomes unstable or disappears when requirements change |
| Renaming or dropping in one step | Old code queries a column that vanished; errors during every rolling deploy |
| Single `UPDATE` for a backfill | Locks, bloat, replication lag, the outage you were preventing |
| `CREATE INDEX` without concurrency on a live table | Writes blocked for the entire build |
| No `lock_timeout` on migrations | One pending lock makes the table inaccessible to everything |
| No timestamps | No auditing, no bot cleanup, cannot be added retroactively |
| Mixed naming (camelCase and snake_case) | Confusing joins, constant friction |
| Singular table names | Inconsistent, reads wrong |
| Missing foreign keys | Referential integrity drifts silently |
| Directly connecting two many-sides | Impossible; needs a join table |
| Join table with no composite key or unique pair | The same membership inserted twice, found months later |
| Delete behavior left to the default | Either an unremovable row or a cascade that erases history |
| One-to-one without a unique FK | Silently a one-to-many |
| Generating the schema from a prompt | Missing relationships, wrong types, phantom tables |
| A document or second database chosen "for flexibility" | Relationships and transactions rebuilt by hand, and one more store to secure and back up |
| Schema pushed directly to production | No reviewable SQL, no history, no rollback, and inferred column drops |
| Assuming a declared relation created an index | Neither tool does; the join gets slow months later |
| Applying a generated rename migration unread | The old column is dropped with its data |
