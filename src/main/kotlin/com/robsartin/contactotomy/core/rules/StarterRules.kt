package com.robsartin.contactotomy.core.rules

/** The default seed rules, derived from the user's examples. Keep, edit, or delete. */
fun RuleSet.Companion.starter(): RuleSet =
    RuleSet(
        listOf(
            Rule("old job (indeed)", TextMatch(TextField.EMAIL, "*@indeed.com")),
            Rule("my own addresses", TextMatch(TextField.EMAIL, "sartin@*")),
            Rule("austin area code", PhoneMatch("512-???-????")),
            Rule("no name and no phone", Predicate(PredicateKind.NO_NAME_AND_NO_PHONE)),
        ),
    )
