/**
 *    Copyright 2009-2016 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

/**
 * 泛型解析工具类，主要用来解析泛型指代的实际的类型
 * 只能处理二层继承，超过将无法解析
 *
 * @author Iwao AVE!
 */
public class TypeParameterResolver {

  /**
   * 解析字段类型
   * @return The field type as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type resolveFieldType(Field field, Type srcType) {
    //获取字段类型
    Type fieldType = field.getGenericType();
    //获取字段所属的类的class
    Class<?> declaringClass = field.getDeclaringClass();

    //解析实际类型
    return resolveType(fieldType, srcType, declaringClass);
  }

  /**
   * 解析方法返回类型
   * @return The return type of the method as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type resolveReturnType(Method method, Type srcType) {

    //获取方法的返回类型
    Type returnType = method.getGenericReturnType();

    //获取方法所属类的类型
    Class<?> declaringClass = method.getDeclaringClass();

    //解析实际类型
    return resolveType(returnType, srcType, declaringClass);
  }

  /**
   * 解析方法参数类型
   * @return The parameter types of the method as an array of {@link Type}s. If they have type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type[] resolveParamTypes(Method method, Type srcType) {

    //获取方法所有参数类型
    Type[] paramTypes = method.getGenericParameterTypes();

    //获取方法所属类的类型
    Class<?> declaringClass = method.getDeclaringClass();
    Type[] result = new Type[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
      //解析实际类型
      result[i] = resolveType(paramTypes[i], srcType, declaringClass);
    }
    return result;
  }

  /**
   * 解析Type类型并返回Type的自定义类型
   * @param type
   * @param srcType
   * @param declaringClass
   * @return
   */
  private static Type resolveType(Type type, Type srcType, Class<?> declaringClass) {
    if (type instanceof TypeVariable) {
      //解析TypeVariable类型
      return resolveTypeVar((TypeVariable<?>) type, srcType, declaringClass);
    } else if (type instanceof ParameterizedType) {
      //解析ParameterizedType类型
      return resolveParameterizedType((ParameterizedType) type, srcType, declaringClass);
    } else if (type instanceof GenericArrayType) {
      //解析GenericArrayType类型
      return resolveGenericArrayType((GenericArrayType) type, srcType, declaringClass);
    } else {
      //非泛型类型直接返回
      return type;
    }
  }

  /**
   * 解析泛型数组
   * @param genericArrayType
   * @param srcType
   * @param declaringClass
   * @return
   */
  private static Type resolveGenericArrayType(GenericArrayType genericArrayType, Type srcType, Class<?> declaringClass) {

    //去掉一层[]后的类型
    Type componentType = genericArrayType.getGenericComponentType();
    Type resolvedComponentType = null;
    if (componentType instanceof TypeVariable) {

      //如果去掉后为TypeVariable类型，则调用resolveTypeVar方法
      resolvedComponentType = resolveTypeVar((TypeVariable<?>) componentType, srcType, declaringClass);
    } else if (componentType instanceof GenericArrayType) {

      //如果去掉仍为GenericArrayType类型，则递归调用resolveGenericArrayType方法
      resolvedComponentType = resolveGenericArrayType((GenericArrayType) componentType, srcType, declaringClass);
    } else if (componentType instanceof ParameterizedType) {

      //如果去掉后为ParameterizedType类型，则调用resolveParameterizedType方法处理
      resolvedComponentType = resolveParameterizedType((ParameterizedType) componentType, srcType, declaringClass);
    }


    if (resolvedComponentType instanceof Class) {
      //如果处理后的结果为Class类型，则返回对应的Class的数组类型
      return Array.newInstance((Class<?>) resolvedComponentType, 0).getClass();
    } else {

      //否则包装为自定义的GenericArrayTypeImpl类型
      return new GenericArrayTypeImpl(resolvedComponentType);
    }
  }

  /**
   * 解析ParameterizedType类型，并返回自定义的ParameterizedTypeImpl类型
   * @param parameterizedType
   * @param srcType
   * @param declaringClass
   * @return
   */
  private static ParameterizedType resolveParameterizedType(ParameterizedType parameterizedType, Type srcType, Class<?> declaringClass) {
    //获取泛型中的外围类型（例如：List<Double>中的List）
    Class<?> rawType = (Class<?>) parameterizedType.getRawType();

    //获取泛型中的实际类型（例如：List<Double>中的Double）
    Type[] typeArgs = parameterizedType.getActualTypeArguments();

    //对泛型中的实际类型再次进行解析
    Type[] args = new Type[typeArgs.length];
    for (int i = 0; i < typeArgs.length; i++) {
      if (typeArgs[i] instanceof TypeVariable) {
        //解析TypeVariable类型
        args[i] = resolveTypeVar((TypeVariable<?>) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof ParameterizedType) {
        //解析ParameterizedType类型
        args[i] = resolveParameterizedType((ParameterizedType) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof WildcardType) {
        //解析WildcardType类型
        args[i] = resolveWildcardType((WildcardType) typeArgs[i], srcType, declaringClass);
      } else {
        //普通类型，直接返回
        args[i] = typeArgs[i];
      }
    }

    //返回自定以类型
    return new ParameterizedTypeImpl(rawType, null, args);
  }

  /**
   * 解析WildcardType类型（? extends Double/? super Double）
   * @param wildcardType
   * @param srcType
   * @param declaringClass
   * @return
   */
  private static Type resolveWildcardType(WildcardType wildcardType, Type srcType, Class<?> declaringClass) {
    //获取下限
    Type[] lowerBounds = resolveWildcardTypeBounds(wildcardType.getLowerBounds(), srcType, declaringClass);

    //获取上限
    Type[] upperBounds = resolveWildcardTypeBounds(wildcardType.getUpperBounds(), srcType, declaringClass);

    //包装成自定义的WildcardTypeImpl类型返回
    return new WildcardTypeImpl(lowerBounds, upperBounds);
  }

  /**
   * 解析上下限类型
   * @param bounds
   * @param srcType
   * @param declaringClass
   * @return
   */
  private static Type[] resolveWildcardTypeBounds(Type[] bounds, Type srcType, Class<?> declaringClass) {
    Type[] result = new Type[bounds.length];
    for (int i = 0; i < bounds.length; i++) {
      //根据上下限不同的类型，进行解析
      if (bounds[i] instanceof TypeVariable) {
        result[i] = resolveTypeVar((TypeVariable<?>) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof ParameterizedType) {
        result[i] = resolveParameterizedType((ParameterizedType) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof WildcardType) {
        result[i] = resolveWildcardType((WildcardType) bounds[i], srcType, declaringClass);
      } else {
        result[i] = bounds[i];
      }
    }
    return result;
  }

  /**
   * 解析泛型代表的实际的类型
   * @param typeVar 需要处理的类型
   * @param srcType 处理的方法/字段调用的类型，
   * @param declaringClass 处理的方法/字段 实际所属的类型，被srcType继承的类或实现的接口
   * @return
   */
  private static Type resolveTypeVar(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass) {
    Type result = null;
    Class<?> clazz = null;

    /**
     * 判断原类型的Class类型，如果为泛型，则获取实际类型（类型擦除后）
     */
    if (srcType instanceof Class) {
      clazz = (Class<?>) srcType;
    } else if (srcType instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) srcType;
      clazz = (Class<?>) parameterizedType.getRawType();
    } else {
      throw new IllegalArgumentException("The 2nd arg must be Class or ParameterizedType, but was: " + srcType.getClass());
    }

    /**
     * 如果原类型和实际归属类型一致则返回泛型的上限，如果不存在则返回Object.class
     */
    if (clazz == declaringClass) {
      Type[] bounds = typeVar.getBounds();
      if(bounds.length > 0) {
        return bounds[0];
      }
      return Object.class;
    }

    /**
     * 如果调用类和实际类不想等，则向上处理继承类或者实现的接口。
     */
    Type superclass = clazz.getGenericSuperclass();
    result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superclass);
    if (result != null) {
      return result;
    }

    Type[] superInterfaces = clazz.getGenericInterfaces();
    for (Type superInterface : superInterfaces) {
      result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superInterface);
      if (result != null) {
        return result;
      }
    }

    //如果父类或者实现的接口中都没有定义指定泛型指代的实际类，则返回Object.class
    return Object.class;
  }

  /**
   * 处理父类型/父接口
   * @param typeVar
   * @param srcType 实际调用的类型
   * @param declaringClass 方法/字段所属的类型
   * @param clazz 实际调用的类型进行类型擦除后的类型
   * @param superclass srcType描述符中定义的类型
   * @return
   */
  private static Type scanSuperTypes(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass, Class<?> clazz, Type superclass) {
    Type result = null;

    //判断父类型是否为泛型
    if (superclass instanceof ParameterizedType) {
      ParameterizedType parentAsType = (ParameterizedType) superclass;

      //获取superClass的实际类型
      Class<?> parentAsClass = (Class<?>) parentAsType.getRawType();

      //判断supper和declaringClass是否为同一个类型
      if (declaringClass == parentAsClass) {

        //获取superClass的实际类型的泛型
        Type[] typeArgs = parentAsType.getActualTypeArguments();

        //所属的类型描述符中的变量
        TypeVariable<?>[] declaredTypeVars = declaringClass.getTypeParameters();

        for (int i = 0; i < declaredTypeVars.length; i++) {
          //循环判断当前处理的类型是否属于所属的类型描述符中的变量
          if (declaredTypeVars[i] == typeVar) {

            //如果当前父类中的实际泛型还是TypeVariable，没有明确，则查询其子类中有没有明确指定
            if (typeArgs[i] instanceof TypeVariable) {

              //其子类中的所有泛型描述父
              TypeVariable<?>[] typeParams = clazz.getTypeParameters();
              for (int j = 0; j < typeParams.length; j++) {
                if (typeParams[j] == typeArgs[i]) {

                  //判断是否为ParameterizedType，则去实际代表的类型
                  if (srcType instanceof ParameterizedType) {
                    result = ((ParameterizedType) srcType).getActualTypeArguments()[j];
                  }
                  break;
                }
              }
            } else {
              //如果不是TypeVariable，直接取对应的类型
              result = typeArgs[i];
            }
          }
        }
      } else if (declaringClass.isAssignableFrom(parentAsClass)) {
        result = resolveTypeVar(typeVar, parentAsType, declaringClass);
      }
    } else if (superclass instanceof Class) {
      //如果为Class则判断superclass是否为declaringClass的父类，如果是，则递归解析
      if (declaringClass.isAssignableFrom((Class<?>) superclass)) {
        result = resolveTypeVar(typeVar, superclass, declaringClass);
      }
    }

    return result;
  }

  private TypeParameterResolver() {
    super();
  }

  /**
   * 自定以的ParameterizedType类型
   */
  static class ParameterizedTypeImpl implements ParameterizedType {
    private Class<?> rawType;

    private Type ownerType;

    private Type[] actualTypeArguments;

    public ParameterizedTypeImpl(Class<?> rawType, Type ownerType, Type[] actualTypeArguments) {
      super();
      this.rawType = rawType;
      this.ownerType = ownerType;
      this.actualTypeArguments = actualTypeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments;
    }

    @Override
    public Type getOwnerType() {
      return ownerType;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public String toString() {
      return "ParameterizedTypeImpl [rawType=" + rawType + ", ownerType=" + ownerType + ", actualTypeArguments=" + Arrays.toString(actualTypeArguments) + "]";
    }
  }

  /**
   * 自定义的WildcardType类型
   */
  static class WildcardTypeImpl implements WildcardType {
    private Type[] lowerBounds;

    private Type[] upperBounds;

    private WildcardTypeImpl(Type[] lowerBounds, Type[] upperBounds) {
      super();
      this.lowerBounds = lowerBounds;
      this.upperBounds = upperBounds;
    }

    @Override
    public Type[] getLowerBounds() {
      return lowerBounds;
    }

    @Override
    public Type[] getUpperBounds() {
      return upperBounds;
    }
  }

  /**
   * 自定义的GenericArrayType类型
   */
  static class GenericArrayTypeImpl implements GenericArrayType {
    private Type genericComponentType;

    private GenericArrayTypeImpl(Type genericComponentType) {
      super();
      this.genericComponentType = genericComponentType;
    }

    @Override
    public Type getGenericComponentType() {
      return genericComponentType;
    }
  }
}
