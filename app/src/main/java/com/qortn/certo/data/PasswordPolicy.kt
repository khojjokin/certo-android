package com.qortn.certo.data

/**
 * Certo's password rules (also enforced by Clerk): 8+ characters with upper and lower case,
 * a number and a symbol. The app never shows or sends the password; after it's set, only a
 * 0-4 strength score goes to the Certo API so the admin console can see weak accounts.
 */
object PasswordPolicy {
    const val REQUIREMENT = "Use 8+ characters with upper and lower case letters, a number and a symbol."

    data class Rule(val label: String, val met: Boolean)

    fun rules(p: String) = listOf(
        Rule("8+ characters", p.length >= 8),
        Rule("A–Z", p.any { it.isUpperCase() }),
        Rule("a–z", p.any { it.isLowerCase() }),
        Rule("0–9", p.any { it.isDigit() }),
        Rule("Symbol", p.any { !it.isLetterOrDigit() && !it.isWhitespace() }),
    )

    fun isValid(p: String) = rules(p).all { it.met }

    /** 0 very weak … 4 strong. */
    fun score(p: String): Int {
        if (p.length < 8) return 0
        val kinds = rules(p).drop(1).count { it.met }
        var s = when { kinds >= 4 -> 3; kinds == 3 -> 2; else -> 1 }
        if (p.length >= 12) s += 1
        val lower = p.lowercase()
        if (listOf("password", "qwerty", "123456", "certo", "letmein", "abc123", "iloveyou").any { lower.contains(it) }) s = minOf(s, 1)
        if (p.toSet().size <= 4) s = minOf(s, 1)
        return minOf(s, 4)
    }
}
