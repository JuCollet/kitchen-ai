# Kitchen: concept, scope and decisions

This document freezes the concept and the framing decisions (brainstorming of 16 November 2026, which replaces the 11 November version). Any change of scope must be recorded here.

## The concept

Kitchen (kitchen.julco.dev) is a cooking assistant **with no chat interface of its own**. Each person keeps their favourite AI (Claude, ChatGPT or any MCP-compatible client). Kitchen, plugged in as an MCP server, provides:

- **the memory**: profiles, appliances, recipes, ratings, menus, shopping lists and pantry, stored precisely and permanently, shared within the household;
- **the brain**: behaviour instructions and ready-to-use prompts for common actions;
- **the AI is the interface**: it presents recipes and menus (text, interactive cards, artifacts…), except for objects shared and edited by several people (the shopping list) and for the printable PDF, which Kitchen provides.

### Value proposition

> **Your recipe book and cooking history, precise and permanent, shared with your household, usable from any AI.**

- **Exact persistence**: the native memory of AIs keeps vague summaries. Kitchen keeps every recipe, every rating, every adjustment, and supports precise queries ("recipes rated 8 or more, not cooked for a month"), even a year later.
- **Multi-AI household**: the person who cooks is not always the one who shops. Everyone uses their own AI, everyone shares the same book, the same menu and the same list.
- **Data belongs to the user**: exportable, independent of any platform.

## Goals and audience

- **Two goals, equally important**: learn a production-grade backend and infrastructure stack (Java, Spring, Postgres, OAuth, k3s, GitOps) **and** ship a product that is actually used.
- **v1 audience**: Julien's household and friends, **by invitation**. Opening to the public later.
- **AI clients**: any MCP-compatible client, with Claude and ChatGPT tested first.
- **Languages**: French, Dutch, English (site, instructions, PDF, widget). Recipes stay in the language they were written in.

## Households and people

- **Each person has their own account** (Google sign-in or passkey) and connects their own AI.
- **Data lives at the household level**: recipes, appliances, menus, shopping lists, pantry.
- **What stays personal**: recipe ratings, dietary constraints (diet, allergies, dislikes), language. A household menu respects the **union** of its members' constraints and favours recipes everyone likes.
- **A person belongs to a single household** in v1. People join a household by **invitation** (single-use link or code, sent by a member).
- **When someone leaves the household**, the recipes stay with the household; their ratings and personal profile leave with them.

## Appliances

- **No catalogue maintained in code.** When a household declares an appliance, the AI writes its **spec sheet** (actual modes, temperature ranges, capacity, remarks) and **has the user confirm it**; the user can correct it or paste an excerpt of the manual. The confirmed sheet is stored in Kitchen.
- **Each appliance has a usage preference**:
  - **default**: every recipe uses it whenever possible (example: "I want to cook everything in the Ninja Speedi");
  - **suggest**: the AI may suggest it when relevant (example: an air fryer you don't want to use all the time);
  - **on request**: only when explicitly asked.
- **Each menu can override the appliances for its period** ("this week, only the Cookeo" or "no Speedi") without changing the permanent preferences.
- **Each recipe is linked to one appliance**, or to none for a classic recipe (pan, pot, oven).
- **The AI relies on the spec sheet, not on its memory**, and the server rejects a recipe that uses a mode missing from the sheet. Goal: never see an invented mode again.

## v1 features

### AI side (MCP server)

- **Conversational onboarding**, driven by the server: personal profile, then household profile and appliances if this is the first member.
- **Prompts** (one-click actions in the AI app):
  - **Weekly menu**: a menu for a chosen period, a consolidated shopping list, choice of appliances for the period;
  - **Recipe of the day**: a suggestion for tonight;
  - **Rate a recipe**: feedback after cooking, rating out of 10 and adjustments;
  - **Clear the fridge**: a recipe using what is left in the pantry.
- **Recipes**: creation, editing, precise search, rating history. Rating out of 10 per person (9 = perfect, nothing to change; 10 = reserved for the best recipe ever tasted).
- **Import**: **the AI does the import**. It reads the source with its own tools (URL, pasted text, Notion page through its connector…), structures the recipe and saves it. Nothing to parse on the server side.
- **Semi-automatic pantry**: items checked on the shopping list go into the pantry; when a recipe is rated, the AI offers to remove what was used; it can also be corrected in conversation. Following menus reuse what is left.
- **Nutrition**: an indicative estimate provided by the AI, always presented as such.
- **Voluntary sharing between households**: a household can publish a recipe; other households can discover it (with its average rating) and import it into their book.

### Provided by Kitchen

- **Shopping list widget (MCP App)**: list grouped by aisle, checkable with one tap, **synchronised** between household members and between AIs. For clients without MCP Apps, the tool also returns a link to a **fallback web page** showing the same list.
- **Menu PDF** to download, nicely laid out, printable in black and white: meal plan, shopping list by aisle with checkboxes, one sheet per recipe (ingredients, steps, appliance settings, times, estimated nutrition) with **benefit labels**. Generated in the language of the person who requests it, served through a signed, temporary link.
- **Minimal website**: home page, "Connect your AI" guide per client, "My account" page (see your data, manage the household and invitations, export your data, delete your account).

### Store

- **Generic**: standard aisles valid everywhere (produce, frozen, meat and fish, dairy, bakery, dry goods, canned goods, condiments, drinks, household, other).

## Out of v1

- Store promotions (Delhaize or others)
- Reminders and notifications (the AI never takes the initiative; we observe whether usage holds without them)
- Appliance catalogue shared between households (could emerge from validated sheets)
- Several households per person
- Opening to the public, listing in connector directories
- Web interface to browse recipes or menus (that is the AI's job)

## Known risks

- **Uneven MCP client support**: prompts, MCP Apps and how well instructions are followed vary by client and model. Claude and ChatGPT are tested first, and plain text always remains usable.
- **Setup friction**: adding a connector is still a chore. The guide must be flawless.
- **Prompt injection through sharing between households**: a recipe published by another household is text written by a third party. Structured fields, limited sizes, and instructions that require treating it as data, never as instructions.
- **Health data** (allergies): explicit consent, GDPR.

## v1 success criterion

Test households use Kitchen every week for at least two months: menus generated from existing recipes, recipes rated, shared shopping list used in the store.
