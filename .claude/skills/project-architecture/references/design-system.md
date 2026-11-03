# Design system

## Ownership is the whole point

The difference between a copy-in component library and a traditional one is not aesthetics or community size. It is **ownership**.

With a packaged library, the component lives in `node_modules`. You import it, and every customization happens from the outside: wrapper components, override props, specificity fights. You cannot open the source and change it.

With a copy-in library, a CLI copies the source into your project. You own it. Customization happens from the inside, because it is your file.

Which means it is not a component library at all. It is **the foundation for your design system**. Treated as a shortcut to nice-looking components, it produces a site identical to everyone else's, followed by panic customization: a `bg-blue-500` here, a `rounded-xl` there, and an inconsistent mess a month later.

## The decision procedure

Before styling anything, ask these in order and stop at the first yes:

1. **Can theme tokens solve it?** Update the global stylesheet. This is most cases.
2. **Is it a reusable component style?** Add a **variant** to the component. Do not pass classes from the outside.
3. **Is it a product-specific pattern?** Create a **wrapper component**.
4. **Is it genuinely one-off?** Only then, a class from the outside. This should be roughly 10% of cases.

Working top-down keeps the design language intact. Working bottom-up, which is what everyone does by default, means every instance carries its own overrides and the system stops being a system.

## Folder split

```
components/
├── ui/            # primitives from the CLI. Yours to edit, but they stay primitives.
└── <feature>/     # product composites built from those primitives.
```

The line is real: `ui/` holds things any product could use (button, card, dialog). `<feature>/` holds things only this product has (a metric card, an invoice row, an org switcher).

The wrapper rule is where most codebases fail, including official example dashboards. Four metric cards on a page, each built by hand from card + header + action + badge, is the same component written four times. Extract it once and use it four times. Not repeating yourself here is not just tidiness; it forces the composite boundary to exist.

**Composition over boolean props.** When a component sprouts `isCompact`, `hasIcon`, `showFooter`, that is several components wearing a trenchcoat. Build the composite instead.

## Tokens

Semantic tokens, never literal colors. `bg-primary`, not `bg-blue-500`.

This rule and the variant rule below are linted, not remembered: `@shadcn/lint` in the lint preset rejects palette colours, arbitrary values and classes passed onto a component, and its errors name the declared token or variant to use. `lint-guardrails.md` has the setup.

The reason is what happens at rebrand. Define `--blue` and use `bg-blue` everywhere, then decide the brand is green: you update the variable's value, but its *name* still says blue, so now you also open every component to rename it. The refactor is the entire codebase.

Define `--primary` instead and rebranding is one value in one file. Every component reads from the token and updates for free.

Junior thinking: "I need a blue button, let me make a blue variable." Senior thinking: "what is this product's primary action color?" The default tokens (`muted`, `accent`, `border`, `input`, `destructive`, and their `-foreground` pairs) are already semantic. Follow that pattern for anything you add.

### OKLCH

Modern color format: **L**ightness, **C**hroma, **H**ue.

- **L** is perceived lightness, 0 (black) to 1 (white).
- **C** is chroma, how intense the color is. 0 is gray.
- **H** is the hue angle, 0 to 360. Roughly 20° red, 90° yellow, 220° blue.

It is worth using because it lets you reason about colors instead of guessing hex codes. Need the same color one step darker? Lower L, leave C and H alone. Building a palette at consistent perceived lightness is arithmetic rather than trial and error.

### The config file went away

The structural change worth knowing before writing any of this: the styling framework moved its design system **out of a JavaScript config and into CSS**. There is no longer a config file exporting a theme object; the tokens are custom properties in the global stylesheet, and a directive maps them onto utility class names. A project still carrying the old config file is carrying a file that is no longer read, which is a confusing thing to debug — it looks like the source of truth and it changes nothing.

Three failure modes come with the new arrangement, and they produce symptoms that do not point at their cause:

- **Declaring the token blocks inside a base layer.** The cascade order breaks and values apply inconsistently rather than not at all, which is worse to diagnose. Declare them at the top level.
- **Double-wrapping color functions.** Where the old setup stored bare channel values and wrapped them at the use site, the current one stores the complete color and handles opacity itself. Wrapping twice yields an invalid color and the element renders unstyled.
- **Animation and utility plugins that assumed the old config.** Several were replaced by CSS-first equivalents; the old ones fail silently because there is no config left to read them.

> **VERIFY:** the current directive for mapping tokens to utilities, whether tokens are declared bare or wrapped in a color function, which companion plugins have CSS-first replacements, and what the component library's config file should say now that there is no framework config to point at. This area changed wholesale in the last major, so anything written from memory will be the previous system.

### Theming and dark mode

Generate a coherent theme rather than hand-tuning every variable. Hand-tuning means owning contrast and color relationships across both modes yourself, which is real work and easy to get subtly wrong.

Dark mode is a second set of values for the **same** token names. No component knows which mode is active; they all read `bg-background` and get the right answer. That only works if every color is a token, which is the payoff for rule one.

## Variants

Variants are how you extend without breaking the foundation. The base styles already give you typography, spacing, radius, focus, and disabled states. A variant adds on top and inherits all of that.

```ts
// inside the component file, alongside the existing variants
variants: {
  variant: {
    default: "...",
    outline: "...",
    premium: "bg-gradient-to-r from-primary to-accent text-primary-foreground",
  },
}
```

Adding `premium` here makes it available everywhere, typed, consistent, and discoverable. Writing those same classes at one call site makes it invisible to everyone else and guarantees the second person reinvents a slightly different version.

> **VERIFY:** the current variant utility and its API. The library and its API surface have both changed names and signatures across versions.

## Common mistakes

| Mistake | Instead |
|---|---|
| Editing base styles to customize | Add a variant |
| Overriding with classes at every call site | Tokens, variants, or a wrapper |
| Literal color names in tokens (`--blue`) | Semantic names (`--primary`) |
| Repeating the same composition inline | Extract a wrapper component |
| Boolean props accumulating on one component | Compose separate components |
| Product-specific components in `ui/` | `ui/` stays primitives |
| A leftover framework config file | Delete it; it is no longer read and reads like the source of truth |
| Token blocks nested inside a base layer | Declare them at the top level, or the cascade misbehaves |
| Color tokens wrapped twice | Store the complete color; the framework handles opacity |
