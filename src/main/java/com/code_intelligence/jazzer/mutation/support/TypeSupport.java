/*
 * Copyright 2023 Code Intelligence GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.code_intelligence.jazzer.mutation.support;

import static com.code_intelligence.jazzer.mutation.support.Preconditions.require;
import static com.code_intelligence.jazzer.mutation.support.Preconditions.requireNonNullElements;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedTypeVariable;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TypeSupport {
  private TypeSupport() {}

  public static boolean isPrimitive(AnnotatedType type) {
    return isPrimitive(type.getType());
  }

  public static boolean isPrimitive(Type type) {
    if (!(type instanceof Class<?>) ) {
      return false;
    }
    return ((Class<?>) type).isPrimitive();
  }

  public static boolean isInheritable(Annotation annotation) {
    return annotation.annotationType().getDeclaredAnnotation(Inherited.class) != null;
  }

  /**
   * Returns {@code type} as a {@code Class<T>} if it is a subclass of T, otherwise empty.
   *
   * <p>This function also returns an empty {@link Optional} for more complex (e.g. parameterized)
   * types.
   */
  public static <T> Optional<Class<T>> asSubclassOrEmpty(AnnotatedType type, Class<T> superclass) {
    if (!(type.getType() instanceof Class<?>) ) {
      return Optional.empty();
    }

    Class<?> actualClazz = (Class<?>) type.getType();
    if (!superclass.isAssignableFrom(actualClazz)) {
      return Optional.empty();
    }

    return Optional.of(actualClazz.asSubclass(superclass));
  }

  public static AnnotatedType asAnnotatedType(Class<?> clazz) {
    requireNonNull(clazz);
    return new AnnotatedType() {
      @Override
      public Type getType() {
        return clazz;
      }

      @Override
      public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return annotatedElementGetAnnotation(this, annotationClass);
      }

      @Override
      public Annotation[] getAnnotations() {
        // No directly present annotations, look for inheritable present annotations on the
        // superclass.
        if (clazz.getSuperclass() == null) {
          return new Annotation[0];
        }
        return stream(clazz.getSuperclass().getAnnotations())
            .filter(TypeSupport::isInheritable)
            .toArray(Annotation[] ::new);
      }

      @Override
      public Annotation[] getDeclaredAnnotations() {
        // No directly present annotations.
        return new Annotation[0];
      }

      @Override
      public String toString() {
        return annotatedTypeToString(this);
      }

      @Override
      public int hashCode() {
        throw new UnsupportedOperationException(
            "hashCode() is not supported as its behavior isn't specified");
      }

      @Override
      public boolean equals(Object obj) {
        throw new UnsupportedOperationException(
            "equals() is not supported as its behavior isn't specified");
      }
    };
  }

  public static AnnotatedParameterizedType withTypeArguments(
      AnnotatedType type, AnnotatedType... typeArguments) {
    requireNonNull(type);
    requireNonNullElements(typeArguments);
    require(typeArguments.length > 0);
    require(!(type instanceof AnnotatedParameterizedType || type instanceof AnnotatedTypeVariable
                || type instanceof AnnotatedWildcardType || type instanceof AnnotatedArrayType),
        "only plain annotated types are supported");
    require(
        ((Class<?>) type.getType()).getEnclosingClass() == null, "nested classes aren't supported");

    ParameterizedType filledRawType = new ParameterizedType() {
      @Override
      public Type[] getActualTypeArguments() {
        return stream(typeArguments).map(AnnotatedType::getType).toArray(Type[] ::new);
      }

      @Override
      public Type getRawType() {
        return type.getType();
      }

      @Override
      public Type getOwnerType() {
        // We require the class is top-level.
        return null;
      }

      @Override
      public String toString() {
        return getRawType()
            + stream(getActualTypeArguments()).map(Type::toString).collect(joining(",", "<", ">"));
      }

      @Override
      public boolean equals(Object obj) {
        if (!(obj instanceof ParameterizedType)) {
          return false;
        }
        ParameterizedType other = (ParameterizedType) obj;
        return getRawType().equals(other.getRawType()) && null == other.getOwnerType()
            && Arrays.equals(getActualTypeArguments(), other.getActualTypeArguments());
      }

      @Override
      public int hashCode() {
        throw new UnsupportedOperationException(
            "hashCode() is not supported as its behavior isn't specified");
      }
    };

    return new AnnotatedParameterizedType() {
      @Override
      public AnnotatedType[] getAnnotatedActualTypeArguments() {
        return Arrays.copyOf(typeArguments, typeArguments.length);
      }

      // @Override as of Java 9
      @SuppressWarnings("Since15")
      public AnnotatedType getAnnotatedOwnerType() {
        return null;
      }

      @Override
      public Type getType() {
        return filledRawType;
      }

      @Override
      public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return type.getAnnotation(annotationClass);
      }

      @Override
      public Annotation[] getAnnotations() {
        return type.getAnnotations();
      }

      @Override
      public Annotation[] getDeclaredAnnotations() {
        return type.getDeclaredAnnotations();
      }

      @Override
      public String toString() {
        return annotatedTypeToString(this);
      }

      @Override
      public boolean equals(Object obj) {
        if (!(obj instanceof AnnotatedParameterizedType)) {
          return false;
        }
        AnnotatedParameterizedType other = (AnnotatedParameterizedType) obj;
        // Can't call getAnnotatedOwnerType on Java 8, but since our own implementation always
        // returns null, comparing getType().getOwnerType() via getType() is sufficient.
        return Objects.equals(getType(), other.getType())
            && Arrays.equals(
                getAnnotatedActualTypeArguments(), other.getAnnotatedActualTypeArguments())
            && Arrays.equals(getAnnotations(), other.getAnnotations());
      }

      @Override
      public int hashCode() {
        throw new UnsupportedOperationException(
            "hashCode() is not supported as its behavior isn't specified");
      }
    };
  }

  public static AnnotatedType withExtraAnnotations(
      AnnotatedType base, Annotation... extraAnnotations) {
    requireNonNull(base);
    requireNonNullElements(extraAnnotations);

    if (extraAnnotations.length == 0) {
      return base;
    }

    require(!(base instanceof AnnotatedTypeVariable || base instanceof AnnotatedWildcardType),
        "Adding annotations to AnnotatedTypeVariables or AnnotatedWildcardTypes is not supported");
    if (base instanceof AnnotatedArrayType) {
      return new AugmentedArrayType((AnnotatedArrayType) base, extraAnnotations);
    } else if (base instanceof AnnotatedParameterizedType) {
      return new AugmentedParameterizedType((AnnotatedParameterizedType) base, extraAnnotations);
    } else {
      return new AugmentedAnnotatedType(base, extraAnnotations);
    }
  }

  private static String annotatedTypeToString(AnnotatedType annotatedType) {
    String annotations =
        stream(annotatedType.getAnnotations()).map(Annotation::toString).collect(joining(" "));
    if (annotations.isEmpty()) {
      return annotatedType.getType().toString();
    } else {
      return annotations + " " + annotatedType.getType();
    }
  }

  private static <T extends Annotation> T annotatedElementGetAnnotation(
      AnnotatedElement element, Class<T> annotationClass) {
    requireNonNull(annotationClass);
    return stream(element.getAnnotations())
        .filter(annotation -> annotationClass.equals(annotation.annotationType()))
        .findFirst()
        .map(annotationClass::cast)
        .orElse(null);
  }

  public static Optional<Class<?>> findFirstParentIfClass(AnnotatedType type, Class<?>... parents) {
    if (!(type.getType() instanceof Class<?>) ) {
      return Optional.empty();
    }
    Class<?> clazz = (Class<?>) type.getType();
    return Stream.of(parents).filter(parent -> parent.isAssignableFrom(clazz)).findFirst();
  }

  public static Optional<AnnotatedType> parameterTypeIfParameterized(
      AnnotatedType type, Class<?> expectedParent) {
    if (!(type instanceof AnnotatedParameterizedType)) {
      return Optional.empty();
    }
    Class<?> clazz = (Class<?>) ((ParameterizedType) type.getType()).getRawType();
    if (!expectedParent.isAssignableFrom(clazz)) {
      return Optional.empty();
    }

    AnnotatedType[] typeArguments =
        ((AnnotatedParameterizedType) type).getAnnotatedActualTypeArguments();
    if (typeArguments.length != 1) {
      return Optional.empty();
    }
    AnnotatedType elementType = typeArguments[0];
    if (!(elementType.getType() instanceof ParameterizedType)
        && !(elementType.getType() instanceof Class)) {
      return Optional.empty();
    }

    return Optional.of(elementType);
  }

  private static class AugmentedArrayType
      extends AugmentedAnnotatedType implements AnnotatedArrayType {
    private AugmentedArrayType(AnnotatedArrayType base, Annotation[] extraAnnotations) {
      super(base, extraAnnotations);
    }

    @Override
    public AnnotatedType getAnnotatedGenericComponentType() {
      return ((AnnotatedArrayType) base).getAnnotatedGenericComponentType();
    }

    // @Override as of Java 9
    @SuppressWarnings("Since15")
    public AnnotatedType getAnnotatedOwnerType() {
      throw new UnsupportedOperationException("Not implemented");
    }
  }

  private static class AugmentedParameterizedType
      extends AugmentedAnnotatedType implements AnnotatedParameterizedType {
    private AugmentedParameterizedType(
        AnnotatedParameterizedType base, Annotation[] extraAnnotations) {
      super(base, extraAnnotations);
    }

    @Override
    public AnnotatedType[] getAnnotatedActualTypeArguments() {
      return ((AnnotatedParameterizedType) base).getAnnotatedActualTypeArguments();
    }

    // @Override as of Java 9
    @SuppressWarnings("Since15")
    public AnnotatedType getAnnotatedOwnerType() {
      throw new UnsupportedOperationException("Not implemented");
    }
  }

  private static class AugmentedAnnotatedType implements AnnotatedType {
    protected final AnnotatedType base;
    private final Annotation[] extraAnnotations;

    private AugmentedAnnotatedType(AnnotatedType base, Annotation[] extraAnnotations) {
      this.base = requireNonNull(base);
      this.extraAnnotations = checkExtraAnnotations(base, extraAnnotations);
    }

    private static Annotation[] checkExtraAnnotations(
        AnnotatedElement base, Annotation[] extraAnnotations) {
      requireNonNullElements(extraAnnotations);
      Set<Class<? extends Annotation>> existingAnnotationTypes =
          stream(base.getDeclaredAnnotations())
              .map(Annotation::annotationType)
              .collect(Collectors.toCollection(HashSet::new));
      for (Annotation annotation : extraAnnotations) {
        boolean added = existingAnnotationTypes.add(annotation.annotationType());
        require(added, annotation + " already directly present on " + base);
      }
      return extraAnnotations;
    }

    @Override
    public Type getType() {
      return base.getType();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
      return annotatedElementGetAnnotation(this, annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
      Set<Class<? extends Annotation>> directlyPresentTypes =
          stream(getDeclaredAnnotations()).map(Annotation::annotationType).collect(toSet());
      return Stream
          .concat(
              // Directly present annotations.
              stream(getDeclaredAnnotations()),
              // Present but not directly present annotations, never added by us as we don't add
              // annotations to the super class.
              stream(base.getAnnotations())
                  .filter(
                      annotation -> !directlyPresentTypes.contains(annotation.annotationType())))
          .toArray(Annotation[] ::new);
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
      return Stream.concat(stream(base.getDeclaredAnnotations()), stream(extraAnnotations))
          .toArray(Annotation[] ::new);
    }

    @Override
    public String toString() {
      return annotatedTypeToString(this);
    }

    @Override
    public boolean equals(Object obj) {
      throw new UnsupportedOperationException(
          "equals() is not supported as its behavior isn't specified");
    }

    @Override
    public int hashCode() {
      throw new UnsupportedOperationException(
          "hashCode() is not supported as its behavior isn't specified");
    }
  }
}
