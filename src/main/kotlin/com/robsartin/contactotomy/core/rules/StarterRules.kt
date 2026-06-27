package com.robsartin.contactotomy.core.rules

/** The default seed rules, derived from the user's examples. Keep, edit, or delete. */
fun RuleSet.Companion.starter(): RuleSet =
    RuleSet(
        listOf(
            Rule("no name and no phone", Predicate(PredicateKind.NO_NAME_AND_NO_PHONE)),
            Rule("empty cards", Predicate(PredicateKind.EMPTY_CARD)),
            Rule("name is an email address", TextMatch(TextField.NAME, "*@*")),
            Rule(
                "no-reply senders",
                Or(
                    listOf(
                        TextMatch(TextField.EMAIL, "no-reply@*"),
                        TextMatch(TextField.EMAIL, "noreply@*"),
                        TextMatch(TextField.EMAIL, "donotreply@*"),
                        TextMatch(TextField.EMAIL, "do-not-reply@*"),
                    ),
                ),
            ),
            Rule("premium rate (1-900)", PhoneMatch("900-???-????")),
            Rule(
                "placeholder names",
                Or(
                    listOf(
                        TextMatch(TextField.NAME, "*test*"),
                        TextMatch(TextField.NAME, "*unknown*"),
                        TextMatch(TextField.NAME, "*no name*"),
                        TextMatch(TextField.NAME, "*new contact*"),
                        TextMatch(TextField.NAME, "*duplicate*"),
                        TextMatch(TextField.NAME, "*do not use*"),
                    ),
                ),
            ),
            Rule(
                "automated sender with no identity",
                And(
                    listOf(
                        Or(
                            listOf(
                                TextMatch(TextField.EMAIL, "no-reply@*"),
                                TextMatch(TextField.EMAIL, "noreply@*"),
                                TextMatch(TextField.EMAIL, "donotreply@*"),
                                TextMatch(TextField.EMAIL, "do-not-reply@*"),
                            ),
                        ),
                        Predicate(PredicateKind.NO_NAME_AND_NO_PHONE),
                    ),
                ),
            ),
        ),
    )
