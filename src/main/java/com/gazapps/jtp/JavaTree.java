package com.gazapps.jtp;
import java.io.File;
import java.io.FileInputStream;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

public class JavaTree {

    public void printTree(File file, String indent) {
        if (!file.exists()) return;

        // Exibe o nome do arquivo ou diretório
        System.out.println(indent + getTreePrefix(file) + file.getName());

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    printTree(child, indent + "│   ");
                }
            }
        } else if (file.getName().endsWith(".java")) {
            // Analisa arquivos .java
            analyzeJavaFile(file, indent + "│   ");
        }
    }

    private String getTreePrefix(File file) {
        return file.isDirectory() ? "├── " : "├── ";
    }

    private void analyzeJavaFile(File javaFile, String indent) {
        try {

        	 // Configura o parser para suportar Java 15
            ParserConfiguration config = new ParserConfiguration();
            config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_15);
            StaticJavaParser.setConfiguration(config);

            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(javaFile));

            // Encontra todas as classes ou interfaces
            for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                System.out.println(indent + "└── class " + clazz.getNameAsString());

                // Lista os campos (atributos)
                for (FieldDeclaration field : clazz.getFields()) {
                    String modifiers = field.getModifiers().toString().trim();
                    String type = field.getVariables().get(0).getType().toString();
                    String name = field.getVariables().get(0).getNameAsString();
                    System.out.println(indent + "    ├── field: " + modifiers + " " + type + " " + name);
                }

                // Lista os métodos
                for (MethodDeclaration method : clazz.getMethods()) {
                    String modifiers = method.getModifiers().toString().trim();
                    String returnType = method.getType().toString();
                    String name = method.getSignature().toString();
                    System.out.println(indent + "    └── method: " + modifiers + " " + returnType + " " + name);
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao analisar " + javaFile.getName() + ": " + e.getMessage());
        }
    }
}