# Contactotomy User Guide

Contactotomy is a **local, offline macOS desktop app** that cleans up contact lists accumulated across Apple and Google accounts. Nothing leaves your machine.

This guide walks you through the full process: back up → run the wizard → re-import your cleaned contacts → keep things tidy over time.

---

## Contents

1. [Back up and export your contacts](#1-back-up-and-export-your-contacts)
2. [Get and run the app](#2-get-and-run-the-app)
3. [The five-step wizard](#3-the-five-step-wizard)
   - [Import](#step-1-import)
   - [Merge](#step-2-merge)
   - [Tidy](#step-3-tidy)
   - [Deletion](#step-4-deletion)
   - [Export](#step-5-export)
4. [Upload your cleaned contacts back](#4-upload-your-cleaned-contacts-back)
5. [Keeping Apple and Google in sync going forward](#5-keeping-apple-and-google-in-sync-going-forward)

---

## 1. Back up and export your contacts

Do this before anything else. Contactotomy never modifies your source files, but you will delete your existing contacts later in the process, so a backup is essential.

### Apple (iCloud / Mac Contacts)

**On Mac:**

1. Open the **Contacts** app.
2. Select all contacts: **Cmd+A**.
3. Choose **File → Export → Export vCard…**
4. Save as `apple.vcf`.

**On iCloud.com:**

1. Go to [icloud.com/contacts](https://icloud.com/contacts).
2. Select all contacts (gear icon → **Select All**).
3. Click the gear icon again → **Export vCard**.
4. Save the downloaded file as `apple.vcf`.

### Google

1. Go to [contacts.google.com](https://contacts.google.com).
2. Click **Export** in the left sidebar.
3. Choose **vCard (for iOS Contacts)**.
4. Save as `google.vcf`.

Keep both files somewhere safe. These are your backups.

---

## 2. Get and run the app

Contactotomy is a Kotlin + Compose Desktop app. Today, you build and run it from source.

### Requirements

- JDK 17 or later
- macOS (Apple Silicon or Intel)

### Build and run

```bash
git clone https://github.com/robsartin/contactotomy.git
cd contactotomy
./gradlew run
```

The app opens in a desktop window.

**Alternatively, build a distributable macOS DMG:**

```bash
./gradlew packageDmg
```

The DMG is written to `build/compose/binaries/main/dmg/`. Open it, drag Contactotomy to Applications, and launch it from there like any other Mac app.

> **Note:** The DMG built this way is not code-signed or notarized. macOS Gatekeeper will warn when opening it on a machine other than the one that built it. To bypass the warning, right-click the app in Applications and choose **Open**. Apple Developer code signing and notarization are not configured in this project.

**Privacy note:** Contactotomy reads and writes files only where you tell it to. No network connections are made. Your contacts never leave your machine.

---

## 3. The five-step wizard

The app steps you through five stages shown at the top of the window: **Import → Merge → Tidy → Deletion → Export**. A triangle (▸) marks the current step. Use **Back** and **Next** to navigate; **Next** is disabled on the Export step, and it is disabled on Import until at least one file has been loaded.

---

### Step 1: Import

**What it does:** Loads your vCard files into the app.

The Import screen has three buttons:

- **Choose Apple export** — opens a file picker labelled "Choose Apple vCard export". Select your `apple.vcf`.
- **Choose Google export** — opens a file picker labelled "Choose Google vCard export". Select your `google.vcf`.
- **Add another file…** — for any additional vCard files (other accounts, local backups, etc.).

Each file you load appears in the list below with its path, source tag, and contact count. A running total shows how many contacts are loaded across all files. If you load the wrong file, click **Remove** next to it.

When you are happy with what is loaded, click **Next**.

---

### Step 2: Merge

**What it does:** Finds duplicate clusters — two or more cards that likely represent the same person — and lets you decide how to combine them.

The screen is split left and right:

- **Left panel:** lists all clusters that need a decision, grouped as "Needs review" (pending) and "Resolved" (decided). A progress indicator shows how many you have reviewed out of the total. The status line at the bottom reads: `Will merge N clusters · M still pending`.
- **Right panel:** shows the detail for whichever cluster is selected.

#### Reviewing a cluster

The detail panel shows the source cards at the top and a "Merged result — tick what to keep" card below.

For each cluster you choose:

| Field | Controls |
|---|---|
| **Name** | Radio buttons: pick one source card's name, or choose "(no name)". Names that look like company names are tagged "· looks like a company". |
| **Phones, Emails, Categories** | Toggle pills: all values from all cards are shown; click a pill to include or exclude it. |
| **Company / org** | Radio buttons: existing org values plus "(from name)" promotions if any card's name looks like a company name. Choose one or "(none)". |
| **Other conflicting fields** (e.g. title, notes) | Radio buttons: pick one value, or "(clear)". |

Then press one of the two decision buttons:

- **✓ Accept merge** — the cards will be merged into one using your field choices.
- **✕ Keep separate** — the cards are left as distinct contacts.

Accepted and rejected clusters move to the "Resolved" list. Click **Undo** next to any resolved item to reconsider it.

#### Shortcuts

- **+ Manual merge** — always available; opens a picker where you can search by name, phone, or email and select two or more cards to merge manually. Useful for duplicates the automatic matcher did not catch. Select at least two cards, then click **Create merge**.
- **Accept all high-confidence** — appears only when there are clusters the matcher identified with high confidence. Accepts all of them in one click.

When all clusters are reviewed the right panel shows "All clusters reviewed". Click **Next** to commit your decisions and proceed.

---

### Step 3: Tidy

**What it does:** Fixes two common data problems — company cards filed under person names, and nameless cards that only have an email address.

The Tidy screen shows a list of contacts that are candidates for tidying. Use the **Filter** field to narrow the list.

The count at the top reads `N of M marked`. Each row has a checkbox and the contact name. When a card is marked, a hint shows what will happen:

- `→ org: Company Name` — the card's name will be moved to the `org` field (the contact is treated as a company/organisation, not a person).
- `→ name: user@example.com` — the card has no name, so the first email address becomes the display name.

Cards are **pre-checked** if they match high-precision heuristics: a name that strongly resembles a company, or a nameless card that has an email. Review the pre-selections and uncheck anything that does not look right.

Click a row to see the full card detail in the right panel.

Click **Next** when you are satisfied. Nothing is changed until you advance.

---

### Step 4: Deletion

**What it does:** Flags contacts matching your deletion rules, lets you review each one, and removes only those you approve.

The screen has three columns:

#### Left column: Rules

A list of rules, each with an enable/disable checkbox. The app starts with a set of built-in starter rules (all enabled by default):

- **no name and no phone**
- **empty cards**
- **name is an email address**
- **no-reply senders** (no-reply@\*, noreply@\*, donotreply@\*, do-not-reply@\*)
- **premium rate (1-900)** (phone pattern 900-???-????)
- **placeholder names** (names containing: test, unknown, no name, new contact, duplicate, do not use)
- **automated sender with no identity** (no-reply address AND no name/phone)

Three buttons sit below the rule list:

- **Load…** — loads a rule set from a `.json` file, replacing the current rules. Use this to load `rules/contact-cleanup.json` (included in the repo) for a broader set of rules covering toll-free business lines, role/department mailboxes, job-hunt/recruiter sites, and more.
- **Save…** — saves the current rule set (enabled and disabled) to a `.json` file. Use this to preserve your customised rules for future runs.
- **Run** — evaluates the enabled rules against your contacts.

#### Middle column: Flagged contacts

Appears after you click **Run**. Contacts are grouped by the rule that matched them. Each group shows its name and count, with an **Approve all** button for that group. A top-level **Approve all** button approves every flagged contact at once.

Each flagged contact shows its name and the reason it was flagged. Check the checkbox next to a contact to approve it for deletion, or uncheck it to keep it. The status line at the bottom reads: `N flagged · M approved → R remain`.

#### Right column: Card detail

Click any flagged contact button to see its full details (name, emails, phones, org, source, and the reason it was flagged).

**Nothing is deleted until you click Next.** Only contacts with their checkbox checked (approved) are removed; the rest pass through unchanged.

---

### Step 5: Export

**What it does:** Saves your cleaned contacts to a vCard file.

The Export screen shows how many cleaned contacts are ready. Click **Save cleaned vCard…** to open a save dialog. The default filename is `contacts-clean.vcf`.

After saving, the screen confirms: `✓ Exported N contacts to /path/to/contacts-clean.vcf`.

Below the save button, the screen shows instructions for importing your cleaned contacts back into Apple and Google — the same steps covered in the next section.

**Next is disabled on the Export step** — it is the end of the wizard.

---

## 4. Upload your cleaned contacts back

This step wipes your existing contacts and replaces them with the cleaned set. It is the only reliable way to mirror the same set into both Apple and Google without accumulating duplicates.

### Confirm your backup

Before touching anything, make sure you have the `apple.vcf` and `google.vcf` files you saved in section 1. Contactotomy never modifies your input files, but you are about to delete all of your contacts.

### Wipe each account

**Apple:**

1. Open the **Contacts** app.
2. Select all: **Cmd+A**.
3. Delete: **Delete** key, then confirm.

**Google:**

1. Go to [contacts.google.com](https://contacts.google.com).
2. Select all contacts.
3. Delete them.

### Import the cleaned file into both accounts

**Apple:**

1. Open the **Contacts** app.
2. Choose **File → Import…**
3. Select your `contacts-clean.vcf`.

**Google:**

1. Go to [contacts.google.com](https://contacts.google.com).
2. Click **Import** in the left sidebar.
3. Upload your `contacts-clean.vcf`.

Both accounts now hold the identical cleaned set.

### What vCard 3.0 preserves (and what it does not)

- **Names, phones, emails, org, title, notes** — preserved.
- **Google labels** — carried in the vCard `CATEGORIES` field; they survive the round-trip through Google.
- **Apple group membership** — **not preserved**. Apple's vCard export does not include group data.
- **Creation dates** — reset on re-import. Each contact's creation date will reflect the import date, not the original.

---

## 5. Keeping Apple and Google in sync going forward

Contactotomy is a **periodic offline cleaner, not a live two-way sync service**. There is no background process and no automatic synchronisation between your accounts. Here is how to think about ongoing use.

### The recurring workflow

Whenever your contact lists accumulate new clutter — after a job change, a phone migration, or just a few months of new additions — repeat the process from the top:

1. Export both Apple and Google to `.vcf` files.
2. Load them into Contactotomy.
3. Review merges, tidy cards, run your deletion rules.
4. Save `contacts-clean.vcf`.
5. Wipe both accounts and import the clean file.

How often you do this is up to you. Once or twice a year is reasonable for most people.

### Designate one account as your source of truth

Because you are pushing the same cleaned set into both Apple and Google, they start out identical after each run. But day-to-day additions diverge:

- Contacts you add on your iPhone go into iCloud.
- Contacts you add via Gmail go into Google.

The simplest approach: pick one account (Apple or Google) as your primary and add all new contacts there. When you run Contactotomy, start the export from that account. You will still export both for de-duplication and deletion, but one account drives the data.

### Caveats

- **No automatic sync.** Changes in one account do not flow to the other until you run the tool again.
- **Each run re-dedupes from scratch.** Merges from a previous run are baked into your `contacts-clean.vcf`, but new duplicates introduced since then will appear again and need another review pass.
- **New additions reintroduce duplicates.** If someone is in both your Apple and Google contacts at the time of the next export, they will show up as a merge candidate again.
- **Back up before every re-import.** Keep the current `apple.vcf` and `google.vcf` exports somewhere safe each time. If the import goes wrong you can recover from them.

### Reusing your rules

The **Save…** and **Load…** buttons on the Deletion screen let you save your current rule set as a `.json` file and reload it next time. Save your tuned rules after your first clean run; on subsequent runs just Load them back in and they will be ready to go. The `rules/contact-cleanup.json` file in the repo is a broader starter library you can use as a base.
