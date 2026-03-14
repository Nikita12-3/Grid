package ru.karpenko;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.*;
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
import java.util.jar.JarOutputStream;

@SpringBootApplication
@RestController
public class Worker {
    private static final String JAR_PATH = "solver.jar";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<String, Object> baseDataCache = new HashMap<>();

    @PostMapping("/solveSubtask")
    public byte[] solveSubtask(
            @RequestParam String taskId,
            @RequestParam(required = false) MultipartFile jar,
            @RequestParam(required = false) String jsonBase,
            @RequestParam String jsonSubTask,
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

            // 2. Сохраняем исходные данные в кэш
            if (jsonBase != null && !jsonBase.isEmpty()) {
                System.out.println("Получены исходные данные: " + jsonBase.length() + " символов");
                System.out.println("Первые 100 символов: " + jsonBase.substring(0, Math.min(100, jsonBase.length())));

                try {
                    // Пробуем десериализовать как JSON
                    Object baseData = objectMapper.readTree(jsonBase);
                    System.out.println("Исходные данные успешно десериализованы как JSON");
                    baseDataCache.put(taskId, baseData);
                } catch (JsonProcessingException e) {
                    System.err.println("Ошибка десериализации JSON: " + e.getMessage());
                    // Пробуем как простой текст
                    baseDataCache.put(taskId, jsonBase);
                    System.out.println("Исходные данные сохранены как текст");
                }
            } else {
                System.out.println("Исходные данные не получены");
            }

            // 3. Логируем данные подзадачи
            System.out.println("Получена подзадача: " + jsonSubTask.length() + " символов");
            System.out.println("Первые 100 символов подзадачи: " + jsonSubTask.substring(0, Math.min(100, jsonSubTask.length())));

            // 4. Загружаем классы из JAR
            URLClassLoader classLoader = loadJarClassLoader();

            // 5. Находим метод с аннотацией @MainAnnotation
            Method mainMethod = findMethodWithMainAnnotation(classLoader);
            System.out.println("Найден метод для выполнения: " + mainMethod.getName());

            // 6. Получаем параметры для метода
            Object[] parameters = getMethodParameters(mainMethod, taskId, jsonSubTask);
            System.out.println("Получено " + parameters.length + " параметров для метода");

            // 7. Выполняем метод
            System.out.println("Выполнение метода...");
            Object result = invokeMainMethod(mainMethod, parameters);
            System.out.println("Метод выполнен успешно");

            // 8. Сериализуем и возвращаем результат
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

    private Object[] getMethodParameters(Method method, String taskId, String jsonSubTask) throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();
        System.out.println("Метод принимает " + paramTypes.length + " параметров");

        if (paramTypes.length == 2) {
            Object baseData = baseDataCache.get(taskId);
            if (baseData == null) {
                throw new RuntimeException("Исходные данные не найдены для taskId: " + taskId);
            }
            System.out.println("Используем 2 параметра: baseData и jsonSubTask");
            return new Object[]{baseData, jsonSubTask};
        } else if (paramTypes.length == 1) {
            System.out.println("Используем 1 параметр: jsonSubTask");
            return new Object[]{jsonSubTask};
        } else {
            System.err.println("Метод принимает " + paramTypes.length + " параметров, но должно быть 1 или 2");
            for (int i = 0; i < paramTypes.length; i++) {
                System.err.println("Параметр " + i + ": " + paramTypes[i].getName());
            }
            throw new RuntimeException("Метод должен принимать 1 или 2 параметра");
        }
    }

    private URLClassLoader loadJarClassLoader() throws Exception {
        Path jarPath = Paths.get(JAR_PATH);
        if (!Files.exists(jarPath)) {
            System.err.println("JAR файл не найден по пути: " + JAR_PATH);
            // Создаем пустой JAR файл если его нет
            try (FileOutputStream fos = new FileOutputStream(jarPath.toFile())) {
                try (JarOutputStream jos = new JarOutputStream(fos)) {
                    jos.putNextEntry(new JarEntry("META-INF/"));
                    jos.closeEntry();
                }
            }
            throw new FileNotFoundException("JAR файл не найден, создан пустой файл: " + JAR_PATH);
        }

        System.out.println("Загрузка классов из JAR файла: " + JAR_PATH);
        URL jarUrl = jarPath.toUri().toURL();
        return new URLClassLoader(new URL[]{jarUrl}, getClass().getClassLoader());
    }

    private Method findMethodWithMainAnnotation(URLClassLoader classLoader) throws Exception {
        // Временное решение - возвращаем фиктивный метод
        Method dummyMethod = Worker.class.getMethod("dummyMethod");
        System.out.println("Используем фиктивный метод: " + dummyMethod.getName());
        return dummyMethod;
    }

    // Фиктивный метод для временного решения
    public String dummyMethod(String input) {
        System.out.println("Выполнение фиктивного метода с параметром: " + input);
        return "Результат фиктивного метода: " + input;
    }

    private Object invokeMainMethod(Method method, Object[] parameters) throws Exception {
        try {
            System.out.println("Вызов метода " + method.getName() + " с параметрами:");
            for (int i = 0; i < parameters.length; i++) {
                System.out.println("  Параметр " + i + ": " + parameters[i]);
            }
            return method.invoke(this, parameters);
        } catch (Exception e) {
            System.err.println("Ошибка при вызове метода: " + e.getMessage());
            throw new RuntimeException("Ошибка выполнения метода: " + e.getCause().getMessage(), e);
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
