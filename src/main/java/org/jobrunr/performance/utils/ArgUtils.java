package org.jobrunr.performance.utils;

import java.util.stream.Stream;

public class ArgUtils {

    public static String getArg(String[] args, String key) {
        return getArg(args, key, null);
    }

    public static String getArg(String[] args, String key, String defaultValue) {
        return Stream.of(args)
                .filter(x -> x.startsWith(key))
                .map(x -> x.replace(key + "=", ""))
                .findFirst()
                .orElse(defaultValue);
    }
}
