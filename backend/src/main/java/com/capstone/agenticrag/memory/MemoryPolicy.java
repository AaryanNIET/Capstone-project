package com.capstone.agenticrag.memory;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Rules deciding what may be stored as long-term memory.
 * <ul>
 *   <li>Secrets (passwords, API keys, tokens, private keys, card and national-id numbers) are never stored.</li>
 *   <li>Special-category personal data (health, religion, sexuality, politics, ethnicity, criminal history, exact
 *       financial accounts) is stored only when the user explicitly asked to remember it.</li>
 * </ul>
 */
@Component
public class MemoryPolicy {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 1000;

    private static final List<Pattern> SECRETS = List.of(
            Pattern.compile("(?i)\\b(pass(word|wd|phrase|code)?|pwd|pin( code)?)\\b\\s*(is|=|:)"),
            Pattern.compile("(?i)\\b(api[ _-]?key|secret[ _-]?key|access[ _-]?key|client[ _-]?secret|auth(entication)?[ _-]?token|bearer|private[ _-]?key|ssh[ _-]?key|otp|2fa|recovery code)s?\\b"),
            Pattern.compile("\\bsk-[A-Za-z0-9_-]{16,}"),
            Pattern.compile("\\b(AKIA|ASIA)[0-9A-Z]{16}\\b"),
            Pattern.compile("\\b(ghp|gho|ghu|ghs|github_pat|xox[abprs]|glpat)[-_][A-Za-z0-9_-]{10,}"),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"),
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
            Pattern.compile("(?i)\\b(cvv|cvc|security code)\\b"),
            Pattern.compile("\\b[A-Za-z0-9+/]{40,}={0,2}"));

    private static final Pattern CARD_CANDIDATE = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");

    private static final Pattern SENSITIVE = Pattern.compile("(?i)\\b(diagnos(is|ed)|disease|illness|disorder|"
            + "medication|prescription|therapy|therapist|hiv|cancer|pregnan(t|cy)|depress(ion|ed)|anxiety|"
            + "religio(n|us)|muslim|christian|hindu|jewish|buddhist|atheist|"
            + "gay|lesbian|bisexual|transgender|sexual orientation|"
            + "political party|vote[ds]? for|democrat|republican|"
            + "ethnicity|race is|criminal record|convicted|arrested|"
            + "bank account|account number|routing number|iban|salary is|income is)\\b");

    public enum Decision { ALLOW, REJECT_SECRET, REJECT_SENSITIVE, REJECT_INVALID }

    public Decision evaluate(String content, boolean explicitRequest) {
        if (content == null) {
            return Decision.REJECT_INVALID;
        }
        String text = content.strip();
        if (text.length() < MIN_LENGTH || text.length() > MAX_LENGTH) {
            return Decision.REJECT_INVALID;
        }
        if (containsSecret(text)) {
            return Decision.REJECT_SECRET;
        }
        if (!explicitRequest && SENSITIVE.matcher(text).find()) {
            return Decision.REJECT_SENSITIVE;
        }
        return Decision.ALLOW;
    }

    public boolean containsSecret(String text) {
        for (Pattern p : SECRETS) {
            if (p.matcher(text).find()) {
                return true;
            }
        }
        Matcher m = CARD_CANDIDATE.matcher(text);
        while (m.find()) {
            if (luhn(m.group().replaceAll("\\D", ""))) {
                return true;
            }
        }
        return false;
    }

    public static Optional<String> describe(Decision decision) {
        return switch (decision) {
            case ALLOW -> Optional.empty();
            case REJECT_SECRET -> Optional.of("Memories cannot contain passwords, keys, tokens or similar secrets.");
            case REJECT_SENSITIVE -> Optional.of("Sensitive personal information is only stored when you explicitly ask me to remember it.");
            case REJECT_INVALID -> Optional.of("Memories must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters.");
        };
    }

    private static boolean luhn(String digits) {
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }
}
