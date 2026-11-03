# Privacy

Two questions this answers. What personal data does the product hold, and could you honour a user's request to see it, correct it or delete it?

`security.md` keeps attackers out. This is about what you do with the data of people who let you in. It is decided in the schema and in the list of third parties, and both are expensive to change once data exists: a deletion that was never designed turns into a manual search across tables, backups, logs and vendors.

The rules below come from data-protection law, which in Colombia is Ley 1581 de 2012 and its regulation. Other jurisdictions (the GDPR in Europe) differ in detail and agree on the shape. This file is architecture, not legal advice; it gives the product the mechanisms the law assumes exist.

## Know what you hold

Keep a **data inventory**: every personal field, where it lives, why it is collected, and who else receives it. It starts as a column in the ERD during discovery (`database.md`) and a list of vendors in `AGENTS.md`.

- **Purpose.** Every field has a stated reason tied to a feature. Data collected "in case it is useful" has no legitimate purpose to point at, and it is the data most often leaked.
- **Minimise.** Collect the field the feature needs, at the precision it needs. A birth year instead of a birth date, a city instead of an address.
- **Sensitive data is a different category.** Health, biometrics, ethnic origin, political or religious views, sexual life, union membership. Treating it needs explicit consent and stronger protection, so mark it in the schema and keep it out of every system that does not strictly need it: logs, analytics, search indexes, exports.
- **Children's data** is restricted by default. If the product can have minors as users, that is a discovery question, not a later patch.

## Consent and purpose, recorded

Consent under Ley 1581 is prior, express and informed: obtained before the data is used, for a purpose the person was told. Two consequences for the design:

- **Record the consent itself.** Who agreed, to which version of the policy, for which purposes, when. The law gives the person the right to ask for proof of their authorization, and a boolean column cannot prove anything. A consent table with the policy version is the usual shape.
- **Purposes are separate switches.** Agreeing to the service is not agreeing to marketing email or to behavioural analytics. Each optional purpose has its own consent, and revoking one leaves the others.

Where consent gates a system (analytics, marketing pixels), the gate lives in one place: the capability for that system checks it, per `product-analytics.md`. A consent check repeated at every call site is a consent check someone forgot.

## Every personal record has an exit

The person may ask to know, update, correct and delete their data, and to revoke consent. Design the exit before the entrance:

- **Deletion follows the relationships.** For every table holding personal data, decide whether a user's deletion removes the row, anonymises it, or keeps it because another law requires it (invoices, for instance). That decision is the same referential-action question from `database.md`, asked about the person rather than the parent row.
- **Anonymise what must survive.** Aggregates, audit history and financial records can keep their shape with the personal fields replaced. Soft deletion is not deletion: a `deleted_at` flag keeps every field readable.
- **The exit reaches every copy.** The analytics vendor, the email provider, the search index, file storage and logs all hold pieces. The data inventory is the list the deletion job walks. Backups are the exception you document: they expire on their retention schedule instead of being edited.
- **Export uses the DAL.** A user's data export is a query per module through the same DAL and DTOs as the app, so it inherits the tenant scoping and never exposes another person's rows.

Deletion and export are slow and touch many systems, so they are jobs (`async-work.md`), idempotent, with the request recorded so you can prove it was honoured.

## Where the data travels

Every vendor that receives personal data is a data transfer, and a transfer to another country is regulated: Ley 1581 (article 26) forbids sending personal data to countries without an adequate level of protection, with listed exceptions such as the person's express consent or the execution of a contract.

- **Pick hosting regions on purpose.** The database, file storage, logs and analytics each have a region. Choose them in discovery and record them.
- **List every processor** in `AGENTS.md` next to the data it receives. A new SDK added to the client is a new processor, whether or not anyone thought of it that way.
- **Send ids, not identities.** Third parties that only need to count or correlate get opaque ids. `operations.md` applies the same rule to logs.

## Retention

Data kept forever is risk kept forever. Give each category a retention period tied to its purpose (inactive accounts, raw analytics events, logs, uploaded files) and enforce it with a scheduled job, not a policy document. Logs and analytics vendors usually have a retention setting; set it rather than accepting the default.

## Obligations outside the code

The product supports these; it does not replace them. A published **data processing policy** (política de tratamiento de datos), a channel for requests and complaints with the legal response times, and, for companies above the asset threshold, registration of the databases with the Superintendencia de Industria y Comercio. Mention them in the blueprint so they are somebody's task.

> **VERIFY:** the current regulation and guidance for the jurisdictions the product serves: for Colombia, Ley 1581 de 2012, its regulatory decree and the SIC's current guidance on international transfers and the Registro Nacional de Bases de Datos threshold; for users in the EU, the GDPR and the ePrivacy rules on cookies and consent. Also each vendor's data regions, retention settings and deletion API.

## Common mistakes

| Mistake | Consequence |
|---|---|
| No data inventory | A deletion request becomes an archaeology project across unknown systems |
| Collecting fields "in case they are useful" | No purpose to justify them, and the most to lose in a breach |
| Consent stored as a boolean | No proof of what the person agreed to, or when |
| One consent for every purpose | Marketing and analytics ride on consent given for the service |
| Soft delete presented as deletion | Every personal field is still readable |
| Deletion that forgets vendors and indexes | The data survives in the systems nobody listed |
| Sensitive data in logs or analytics | The strictest category copied into the widest-read systems |
| Hosting regions left at the vendor default | An international transfer nobody decided |
| A client SDK added without listing it | A new processor receiving personal data, unrecorded |
| No retention job | Data kept forever because nothing ever removes it |
