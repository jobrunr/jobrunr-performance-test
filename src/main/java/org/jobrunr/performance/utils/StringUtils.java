package org.jobrunr.performance.utils;

import org.jobrunr.performance.scenario.Scenario;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {

    public static String camelCaseToHumanReadable(Scenario scenario) {
        // Pattern to capture a prefix like "Scenario01" and the remainder
        String input = scenario.getClass().getSimpleName();
        Pattern pattern = Pattern.compile("^(Scenario\\d+)(.*)$");
        Matcher matcher = pattern.matcher(input);
        if (matcher.matches()) {
            String prefix = matcher.group(1);
            String remainder = matcher.group(2);

            String spaced = remainder.replaceAll("(?<!^)(?=[A-Z])", " ").trim();

            if (!spaced.isEmpty()) {
                spaced = spaced.substring(0, 1).toUpperCase() + spaced.substring(1).toLowerCase();
            }

            return prefix + " - " + spaced;
        } else {
            // Fallback: simply insert spaces for camel case in the entire string.
            String spaced = input.replaceAll("(?<!^)(?=[A-Z])", " ").trim();
            return spaced;
        }
    }

    public static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public static boolean isNotNullOrEmpty(String s) {
        return !isNullOrEmpty(s);
    }

    public static String substringBefore(String s, String splitter) {
        int endIndex = s.indexOf(splitter);
        if (endIndex >= 0) {
            return s.substring(0, endIndex);
        }
        return s;
    }

    public static String substringAfter(String s, String splitter) {
        final int indexOf = s.indexOf(splitter);
        return indexOf >= 0 ? s.substring(indexOf + splitter.length()) : null;
    }

    public static String substringBeforeLast(String s, String splitter) {
        return s.substring(0, s.lastIndexOf(splitter));
    }

    public static String substringAfterLast(String s, String splitter) {
        return s.substring(s.lastIndexOf(splitter) + 1);
    }

    public static String substringBetween(String s, String open, String close) {
        if (s.contains(open) && s.contains(close)) {
            return substringBefore(substringAfter(s, open), close);
        }
        return null;
    }
}
