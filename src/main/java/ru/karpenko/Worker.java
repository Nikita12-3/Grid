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
                try {
                    byte[] baseDataBytes = jsonBase.getBytes(StandardCharsets.ISO_8859_1);
                    try (ByteArrayInputStream bis = new ByteArrayInputStream(baseDataBytes);
                         ObjectInputStream ois = new ObjectInputStream(bis)) {
                        Object baseData = ois.readObject();
                        baseDataCache.put(taskId, baseData);
                    }
                } catch (Exception e) {
                    System.err.println("Ошибка десериализации: " + e.getMessage());
                    baseDataCache.put(taskId, jsonBase);
                }
            }

            else
            {
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
        // Получаем все классы из JAR
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
            System.out.println("Вызов метода " + method.getName() + " с параметрами:");
            for (int i = 0; i < parameters.length; i++) {
                System.out.println("  Параметр " + i + ": " + parameters[i]);
            }
            // Убедитесь, что параметры соответствуют ожидаемым типам
            if (method.getParameterTypes()[0].isInstance(parameters[0])) {
                return method.invoke(this, parameters);
            } else {
                // Если параметр не соответствует ожидаемому типу, пробуем десериализовать его
                Object deserializedParam = objectMapper.readValue((String) parameters[0], method.getParameterTypes()[0]);
                return method.invoke(this, new Object[]{deserializedParam});
            }
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
