package ru.karpenko;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
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
@RequestMapping("/worker")
public class Worker {
    private static final String JAR_PATH = "solver.jar";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final RestTemplate restTemplate = new RestTemplate();
    private static String workerId;

    public static void main(String[] args) {
        SpringApplication.run(Worker.class, args);
    }

    @Bean
    public CommandLineRunner registerWorker(ServletWebServerApplicationContext context) {
        return args -> {
            int port = context.getWebServer().getPort();
            String workerUrl = "http://localhost:" + port + "/worker";
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.postForObject(
                    "http://localhost:8083/distributor/registerWorker",
                    workerUrl,
                    String.class
            );
            workerId = response;
            System.out.println("Регистрация воркера: " + response);
        };
    }

    @PostMapping("/solveSubtask")
    public byte[] solveSubtask(
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String subtaskId,
            @RequestParam(required = false) MultipartFile jar,
            @RequestParam(required = false) MultipartFile baseData,
            @RequestParam(required = false) MultipartFile subTaskData,
            @RequestParam(required = false, defaultValue = "http://localhost:8083") String managerAddress) {

        String currentTaskId = taskId != null ? taskId : subtaskId;

        System.out.println("\n=== Начало обработки задачи " + currentTaskId + " ===");
        System.out.println("managerAddress: " + managerAddress);

        sendTaskAccepted(subtaskId, managerAddress);

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

                sendResult(currentTaskId, subtaskId, workerId, resultBytes, managerAddress);

                System.out.println("=== Задача " + currentTaskId + " выполнена успешно ===\n");
                return resultBytes;
            } else {
                System.out.println("JAR файл не получен");
                return new byte[0];
            }
        } catch (Exception e) {
            System.err.println("\n=== ОШИБКА В РАБОТЕ ВОРКЕРА ===");
            System.err.println("Ошибка при обработке задачи " + currentTaskId + ": " + e.getMessage());
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

    private void sendTaskAccepted(String subTaskId, String managerAddress) {
        try {
            restTemplate.postForObject(
                    managerAddress + "/distributor/taskAccepted/" + subTaskId,
                    null,
                    String.class
            );
            System.out.println("Подзадача №" + subTaskId + " воркером принята");
        } catch (Exception e) {
            System.err.println("Ошибка при отправке подтверждения: " + e.getMessage());
        }
    }

    private void sendResult(String taskId, String subtaskId, String workerId, byte[] resultData, String managerAddress) {
        try {
            // Создаём JSON-объект с нужными полями
            Map<String, Object> resultJson = new HashMap<>();
            resultJson.put("taskId", taskId);
            resultJson.put("subtaskId", subtaskId);
            resultJson.put("workerId", workerId);
            resultJson.put("result", new String(resultData, StandardCharsets.UTF_8)); // или Base64.encode(resultData)

            // Настраиваем заголовки
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Создаём HTTP-запрос
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(resultJson, headers);

            // Отправляем запрос
            ResponseEntity<String> response = restTemplate.exchange(
                    managerAddress + "/distributor/result",
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            System.out.println("Результат для подзадачи №" + subtaskId + " отправлен на распределитель");
        } catch (Exception e) {
            System.err.println("Ошибка при отправке результата: " + e.getMessage());
            e.printStackTrace();
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
        final Object[] resultHolder = new Object[1];

        Thread invokeThread = new Thread(() -> {
            try {
                Object instance = method.getDeclaringClass().newInstance();
                System.out.println("Вызов метода " + method.getName() + " с параметрами:");
                for (int i = 0; i < parameters.length; i++) {
                    System.out.println("  Параметр " + i + ": " + parameters[i]);
                }

                Object result = method.invoke(instance, parameters);
                resultHolder[0] = result;

                if (result == null) {
                    System.err.println("Предупреждение: метод вернул null");
                    resultHolder[0] = new ArrayList<>();
                }
            } catch (Exception e) {
                System.err.println("Ошибка при вызове метода: " + e.getMessage());
            }
        });

        invokeThread.start();
        invokeThread.join();

        return resultHolder[0];
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