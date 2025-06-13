package com.netease.open.hrpc.backend;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MethodMatcher {
    private static final String TAG = "MethodMatcher-CGM";
    // 改进的 primitiveTypeAssignableFrom 方法
    private static boolean primitiveTypeAssignableFrom(Class<?> parType, Class<?> argType) {
        // 如果都不是基本类型或包装类，使用 isAssignableFrom
        if (!isPrimitiveOrWrapper(parType) && !isPrimitiveOrWrapper(argType)) {
            return parType.isAssignableFrom(argType);
        }

        // 将包装类转换为对应的基本类型
        parType = unwrap(parType);
        argType = unwrap(argType);

        // 如果目标类型不是基本类型，返回 false
        if (!parType.isPrimitive()) {
            return false;
        }

        // 基本类型宽化转换规则 (JLS §5.1.2)
        if (parType == double.class) {
            return argType == double.class || argType == float.class || argType == long.class ||
                   argType == int.class || argType == short.class || argType == byte.class;
        } else if (parType == float.class) {
            return argType == float.class || argType == long.class || argType == int.class ||
                   argType == short.class || argType == byte.class;
        } else if (parType == long.class) {
            return argType == long.class || argType == int.class || argType == short.class ||
                   argType == byte.class;
        } else if (parType == int.class) {
            return argType == int.class || argType == short.class || argType == byte.class;
        } else if (parType == short.class) {
            return argType == short.class || argType == byte.class;
        } else if (parType == byte.class) {
            return argType == byte.class;
        } else if (parType == char.class) {
            return argType == char.class;
        } else if (parType == boolean.class) {
            return argType == boolean.class;
        }

        return false;
    }

    // 判断是否是基本类型或包装类
    private static boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() ||
               type == Integer.class || type == Double.class || type == Float.class ||
               type == Long.class || type == Short.class || type == Byte.class ||
               type == Character.class || type == Boolean.class;
    }

    // 将包装类转换为对应的基本类型
    private static Class<?> unwrap(Class<?> type) {
        if (type == Integer.class) return int.class;
        if (type == Double.class) return double.class;
        if (type == Float.class) return float.class;
        if (type == Long.class) return long.class;
        if (type == Short.class) return short.class;
        if (type == Byte.class) return byte.class;
        if (type == Character.class) return char.class;
        if (type == Boolean.class) return boolean.class;
        return type;
    }

    // 改进的匹配逻辑
    public static java.lang.reflect.Method findMatchingMethod(List<java.lang.reflect.Method> _overloadMethods,
                                                             String _overloadMethodName,
                                                             List<Object> calcArguments) {
        java.lang.reflect.Method func = null;
        for (java.lang.reflect.Method m : _overloadMethods) {
            Class<?>[] paramTypes = m.getParameterTypes();
            if (paramTypes.length == calcArguments.size()) {
                boolean matched = true;
                Log.d(TAG, "method " + m.getName() + ", paramTypes.length=" + paramTypes.length);
                for (int j = 0; j < paramTypes.length; j++) {
                    Class<?> parType = paramTypes[j];
                    Class<?> argType = calcArguments.get(j).getClass();
                    // 调试日志，显示每个参数类型和匹配结果
                    Log.d(TAG, String.format("Method: %s, Param %d: parType=%s, argType=%s, isAssignableFrom=%b, primitiveAssignable=%b",
                            _overloadMethodName, j, parType.getSimpleName(), argType.getSimpleName(),
                            parType.isAssignableFrom(argType), primitiveTypeAssignableFrom(parType, argType)));
                    // 检查数组类型是否一致
                    if (parType.isArray() != argType.isArray()) {
                        Log.d(TAG, String.format("Method: %s, Param %d: Array type mismatch", _overloadMethodName, j));
                        matched = false;
                        break;
                    }
                    // 检查类型兼容性
                    if (!parType.isAssignableFrom(argType) && !primitiveTypeAssignableFrom(parType, argType)) {
                        Log.d(TAG, String.format("Method: %s, Param %d: Type mismatch", _overloadMethodName, j));
                        matched = false;
                        break;
                    }
                }
                if (matched) {
                    func = m;
                    break;
                }
            } else {
                Log.d(TAG, String.format("Method: %s, Parameter count mismatch: expected=%d, actual=%d",
                        _overloadMethodName, paramTypes.length, calcArguments.size()));
            }
        }
        _overloadMethods.clear();
        return func;
    }

    // 日志标签（根据你的环境定义）

    // 测试代码
    public static void test(String[] args) throws Exception {
        // 模拟目标类
        class Inputer {
            public void swipe(double x1, double y1, double x2, double y2, double duration) {
                System.out.println("Swiping...");
            }
        }

        // 模拟 _overloadMethods
        List<java.lang.reflect.Method> _overloadMethods = new ArrayList<>();
        _overloadMethods.add(Inputer.class.getDeclaredMethod("swipe", double.class, double.class, double.class, double.class, double.class));

        // 模拟 calcArguments（从 JSON 解析）
        List<Object> calcArguments = new ArrayList<>();
        calcArguments.add(0.5); // Double
        calcArguments.add(0.5555555555555556); // Double
        calcArguments.add(0.5); // Double
        calcArguments.add(0); // Integer (JSON 解析的 0)
        calcArguments.add(2); // Integer (JSON 解析的 2)

        // 测试
        String _overloadMethodName = "swipe";
        java.lang.reflect.Method func = findMatchingMethod(_overloadMethods, _overloadMethodName, calcArguments);
        System.out.println("Found method: " + (func != null ? func.getName() : "null"));
    }
}