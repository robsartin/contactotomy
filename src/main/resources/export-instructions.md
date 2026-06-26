# Export, clean, and import your contacts

## 1. Export from each account
- **Apple (iCloud / Mac):** open Contacts, select all (Cmd+A), then
  File → Export → Export vCard…, and save it as `apple.vcf`.
- **Google:** go to contacts.google.com → Export → vCard (for iOS Contacts),
  and save it as `google.vcf`.

## 2. Back up first
Keep copies of `apple.vcf` and `google.vcf` somewhere safe before changing
anything. Contactotomy never modifies your input files.

## 3. Clean
Load both files into Contactotomy, review the proposed merges, run your deletion
rules, review the flagged cards, and save `contacts-clean.vcf`.

## 4. Wipe each account (required to mirror into both without duplicates)
After confirming your backup, delete all existing contacts in EACH account:
- **Apple:** in Contacts, select all and delete.
- **Google:** at contacts.google.com, select all and delete.

## 5. Import the clean file into both
Import `contacts-clean.vcf` into BOTH Apple (Contacts → File → Import) and Google
(Import → upload). Both accounts now hold the identical clean set.

## Tradeoffs to know
- Re-importing resets each contact's creation date.
- Apple group membership is lost — Apple's vCard export does not include it.
- Google labels survive — they are carried in the vCard `CATEGORIES` field.

## Re-running
Repeat from step 1 whenever cruft accumulates; the wipe-before-import step keeps
each pass clean.
