package org.performance.utils;

import java.io.InputStream;
import java.net.URI;
import java.util.jar.Manifest;

import static java.util.Optional.ofNullable;

public class JarUtils {

    public static String getJavaVersion() {
        return Runtime.version().toString();
    }

    public static String getToolName(Object object) {
        return ofNullable(getManifestAttributeValue(object, "Implementation-Title")).orElseThrow();
    }

    public static String getToolVersion(Object object) {
        String version = getVersion(object);
        String branch = ofNullable(getManifestAttributeValue(object, "Git-Branch")).orElse("branch unknown");
        String commitId = ofNullable(getManifestAttributeValue(object, "Git-Commit")).map(x -> "@" + x).orElse("");
        if (StringUtils.isNullOrEmpty(branch)) return version;
        return version + " (" + branch + commitId + ")";
    }

    public static String getVersion(Object object) {
        Class<?> clazz = object instanceof Class ? (Class<?>) object : object.getClass();
        String version = clazz.getPackage().getImplementationVersion();
        if (version != null) {
            return version;
        }

        return getManifestAttributeValue(clazz, "Bundle-Version");
    }

    public static String getManifestAttributeValue(Object object, String attributeName) {
        Class<?> clazz = object instanceof Class ? (Class<?>) object : object.getClass();
        return getManifestAttributeValue(clazz, attributeName);
    }

    public static String getManifestAttributeValue(Class<?> clazz, String attributeName) {
        return getManifest(clazz).getMainAttributes().getValue(attributeName);
    }

    private static Manifest getManifest(Class<?> clazz) {
        String resource = "/" + clazz.getName().replace(".", "/") + ".class";
        String fullPath = clazz.getResource(resource).toString();
        String archivePath = fullPath.substring(0, fullPath.length() - resource.length());
        if (archivePath.endsWith("\\WEB-INF\\classes") || archivePath.endsWith("/WEB-INF/classes")) {
            archivePath = archivePath.substring(0, archivePath.length() - "/WEB-INF/classes".length()); // Required for wars
        }

        try (InputStream input = new URI(archivePath + "/META-INF/MANIFEST.MF").toURL().openStream()) {
            return new Manifest(input);
        } catch (Exception e) {
            throw new RuntimeException("Loading MANIFEST for class " + clazz + " failed!", e);
        }
    }
}
