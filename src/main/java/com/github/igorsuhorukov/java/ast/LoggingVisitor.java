package com.github.igorsuhorukov.java.ast;

import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class LoggingVisitor extends ASTVisitor {

    final static Set<String> LOGGER_CLASS = new HashSet<String>() {{
        add("org.slf4j.Logger");
        add("org.apache.commons.logging.Log");
        add("org.springframework.boot.cli.util.Log");
    }};

    final static Set<String> LOGGER_METHOD = new HashSet<String>() {{
        add("fatal");
        add("error");
        add("warn");
        add("info");
        add("debug");
        add("trace");
    }};

    public static final String LITERAL = "Literal";
    public static final String FORMAT_METHOD = "format";

    private final CompilationUnit cu;
    private final String currentClassName;

    public LoggingVisitor(CompilationUnit cu, String currentClassName) {
        this.cu = cu;
        this.currentClassName = currentClassName;
    }

    @Override
    public boolean visit(MethodInvocation node) {
        if (LOGGER_METHOD.contains(node.getName().getIdentifier())) {
            ITypeBinding objType = node.getExpression() != null ? node.getExpression().resolveTypeBinding() : null;
            if (objType != null && LOGGER_CLASS.contains(objType.getBinaryName())) {

                int lineNumber = cu.getLineNumber(node.getStartPosition());

                boolean isFormat = false;
                boolean isConcat = false;
                boolean isLiteral1 = false;
                boolean isLiteral2 = false;
                boolean isMethod = false;
                boolean withException = false;

                for (int i = 0; i < node.arguments().size(); i++) {
                    ASTNode innerNode = (ASTNode) node.arguments().get(i);
                    if (i == node.arguments().size() - 1) {
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
                            isFormat = true;
                        } else {
                            isMethod = true;
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
                String type = loggerInvocationType(node, isFormat, isConcat, isLiteral1 || isLiteral2, isMethod);
                System.out.println(currentClassName + ":" + lineNumber + "\t\t\t" + node+"\t\ttype "+type); //node.getStartPosition()

            }
        }
        return true;
    }

    private String loggerInvocationType(MethodInvocation node, boolean isFormat, boolean isConcat, boolean isLiteral, boolean isMethod) {
        if (!isConcat && !isFormat && isLiteral) {
            return "literal";
        } else {
            if (isFormat && isConcat) {
                return "format concat";
            } else if (isFormat && !isLiteral) {
                return "format";
            } else if (isConcat && !isLiteral) {
                return "concat";
            } else {
                if (isConcat || isFormat || isLiteral) {
                    if (node.arguments().size() == 1) {
                        return "single argument";
                    } else {
                        return  "mixed logging";
                    }
                }
            }
            if(isMethod){
                return "method";
            }
        }
        return "unknown";
    }
}
