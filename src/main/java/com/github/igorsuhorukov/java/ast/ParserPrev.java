package com.github.igorsuhorukov.java.ast;

/*
import com.github.igorsuhorukov.eclipse.aether.artifact.DefaultArtifact;
import com.github.igorsuhorukov.smreed.dropship.MavenClassLoader;
import io.fabric8.insight.log.log4j.MavenCoordHelper;
import org.apache.commons.io.IOUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
*/

/**
 * http://help.eclipse.org/mars/index.jsp?topic=%2Forg.eclipse.jdt.doc.isv%2Fguide%2Fjdt_api_manip.htm
 */
public class ParserPrev {
/*

    final static Set<String> LOGGER_CLASS = new HashSet<String>();
    static {
        LOGGER_CLASS.add("org.slf4j.Logger");
        LOGGER_CLASS.add("org.apache.commons.logging.Log");
        LOGGER_CLASS.add("org.springframework.boot.cli.util.Log");

    }
    final static Set<String> LOGGER_METHOD = new HashSet<String>();
    static {
        LOGGER_METHOD.add("fatal");
        LOGGER_METHOD.add("error");
        LOGGER_METHOD.add("warn");
        LOGGER_METHOD.add("info");
        LOGGER_METHOD.add("debug");
        LOGGER_METHOD.add("trace");
    }

    public static final String FORMAT_METHOD = "format";
    public static final String LITERAL = "Literal";

    public static void main(String[] args) throws Exception {

        String file = getJarFileByClass(Class.forName("net.sf.log4jdbc.ConnectionSpy"));

        Set<String> classes = getClasses(file);

        Map<String, URLClassLoader> classLoaderCache = new HashMap<>();

        for(final String currentClassName: classes){

            String unitName = currentClassName + ".java";

            String mavenSourcesId = getMavenSourcesId(currentClassName);
            if(mavenSourcesId==null) throw new IllegalArgumentException("Maven group:artifact:version not found for class " + currentClassName);

            URLClassLoader urlClassLoader = null;
            if(!classLoaderCache.containsKey(mavenSourcesId)){
                urlClassLoader = MavenClassLoader.forMavenCoordinates(mavenSourcesId);
                classLoaderCache.put(mavenSourcesId, urlClassLoader);
            } else {
                urlClassLoader = classLoaderCache.get(mavenSourcesId);
            }

            String[] classpath = prepareClasspath(urlClassLoader);


            ASTParser parser = ASTParser.newParser(AST.JLS8);
            parser.setResolveBindings(true);
            //parser.setBindingsRecovery(true);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);

            Map options = JavaCore.getOptions();
            parser.setCompilerOptions(options);
            String[] sources = {System.getProperty("java.io.tmpdir")};

            parser.setEnvironment(classpath, sources, new String[]{"UTF-8"}, true);

            parser.setUnitName(unitName);
            String sourceString;
            try (InputStream javaSource = urlClassLoader.getResourceAsStream(currentClassName.replace(".", "/") + ".java")){
                if(javaSource==null) continue;
                sourceString = IOUtils.toString(javaSource);
                parser.setSource(sourceString.toCharArray());
            }

            final CompilationUnit cu = (CompilationUnit) parser.createAST(null);

            if (cu.getAST().hasBindingsRecovery()) {
                System.out.println("Binding activated.");
            }

            System.out.println(currentClassName);
            cu.accept(new ASTVisitor() {

                          @Override
                          public boolean visit(MethodInvocation node) {
                              if (LOGGER_METHOD.contains(node.getName().getIdentifier())) {
                                  ITypeBinding objType = node.getExpression() != null ? node.getExpression().resolveTypeBinding() : null;
                                  if (objType != null && LOGGER_CLASS.contains(objType.getBinaryName())) {

                                      int lineNumber = cu.getLineNumber(node.getStartPosition());
                                      System.out.println(node.getStartPosition());
                                      System.out.println(currentClassName + ":" + lineNumber + "\t\t\t" + node);


                                      boolean isFormat = false;
                                      boolean isConcat = false;
                                      boolean isLiteral1 = false;
                                      boolean isLiteral2 = false;
                                      boolean withException = false;
                                      List arguments1 = node.arguments();
                                      for (int i = 0; i < arguments1.size(); i++) {
                                          ASTNode innerNode = (ASTNode) arguments1.get(i);
                                          if (i == arguments1.size() - 1) {
                                              if (innerNode instanceof SimpleName && ((SimpleName) innerNode).resolveTypeBinding() != null) {
                                                  ITypeBinding typeBinding = ((SimpleName) innerNode).resolveTypeBinding();
                                                  while (typeBinding != null && Object.class.getName().equals(typeBinding.getBinaryName())) {
                                                      if (Throwable.class.getName().equals(typeBinding.getBinaryName())) {
                                                          withException = true;
                                                          break;
                                                      }
                                                      typeBinding = typeBinding.getSuperclass();
                                                  }
                                                  if (withException) continue;
                                              }
                                          }
                                          if (innerNode instanceof MethodInvocation) {
                                              MethodInvocation methodInvocation = (MethodInvocation) innerNode;
                                              if (FORMAT_METHOD.equals(methodInvocation.getName().getIdentifier()) && methodInvocation.getExpression() != null
                                                      && methodInvocation.getExpression().resolveTypeBinding() != null
                                                      && String.class.getName().equals(methodInvocation.getExpression().resolveTypeBinding().getBinaryName())) {
                                                  List<Expression> arguments = methodInvocation.arguments();
                                                  isFormat = true;
                                              }
                                          } else if (innerNode instanceof InfixExpression) {
                                              InfixExpression infixExpression = (InfixExpression) innerNode;
                                              if (InfixExpression.Operator.PLUS.equals(infixExpression.getOperator())) {
                                                  List expressions = new ArrayList();
                                                  expressions.add(infixExpression.getLeftOperand());
                                                  expressions.add(infixExpression.getRightOperand());
                                                  expressions.addAll(infixExpression.extendedOperands());
                                                  long stringLiteralCount = expressions.stream().filter(item -> item instanceof StringLiteral).count();
                                                  long notLiteralCount = expressions.stream().filter(item -> item.getClass().getName().contains(LITERAL)).count();
                                                  if (notLiteralCount > 0 && stringLiteralCount > 0) {
                                                      isConcat = true;
                                                  }
                                              }
                                          } else if (innerNode instanceof Expression && innerNode.getClass().getName().contains(LITERAL)) {
                                              isLiteral1 = true;
                                          } else if (innerNode instanceof SimpleName || innerNode instanceof QualifiedName
                                                  || innerNode instanceof ConditionalExpression || innerNode instanceof ThisExpression
                                                  || innerNode instanceof ParenthesizedExpression
                                                  || innerNode instanceof PrefixExpression || innerNode instanceof PostfixExpression
                                                  || innerNode instanceof ArrayCreation || innerNode instanceof ArrayAccess
                                                  || innerNode instanceof FieldAccess || innerNode instanceof ClassInstanceCreation) {
                                              isLiteral2 = true;
                                          }
                                      }
                                      report(node, isFormat, isConcat, isLiteral1, isLiteral2);
                                  }
                              }
                              return true;
                          }

                          private void report(MethodInvocation node, boolean isFormat, boolean isConcat, boolean isLiteral1, boolean isLiteral2) {
                              boolean isLiteral = isLiteral1 || isLiteral2;
                              if(!isConcat && !isFormat && isLiteral){
                                  //literal
                              } else {
                                  if(isFormat && isConcat){
                                      //format concat
                                  } else if(isFormat && !isLiteral){
                                      //format
                                  } else if(isConcat && !isLiteral){
                                      //concat
                                  } else {
                                      if(isConcat || isFormat || isLiteral){
                                          if(node.arguments().size() == 1){
                                              //single argument
                                          } else {
                                              //mixed
                                          }
                                      }
                                  }
                              }
                          }
                      }
            );
        }
    }

    private static String getJarFileByClass(Class<?> queryClass) {
        CodeSource source = queryClass.getProtectionDomain().getCodeSource();
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

    private static Set<String> getClasses(String file) throws IOException {
        JarFile jarFile = new JarFile(file);
        Set<String> classes = new HashSet<>();
        Enumeration entries = jarFile.entries();
        while(entries.hasMoreElements()) {
            JarEntry jarEntry = (JarEntry)entries.nextElement();
            String name = jarEntry.getName();
            if(name.endsWith("class") && !name.contains("$")) {
                classes.add(name.replace(".class", "").replace('/', '.'));
            }
        }
        return classes;
    }

    private static String getMavenSourcesId(final String className) {
        String mavenCoordinates = MavenCoordHelper.getMavenCoordinates(className);
        if(mavenCoordinates==null) return null;
        String[] parts = mavenCoordinates.split(":");
        if(parts.length==2){
            String artifactId = parts[0].replace("-" + parts[1]+".jar","");
            final AtomicReference<Class> aClass = new AtomicReference<>();
            new MavenCoordHelper(){
                {
                    try {
                        aClass.set(findClass(className));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException();
                    }
                }
            };
            String file = aClass.get().getProtectionDomain().getCodeSource().getLocation().getFile().replace("/home/igor/.m2/repository/","");
            String groupId = file.substring(0, file.indexOf("/" + artifactId)).replace("/",".");

            mavenCoordinates=groupId+":"+artifactId+":"+parts[1];
        }
        DefaultArtifact artifact = new DefaultArtifact(mavenCoordinates);
        return String.format("%s:%s:%s:sources:%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getVersion());
    }

    private static String[] prepareClasspath(URLClassLoader urlClassLoader) {
        URL[] urLs = urlClassLoader.getURLs();
        String[] classpath = new String[urLs.length];
        for (int i = 0; i < urLs.length; i++) {
            URL url = urLs[i];
            classpath[i] = url.getFile();
        }
        return classpath;
    }
*/
}

