package com.github.igorsuhorukov.java.ast;

import com.github.igorsuhorukov.eclipse.aether.artifact.DefaultArtifact;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.io.CharStreams;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 */
public class ParserUtils {

    public static final int MAX_CACHE_SIZE = 1000;

    public static Set<String> getClasses(String file) throws IOException {
        return Collections.list(new JarFile(file).entries()).stream()
                .filter(jar -> jar.getName().endsWith("class") && !jar.getName().contains("$"))
                .map(new Function<JarEntry, String>() {
                    @Override
                    public String apply(JarEntry jarEntry) {
                        return jarEntry.getName().replace(".class", "").replace('/', '.');
                    }
                }).collect(Collectors.toSet());
    }

    public static String getMavenSourcesId(String className) {
        String mavenCoordinates = io.fabric8.insight.log.log4j.MavenCoordHelper.getMavenCoordinates(className);
        if(mavenCoordinates==null) return null;
        DefaultArtifact artifact = new DefaultArtifact(mavenCoordinates);
        return String.format("%s:%s:%s:sources:%s", artifact.getGroupId(), artifact.getArtifactId(),
                                                    artifact.getExtension(), artifact.getVersion());
    }

    public static LoadingCache<String, URLClassLoader> createMavenClassloaderCache() {
        return CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .build(new CacheLoader<String, URLClassLoader>() {
                    @Override
                    public URLClassLoader load(String mavenId) throws Exception {
                        return com.github.igorsuhorukov.smreed.dropship.MavenClassLoader.forMavenCoordinates(mavenId);
                    }
                });
    }


    public static String[] prepareClasspath(URLClassLoader urlClassLoader) {
        return Arrays.stream(urlClassLoader.getURLs()).map(new Function<URL, String>() {
            @Override
            public String apply(URL url) {
                return url.getFile();
            }
        }).toArray(String[]::new);
    }

    public static String getJarFileByClass(Class<?> clazz) {
        CodeSource source = clazz.getProtectionDomain().getCodeSource();
        String file = null;
        if (source != null) {
            URL locationURL = source.getLocation();
            if ("file".equals(locationURL.getProtocol())) {
                file = locationURL.getPath();
            } else {
                file = locationURL.toString();
            }
        }
        return file;
    }

    static String getClassSourceCode(String className, URLClassLoader urlClassLoader) throws IOException {
        String sourceText = null;
        try (InputStream javaSource = urlClassLoader.getResourceAsStream(className.replace(".", "/") + ".java")) {
            if (javaSource != null){
                try (InputStreamReader sourceReader = new InputStreamReader(javaSource)){
                    sourceText = CharStreams.toString(sourceReader);
                }
            }
        }
        return sourceText;
    }
}
