/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.notNullVerification;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.*;
import se.eris.lang.LangUtils;
import se.eris.notnull.Configuration;
import se.eris.notnull.ImplicitNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author ven
 * @author Vladislav.Rassokhin
 * @author Olle Sundblad
 *         noinspection HardCodedStringLiteral
 */
public class NotNullInstrumenterClassVisitor extends ClassVisitor {

    private final Set<String> notnull;
    private final Set<String> nullable;
    private final Collection<ThrowOnNullMethodVisitor> methodVisitors = new ArrayList<>();

    private String className;
    private boolean isAnonymous = false;
    private boolean classAnnotatedImplicit = false;
    @NotNull
    private final Configuration configuration;

    public NotNullInstrumenterClassVisitor(@NotNull final ClassVisitor classVisitor, @NotNull final Configuration configuration) {
        super(Opcodes.ASM5, classVisitor);
        this.configuration = configuration;
        this.notnull = convertToClassName(configuration.getNotNullAnnotations());
        this.nullable = convertToClassName(configuration.getNullableAnnotations());
    }

    @NotNull
    private Set<String> convertToClassName(@NotNull final Iterable<String> annotations) {
        final Set<String> converted = new HashSet<>();
        for (@NotNull final String annotation : annotations) {
            converted.add(LangUtils.convertToJavaClassName(annotation));
        }
        return converted;
    }

    public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        className = name;
    }

    @Override
    public void visitInnerClass(final String name, final String outer, final String innerName, final int access) {
        if (name.equals(className)) {
            isAnonymous = innerName == null;
        }
    }

    @NotNull
    public MethodVisitor visitMethod(final int access, @NotNull final String name, final String desc, final String signature, final String[] exceptions) {
        final Type[] argumentTypes = Type.getArgumentTypes(desc);
        final Type returnType = Type.getReturnType(desc);
        final MethodVisitor methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);
        final ThrowOnNullMethodVisitor visitor;
        if (classAnnotatedImplicit || configuration.isImplicitInstrumentation(toClassName(className))) {
            visitor = new ImplicitThrowOnNullMethodVisitor(methodVisitor, argumentTypes, returnType, access, name, className, nullable, isAnonymous);
        } else {
            visitor = new AnnotationThrowOnNullMethodVisitor(methodVisitor, argumentTypes, returnType, access, name, className, notnull);
        }
        methodVisitors.add(visitor);
        return visitor;
    }

    @NotNull
    private String toClassName(final String className) {
        return className.replace('/', '.');
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
        if (LangUtils.convertToJavaClassName(ImplicitNotNull.class.getName()).equals(desc)) {
            classAnnotatedImplicit = true;
        }
        return super.visitAnnotation(desc, visible);
    }

    public boolean hasInstrumented() {
        for (final ThrowOnNullMethodVisitor methodVisitor : methodVisitors) {
            if (methodVisitor.hasInstrumented()) {
                return true;
            }
        }
        return false;
    }


}
