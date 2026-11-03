# Accessibility

The failure this prevents is not "some users cannot use the app". It is more specific and easier to create by accident: **the app communicates entirely through pixels, and every change happens silently.**

A filter runs and 40 rows become 3 — visible instantly to someone watching, invisible to someone listening. A page changes and focus falls back to the top of the document, so a keyboard user walks through the entire navigation again to reach the table they were reading. A form fails validation and the fields turn red, which is nothing at all to a screen reader and nothing at all to the ~8% of men with a color vision deficiency.

This is a reference in an architecture skill rather than a design one because the fixes are structural. They live in how the markup is chosen and where state changes are announced, and both get much more expensive to retrofit than to build. It pairs with `list-views.md` and `mutations.md` — those two describe screens made almost entirely of the patterns that break here.

## Semantic HTML is the whole foundation

Native elements arrive with a role, keyboard behavior, focus handling and screen reader support already correct. A `<button>` is focusable, activates on Enter and Space, announces itself as a button, and works with voice control. A `<div onClick>` does none of that, and making it equivalent takes a role, a tabindex, two key handlers and a disabled state you will get subtly wrong.

So the rule is not "add ARIA". It is **use the element that already means what you mean**, and reach for ARIA only where no element exists. The first rule of ARIA is not to use ARIA — a native element beats a correct ARIA reimplementation of it, and beats an incorrect one by a mile.

The practical consequence for a codebase: a custom component built on divs is a permanent maintenance cost. When you use a component library, the primitives are usually built on the right elements with the keyboard behavior implemented and tested. That is most of the value they provide, and it is thrown away by rebuilding a select or a dialog by hand.

## Tables

Use a real `<table>` with `<thead>`, `<tbody>`, `<th>` and `<td>`. Divs styled as a grid produce a wall of unrelated text: no row and column relationships, so a screen reader cannot say "Status: overdue" when reading a cell — it reads "overdue" with no idea which column that was.

**Headers declare their direction.** `scope="col"` on column headers, `scope="row"` on the cell identifying its row. This is what lets a cell be announced with its context.

**A sortable header is a button inside the `<th>`, and the sort state goes on the `<th>`.** The button is what makes it operable by keyboard and announced as clickable; the state attribute is what makes the current sort perceivable rather than implied by an arrow glyph.

```html
<th scope="col" aria-sort="ascending">
  <button type="button">Name <span aria-hidden="true">▲</span></button>
</th>
```

The arrow is hidden from assistive tech because the sort attribute already conveys it; leaving it exposed means hearing a triangle character read aloud in every header.

**Do not claim to be a grid unless you are one.** There are two patterns and they differ in keyboard contract: a plain table leaves the browser's normal tab order alone, while a grid becomes a **single tab stop** whose cells are navigated with arrow keys, Home, End, Enter to enter a cell and Escape to leave it. Declaring the grid role without implementing all of that produces something that announces a keyboard model it does not have, which is worse than the plain table you started with. Most product tables are tables. Reach for a grid when cells are genuinely editable, spreadsheet-style.

## Focus is a thing you own

Focus is the keyboard user's cursor. Anything that removes the focused element without putting focus somewhere sensible drops it to the top of the document, and the user has to walk back through the entire page.

Four moments in the screens this skill describes, and all four are easy to miss because a mouse never reveals them:

| After | Focus should go to |
|---|---|
| Changing page | The table container or its first cell — not the body |
| Applying a filter that removed the focused row | The filter control, or the results container |
| Opening a modal | Inside it, and stay trapped there while it is open |
| Closing a modal | Back to the element that opened it |
| Deleting a row | The next row, or the container if it was the last |

The modal pair is the one to get right first, because it is the most common and the most disorienting when wrong: focus left behind a dialog means a keyboard user is tabbing invisibly through the page underneath.

Also: **never remove focus outlines.** If the default is ugly, style it. Removing it makes the app unusable by keyboard while looking identical to everyone else, which is why it survives review so often.

## Announcing what changed

Server-driven filtering and mutation both change the page without a navigation, so nothing announces. A live region fixes this: a container whose content, when it changes, is read out without moving focus.

Three things make the difference between helpful and maddening:

- **Announce the outcome, not the mechanism.** "23 results" or "No results for that filter" — not "loading".
- **Debounce it.** Wired to a search input without a delay, it announces on every keystroke and the user cannot hear their own typing. The delay from `list-views.md` serves both purposes.
- **The region must exist in the DOM before it has content.** A region rendered at the same moment as its message is frequently not announced at all, because assistive tech never saw an empty region to watch. Render it empty and fill it.

This is the accessible counterpart of the pending state in `mutations.md`: same event, second channel.

## Forms

Everything in `mutations.md` about field-level errors has an accessibility half, and it is small:

- **Every input has a real `<label>` tied to it.** Placeholder text is not a label — it disappears when typing starts, and it fails contrast requirements almost everywhere it is used.
- **Errors are linked to the input, not merely near it.** Point the input at the error's id with `aria-describedby` and mark it invalid. Otherwise the error is a paragraph floating in the page that a screen reader user reaches only by chance, if at all.
- **Errors are text.** A red border communicates nothing to a screen reader and nothing to someone who cannot distinguish the red. Color can reinforce the message; it can never be the message.
- **On a failed submit, move focus to the first invalid field** or to a summary listing the errors as links. Otherwise the user submits, hears nothing, and has no idea what went wrong or where.

That is the whole list. It costs almost nothing at build time and is tedious to retrofit across forty forms.

## Comboboxes and anything else that is hard

An autocomplete or a filter dropdown is one of the hardest widgets to build correctly: it needs a specific set of roles, a relationship between the input and the list, a way to indicate the highlighted option without moving real focus, and a full keyboard model.

**Use the library primitive.** This is the clearest case in this file where building it yourself is a bad trade — you will get the visual behavior right in an hour and the assistive-technology behavior wrong for years. `design-system.md` already says the component library owns the primitives; this is the strongest reason why.

If it must be custom, know that there are two focus strategies — moving real focus between options, or keeping focus in the input and pointing at the active option — and that mixing them produces a widget that announces one thing and behaves as another.

One mobile detail that catches everyone: the on-screen keyboard does not resize the window on iOS, it shifts the layout. A full-screen picker sized to the window height renders its actions off-screen behind the keyboard. Size it to the visual viewport instead.

## What tools catch, and what they do not

Automated checks — a lint plugin for JSX, an audit in the browser or in CI — reliably catch missing alt text, missing labels, invalid ARIA and contrast failures. Wire them in; they are cheap and they enforce the boring half.

They find roughly a third of real issues. They cannot tell you that focus vanished after pagination, that the announcement never fired, or that the tab order jumps around the page, because those are behaviors rather than markup.

The check that finds those takes two minutes: **put the mouse away and use the feature with the keyboard only.** Tab to it, operate it, complete the task. If you get stuck, lose your place, or cannot tell what is focused, so will your users. Then turn on the screen reader built into your OS and do it again.

This is the same lesson as `lint-guardrails.md`, in a different domain: the tool checks the shape, and the meaning needs a person. It belongs in the same place too — the automated checks in CI, the manual pass when building a feature with a new interaction.

> **VERIFY:** the current accessibility lint plugin for the framework and whether it is included by default, and the current automated audit tooling. Rule coverage changes; the manual keyboard pass does not.

## Common mistakes

| Mistake | Consequence |
|---|---|
| Table built from divs | No row and column relationships; cells read without their headers |
| Sortable header with no sort state | The current sort exists only as an arrow glyph |
| Sort icon not hidden from assistive tech | A triangle character read aloud in every column header |
| Grid role without the grid keyboard model | Announces a contract it does not honor |
| `<div onClick>` instead of a button | Not focusable, not keyboard-operable, not announced |
| Focus outline removed | The app becomes unusable by keyboard, invisibly to everyone else |
| Focus dropped after pagination or filtering | The user restarts from the top of the document |
| Modal that does not return focus on close | Tabbing continues invisibly behind the dialog |
| Filter results never announced | The screen changed and nothing said so |
| Live region rendered at the same time as its message | Frequently never announced at all |
| Live region wired to every keystroke | Constant interruption; the user cannot hear their own typing |
| Placeholder used as the label | Disappears on typing, and usually fails contrast |
| Error message near the input but not linked to it | Reached only by chance |
| Error signalled only by color | Invisible to screen readers and to color-blind users |
| No focus move after a failed submit | The user hears nothing and does not know what failed |
| Hand-built combobox | Months of subtle assistive-technology bugs for an afternoon of visual work |
| Automated audit treated as sufficient | Catches about a third; misses every behavioral failure |
