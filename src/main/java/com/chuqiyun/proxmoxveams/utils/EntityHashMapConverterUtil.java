package com.chuqiyun.proxmoxveams.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mryunqi
 * @date 2023/6/30
 */
public class EntityHashMapConverterUtil {
    /**
     * 将实体类转换为HashMap
     *
     * @param entity 实体类
     * @param <T>    实体类类型
     * @return HashMap
     * @throws IllegalAccessException
     */
    public static <T> HashMap<Object, Object> convertToHashMap(T entity) throws IllegalAccessException {
        HashMap<Object, Object> hashMap = new HashMap<>();
        Class<?> clazz = entity.getClass();
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            Object value = field.get(entity);
            hashMap.put(field.getName(), value);
        }

        return hashMap;
    }

    /**
     * 将HashMap转换为实体类
     *
     * @param hashMap HashMap
     * @param clazz   实体类类型
     * @param <T>     实体类类型
     * @return 实体类
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public static <T> T convertToEntity(Map<Object, Object> hashMap, Class<T> clazz) throws IllegalAccessException, InstantiationException {
        T entity;
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            entity = constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to create an instance of the entity class.", e);
        }

        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            Object value = hashMap.get(field.getName());
            field.set(entity, value);
        }

        return entity;
    }

    /*public static <T> T convertToEntity(HashMap<Object, Object> hashMap, Class<T> clazz) throws IllegalAccessException, InstantiationException {
        T entity = clazz.newInstance();
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            Object value = hashMap.get(field.getName());
            field.set(entity, value);
        }

        return entity;
    }*/
}
