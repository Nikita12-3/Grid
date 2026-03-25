package ru.karpenko;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

//@Service
public class WorkerService {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String JAR_PATH = "solver.jar";
    private RestTemplate restTemplate = new RestTemplate();

    public byte[] solveSubtask(
            String taskId,
            MultipartFile jar,
            MultipartFile baseData,
            MultipartFile subTaskData,
            String managerAddress) {

        System.out.println("\n=== Начало обработки задачи " + taskId + " ===");
        System.out.println("managerAddress: " + managerAddress);

        Path tempDir = null;

        try {
            if (jar != null && !jar.isEmpty()) {
                System.out.println("Получен JAR файл размером: " + jar.getSize() + " байт");

                tempDir = Files.createTempDirectory("solver");
                Path tempJarPath = tempDir.resolve("solver.jar");

                try (InputStream inputStream = jar.getInputStream()) {
                    Files.copy(inputStream, tempJarPath, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("JAR файл скопирован в: " + tempJarPath);
                }

                URLClassLoader classLoader = loadJarClassLoader(tempJarPath.toString());

                Object baseDataObj = null;
                Object subTaskObj = null;

                if (baseData != null && !baseData.isEmpty()) {
                    try (InputStream is = baseData.getInputStream();
                         ObjectInputStream ois = new ObjectInputStream(is)) {
                        baseDataObj = ois.readObject();
                        System.out.println("Исходные данные успешно десериализованы");
                    }
                }

                if (subTaskData != null && !subTaskData.isEmpty()) {
                    try (InputStream is = subTaskData.getInputStream();
                         ObjectInputStream ois = new ObjectInputStream(is)) {
                        subTaskObj = ois.readObject();
                        System.out.println("Подзадача успешно десериализована");
                    }
                }

                Method mainMethod = findMethodWithMainAnnotation(classLoader);
                System.out.println("Найден метод для выполнения: " + mainMethod.getName());

                Object[] parameters = getMethodParameters(mainMethod, subTaskObj);
                System.out.println("Получено " + parameters.length + " параметров для метода");

                System.out.println("Выполнение метода...");
                Object result = invokeMainMethod(mainMethod, parameters);
                System.out.println("Метод выполнен успешно");

                byte[] resultBytes = serializeResult(result);
                System.out.println("Результат сериализован, размер: " + resultBytes.length + " байт");

                System.out.println("=== Задача " + taskId + " выполнена успешно ===\n");
                return resultBytes;
            } else {
                System.out.println("JAR файл не получен");
                return new byte[0];
            }
        } catch (Exception e) {
            System.err.println("\n=== ОШИБКА В РАБОТЕ ВОРКЕРА ===");
            System.err.println("Ошибка при обработке задачи " + taskId + ": " + e.getMessage());
            e.printStackTrace();
            System.err.println("=============================\n");
            return new byte[0];
        } finally {
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                            .sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    System.err.println("Ошибка при удалении файла: " + path + ": " + e.getMessage());
                                }
                            });
                } catch (IOException e) {
                    System.err.println("Ошибка при удалении временных файлов: " + e.getMessage());
                }
            }
        }
    }

    // Остальные методы остаются без изменений
    public void sendTaskAccepted(String taskId, String managerAddress) {
        restTemplate.postForObject(
                managerAddress + "/distributor/taskAccepted/" + taskId,
                null,
                String.class
        );
    }

    public void sendResult(String taskId, byte[] result, String managerAddress) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(result, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    managerAddress + "/distributor/result/" + taskId,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );
            System.out.println("Результат отправлен: " + response.getStatusCode());
        } catch (Exception e) {
            System.err.println("Ошибка при отправке результата: " + e.getMessage());
            throw e;
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
        java.lang.reflect.Field field = subTaskObj.getClass().getDeclaredField(paramName);
        field.setAccessible(true);
        return field.get(subTaskObj);
    }

    private URLClassLoader loadJarClassLoader(String jarPath) throws Exception {
        Path jarFilePath = Paths.get(jarPath);
        if (!Files.exists(jarFilePath)) {
            System.err.println("JAR файл не найден по пути: " + jarPath);
            throw new FileNotFoundException("JAR файл не найден: " + jarPath);
        }

        System.out.println("Загрузка классов из JAR файла: " + jarPath);
        URL jarUrl = jarFilePath.toUri().toURL();
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
            Object instance = method.getDeclaringClass().newInstance();
            System.out.println("Вызов метода " + method.getName() + " с параметрами:");
            for (int i = 0; i < parameters.length; i++) {
                System.out.println("  Параметр " + i + ": " + parameters[i]);
            }

            Object result = method.invoke(instance, parameters);

            if (result == null) {
                System.err.println("Предупреждение: метод вернул null");
                return new ArrayList<>();
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
}
