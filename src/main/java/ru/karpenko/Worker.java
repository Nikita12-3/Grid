package ru.karpenko;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@SpringBootApplication
@RestController
public class Worker {
    private static final String JAR_PATH = "solver.jar";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/solveSubtask")
    public byte[] solveSubtask(
            @RequestParam String taskId,
            @RequestParam(required = false) MultipartFile jar,
            @RequestParam(required = false) MultipartFile baseData,
            @RequestParam(required = false) MultipartFile subTaskData,
            @RequestParam(required = false, defaultValue = "localhost:8082") String managerAddress) {

        System.out.println("\n=== Начало обработки задачи " + taskId + " ===");
        System.out.println("managerAddress: " + managerAddress);

        try {
            // 1. Сохраняем JAR если он пришел
            if (jar != null && !jar.isEmpty()) {
                System.out.println("Получен JAR файл размером: " + jar.getSize() + " байт");
                try (InputStream inputStream = jar.getInputStream()) {
                    Files.copy(inputStream, Paths.get(JAR_PATH), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("JAR файл сохранен по пути: " + JAR_PATH);
                }
            } else {
                System.out.println("JAR файл не получен");
            }

            // 2. Десериализуем baseData и subTaskData
            Object baseDataObj = null;
            Object subTaskObj = null;

            if (baseData != null && !baseData.isEmpty()) {
                try (InputStream is = baseData.getInputStream();
                     ObjectInputStream ois = new ObjectInputStream(is)) {
                    baseDataObj = ois.readObject();
                    System.out.println("Исходные данные успешно десериализованы: " + baseDataObj);
                }
            }

            if (subTaskData != null && !subTaskData.isEmpty()) {
                try (InputStream is = subTaskData.getInputStream();
                     ObjectInputStream ois = new ObjectInputStream(is)) {
                    subTaskObj = ois.readObject();
                    System.out.println("Подзадача успешно десериализована: " + subTaskObj);
                }
            }

            // 3. Загружаем классы из JAR
            URLClassLoader classLoader = loadJarClassLoader();

            // 4. Находим метод с аннотацией @MainAnnotation
            Method mainMethod = findMethodWithMainAnnotation(classLoader);
            System.out.println("Найден метод для выполнения: " + mainMethod.getName());

            // 5. Получаем параметры для метода
            Object[] parameters = getMethodParameters(mainMethod, subTaskObj);
            System.out.println("Получено " + parameters.length + " параметров для метода");

            // 6. Выполняем метод
            System.out.println("Выполнение метода...");
            Object result = invokeMainMethod(mainMethod, parameters);
            System.out.println("Метод выполнен успешно");

            // 7. Сериализуем и возвращаем результат
            byte[] resultBytes = serializeResult(result);
            System.out.println("Результат сериализован, размер: " + resultBytes.length + " байт");

            System.out.println("=== Задача " + taskId + " выполнена успешно ===\n");
            return resultBytes;
        } catch (Exception e) {
            System.err.println("\n=== ОШИБКА В РАБОТЕ ВОРКЕРА ===");
            System.err.println("Ошибка при обработке задачи " + taskId + ": " + e.getMessage());
            e.printStackTrace();
            System.err.println("=============================\n");
            return new byte[0];
        }
    }

    private Object[] getMethodParameters(Method method, Object subTaskObj) throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();
        System.out.println("Метод принимает " + paramTypes.length + " параметров");

        Object[] parameters = new Object[paramTypes.length];
        Annotation[][] paramAnnotations = method.getParameterAnnotations();

        for (int i = 0; i < paramTypes.length; i++) {
            for (Annotation annotation : paramAnnotations[i]) {
                if (annotation instanceof Param) {
                    String paramName = ((Param) annotation).value();
                    Object paramValue = getParamValue(subTaskObj, paramName);
                    parameters[i] = paramValue;
                }
            }
        }

        return parameters;
    }

    private Object getParamValue(Object subTaskObj, String paramName) throws Exception {
        // Используем рефлексию для получения значения поля из объекта subTaskObj
        java.lang.reflect.Field field = subTaskObj.getClass().getDeclaredField(paramName);
        field.setAccessible(true);
        return field.get(subTaskObj);
    }

    private URLClassLoader loadJarClassLoader() throws Exception {
        Path jarPath = Paths.get(JAR_PATH);
        if (!Files.exists(jarPath)) {
            System.err.println("JAR файл не найден по пути: " + JAR_PATH);
            throw new FileNotFoundException("JAR файл не найден: " + JAR_PATH);
        }

        System.out.println("Загрузка классов из JAR файла: " + JAR_PATH);
        URL jarUrl = jarPath.toUri().toURL();
        return new URLClassLoader(new URL[]{jarUrl}, getClass().getClassLoader());
    }

    private Method findMethodWithMainAnnotation(URLClassLoader classLoader) throws Exception {
        Enumeration<JarEntry> entries = new JarFile(JAR_PATH).entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().endsWith(".class")) {
                String className = entry.getName()
                        .replace("/", ".")
                        .replace(".class", "");
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(MainAnnotation.class)) {
                            return method;
                        }
                    }
                } catch (ClassNotFoundException e) {
                    System.err.println("Не удалось загрузить класс: " + className);
                }
            }
        }
        throw new RuntimeException("Метод с аннотацией @MainAnnotation не найден");
    }

    private Object invokeMainMethod(Method method, Object[] parameters) throws Exception {
        try {
            // Создаем экземпляр класса
            Object instance = method.getDeclaringClass().newInstance();

            System.out.println("Вызов метода " + method.getName() + " с параметрами:");
            for (int i = 0; i < parameters.length; i++) {
                System.out.println("  Параметр " + i + ": " + parameters[i]);
            }

            // Вызываем метод
            Object result = method.invoke(instance, parameters);

            if (result == null) {
                System.err.println("Предупреждение: метод вернул null");
                return new ArrayList<>(); // Возвращаем пустой список по умолчанию
            }
            return result;
        } catch (Exception e) {
            System.err.println("Ошибка при вызове метода: " + e.getMessage());
            throw e;
        }
    }

    private byte[] serializeResult(Object result) throws Exception {
        try {
            System.out.println("Сериализация результата: " + result);
            return objectMapper.writeValueAsBytes(result);
        } catch (Exception e) {
            System.err.println("Ошибка сериализации: " + e.getMessage());
            throw e;
        }
    }

    public static void main(String[] args) {
        System.out.println("Запуск воркера...");
        SpringApplication.run(Worker.class, args);
    }
}
