package com.capstone.agenticrag.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.capstone.agenticrag.common.Texts;

/**
 * Identifies memory candidates in a user's own message with transparent, deterministic rules. Only first-person
 * statements and explicit "remember ..." requests qualify; questions are ignored. Assistant output and tool results
 * (web pages, documents) are never used as a memory source, which blocks indirect prompt injection into memory.
 */
@Component
public class MemoryCandidateExtractor {

    /**
     * @param exclusivePrefix for single-valued attributes (e.g. "User's name is "), an existing memory starting with
     *                        the same prefix is a contradiction and is replaced by the newer statement; else null.
     */
    public record Candidate(String content, Memory.Category category, boolean explicit, float confidence,
                            String exclusivePrefix) {}

    private record Rule(Pattern pattern, Memory.Category category, String template, boolean exclusive) {}

    /** End of the captured value: end of text, a newline, or sentence punctuation followed by space (not "3.5"). */
    private static final String END = "(?=$|\\n|[.!;,](?:\\s|$)|\\s+(?:but|because|so that)\\s)";

    private static final Pattern EXPLICIT = Pattern.compile(
            "(?i)^(?:please\\s+)?(?:remember|don'?t forget|do not forget|keep in mind|note|make a note)(?:\\s+that)?[,:]?\\s+(.+)$");

    /** A leading greeting or filler ("Hello, my name is ...", "Ok so I prefer ...") does not change the statement. */
    private static final Pattern GREETING = Pattern.compile(
            "(?i)^(?:(?:hi|hello|hey|hiya|greetings|good (?:morning|afternoon|evening)|ok(?:ay)?|so|well|also|btw|by the way)"
                    + "(?:\\s+there)?[,!.:]?\\s+)+");

    private static final Pattern FROM_NOW_ON = Pattern.compile(
            "(?i)^(?:from now on|going forward|in (?:all )?future (?:answers|responses|chats)|always|never)\\b[,]?\\s*(.+)$");

    private static final List<Rule> RULES = List.of(
            rule("^my name is ([\\p{L}][\\p{L} '.-]{0,40}?)" + END, Memory.Category.FACT, "User's name is %s", true),
            rule("^(?:please )?call me ([\\p{L}][\\p{L} '.-]{0,40}?)" + END, Memory.Category.PREFERENCE, "User prefers to be called %s", true),
            rule("^i(?:'m| am) (?:currently )?(?:working on|building|developing|writing) (.{3,200}?)" + END, Memory.Category.PROJECT, "User is working on %s", false),
            rule("^my (?:current |capstone |main )?project is (.{3,200}?)" + END, Memory.Category.PROJECT, "User's project is %s", false),
            rule("^my (?:main |primary )?goal is (?:to )?(.{3,200}?)" + END, Memory.Category.GOAL, "User's goal is to %s", false),
            rule("^i(?:'m| am) (?:trying|planning|hoping|learning) (?:to )?(.{3,200}?)" + END, Memory.Category.GOAL, "User is trying to %s", false),
            rule("^i want to (?:learn|become|build|get better at) (.{3,200}?)" + END, Memory.Category.GOAL, "User wants to learn or achieve: %s", false),
            rule("^i (?:really )?prefer (.{2,200}?)" + END, Memory.Category.PREFERENCE, "User prefers %s", false),
            rule("^i (?:really )?(?:like|love|enjoy) (.{2,200}?)" + END, Memory.Category.PREFERENCE, "User likes %s", false),
            rule("^i (?:really )?(?:dislike|hate|don'?t like|do not like) (.{2,200}?)" + END, Memory.Category.PREFERENCE, "User dislikes %s", false),
            rule("^my favou?rite ([\\p{L} ]{2,30}) is (.{2,100}?)" + END, Memory.Category.PREFERENCE, null, true),
            rule("^i (?:work|am working) as (?:an? )?(.{2,100}?)" + END, Memory.Category.FACT, "User works as %s", true),
            rule("^i(?:'m| am) an? ((?:senior |junior |lead |final[- ]year |\\w+ )?(?:student|developer|engineer|designer|teacher|researcher|manager|scientist|analyst|architect)\\b.{0,80}?)" + END,
                    Memory.Category.FACT, "User is a %s", true),
            rule("^i (?:live|am based) in (.{2,80}?)" + END, Memory.Category.FACT, "User lives in %s", true),
            rule("^i (?:use|code in|program in|mostly use) (.{2,120}?)" + END, Memory.Category.PREFERENCE, "User uses %s", false));

    private static Rule rule(String regex, Memory.Category category, String template, boolean exclusive) {
        return new Rule(Pattern.compile("(?i)" + regex), category, template, exclusive);
    }

    public List<Candidate> extract(String userMessage) {
        Map<String, Candidate> out = new LinkedHashMap<>();
        if (userMessage == null || userMessage.length() > 4000) {
            return List.of();
        }
        for (String raw : splitSentences(userMessage)) {
            String sentence = GREETING.matcher(raw.strip()).replaceFirst("");
            if (sentence.length() < 4 || sentence.endsWith("?")) {
                continue;
            }
            Matcher explicit = EXPLICIT.matcher(sentence);
            if (explicit.matches()) {
                String body = stripTrailing(explicit.group(1));
                Candidate inner = matchRules(body, true);
                Candidate c = inner != null ? inner
                        : new Candidate(Texts.truncate(capitalize(toThirdPerson(body)), MemoryPolicy.MAX_LENGTH), categorize(body), true, 0.95f, null);
                out.putIfAbsent(Texts.normalizeForComparison(c.content()), c);
                continue;
            }
            Matcher instruction = FROM_NOW_ON.matcher(sentence);
            if (instruction.matches() && instruction.group(1).length() > 6) {
                String content = "Instruction for future answers: " + stripTrailing(sentence);
                out.putIfAbsent(Texts.normalizeForComparison(content),
                        new Candidate(Texts.truncate(content, MemoryPolicy.MAX_LENGTH), Memory.Category.INSTRUCTION, true, 0.9f, null));
                continue;
            }
            Candidate c = matchRules(sentence, false);
            if (c != null) {
                out.putIfAbsent(Texts.normalizeForComparison(c.content()), c);
            }
        }
        return new ArrayList<>(out.values());
    }

    private Candidate matchRules(String sentence, boolean explicit) {
        for (Rule r : RULES) {
            Matcher m = r.pattern().matcher(sentence);
            if (m.find()) {
                String content;
                if (r.template() == null) { // "my favourite X is Y"
                    String prefix = "User's favourite " + m.group(1).strip() + " is ";
                    return new Candidate(prefix + stripTrailing(m.group(2)), r.category(), explicit,
                            explicit ? 0.95f : 0.75f, prefix);
                }
                content = r.template().formatted(toThirdPerson(stripTrailing(m.group(1))));
                String prefix = r.exclusive() ? r.template().substring(0, r.template().indexOf("%s")) : null;
                return new Candidate(Texts.truncate(content, MemoryPolicy.MAX_LENGTH), r.category(), explicit,
                        explicit ? 0.95f : 0.7f, prefix);
            }
        }
        return null;
    }

    static Memory.Category categorize(String text) {
        String t = text.toLowerCase();
        if (t.matches(".*\\b(prefer|like|love|hate|dislike|favou?rite|call me)\\b.*")) return Memory.Category.PREFERENCE;
        if (t.matches(".*\\b(project|working on|building|app|thesis|capstone)\\b.*")) return Memory.Category.PROJECT;
        if (t.matches(".*\\b(goal|want to|plan to|trying to|deadline)\\b.*")) return Memory.Category.GOAL;
        if (t.matches(".*\\b(always|never|from now on|should|must|format|respond|answer)\\b.*")) return Memory.Category.INSTRUCTION;
        return Memory.Category.FACT;
    }

    /** Rewrites first-person phrasing so stored memories read unambiguously ("I use" becomes "user uses"). */
    static String toThirdPerson(String text) {
        String t = " " + text.strip() + " ";
        t = t.replaceAll("(?i) i am ", " the user is ")
                .replaceAll("(?i) i'm ", " the user is ")
                .replaceAll("(?i) i have ", " the user has ")
                .replaceAll("(?i) i've ", " the user has ")
                .replaceAll("(?i) i ", " the user ")
                .replaceAll("(?i) my ", " the user's ")
                .replaceAll("(?i) me ", " the user ")
                .replaceAll("(?i) mine ", " the user's ");
        return t.strip();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String stripTrailing(String s) {
        return s.strip().replaceAll("[\\s.!;,]+$", "");
    }

    private static List<String> splitSentences(String text) {
        return List.of(text.split("(?<=[.!?])\\s+|\\n+"));
    }
}
