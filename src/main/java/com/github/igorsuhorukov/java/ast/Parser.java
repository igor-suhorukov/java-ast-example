package com.github.igorsuhorukov.java.ast;

import com.google.common.cache.LoadingCache;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.net.URLClassLoader;
import java.util.Set;

import static com.github.igorsuhorukov.java.ast.ParserUtils.*;

public class Parser {

    public static final String[] SOURCE_PATH = new String[]{System.getProperty("java.io.tmpdir")};
    public static final String[] SOURCE_ENCODING = new String[]{"UTF-8"};

    public static void main(String[] args) throws Exception {

        if(args.length!=1) throw new IllegalArgumentException("Class name should be specified");
        String file = getJarFileByClass(Class.forName(args[0]));
        Set<String> classes = getClasses(file);
        LoadingCache<String, URLClassLoader> classLoaderCache = createMavenClassloaderCache();

        for (final String currentClassName : classes) {

            String mavenSourcesId = getMavenSourcesId(currentClassName);
            if (mavenSourcesId == null)
                throw new IllegalArgumentException("Maven group:artifact:version not found for class " + currentClassName);

            URLClassLoader urlClassLoader = classLoaderCache.get(mavenSourcesId);

            ASTParser parser = ASTParser.newParser(AST.JLS8);
            parser.setResolveBindings(true);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setCompilerOptions(JavaCore.getOptions());

            parser.setEnvironment(prepareClasspath(urlClassLoader), SOURCE_PATH, SOURCE_ENCODING, true);

            parser.setUnitName(currentClassName + ".java");

            String sourceText = getClassSourceCode(currentClassName, urlClassLoader);
            if(sourceText == null) continue;

            parser.setSource(sourceText.toCharArray());

            CompilationUnit cu = (CompilationUnit) parser.createAST(null);

            cu.accept(new LoggingVisitor(cu, currentClassName));
        }
    }
}

