package ru.krastti.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

/** HTML-страницы ошибок браузера; JSON-ответы API обрабатываются отдельно. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class BrowserErrorViewResolver implements ErrorViewResolver {

    private final String template;

    public BrowserErrorViewResolver() throws IOException {
        template = new ClassPathResource("browser-error.html").getContentAsString(StandardCharsets.UTF_8);
    }

    /** Выбирает описание по HTTP-коду, не используя сообщения исключений и параметры запроса. */
    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        ErrorPage page = pageFor(status.value());
        // При ERROR-диспетчеризации сохраняется исходный метод: POST нельзя повторять кнопкой обновления.
        boolean retry = "GET".equals(request.getMethod())
                && (status == HttpStatus.INTERNAL_SERVER_ERROR || status == HttpStatus.BAD_GATEWAY
                || status == HttpStatus.SERVICE_UNAVAILABLE || status == HttpStatus.GATEWAY_TIMEOUT);
        String html = template.replace("{{status}}", Integer.toString(status.value()))
                .replace("{{title}}", page.title())
                .replace("{{description}}", page.description())
                .replace("{{recommendation}}", page.recommendation())
                .replace("{{loginHidden}}", status == HttpStatus.UNAUTHORIZED ? "" : "hidden")
                .replace("{{retryHidden}}", retry ? "" : "hidden");
        return new ModelAndView(new ErrorPageView(status.value(), html));
    }

    private static ErrorPage pageFor(int status) {
        return switch (status) {
            case 400 -> new ErrorPage("Некорректный запрос",
                    "Сервер не смог обработать запрос из-за некорректных данных.",
                    "Проверьте адрес страницы и введённые значения.");
            case 401 -> new ErrorPage("Требуется авторизация",
                    "Для открытия этой страницы необходимо войти в учётную запись.",
                    "Перейдите ко входу администратора и повторите действие.");
            case 403 -> new ErrorPage("Доступ запрещён",
                    "Недостаточно прав для открытия страницы или выполнения действия.",
                    "Вернитесь на главную. Если доступ должен быть открыт, обратитесь к администратору.");
            case 404 -> new ErrorPage("Страница не найдена",
                    "По этому адресу нет страницы или запрошенного ресурса.",
                    "Проверьте адрес или перейдите на главную страницу ServiceOil.");
            case 405 -> new ErrorPage("Способ обращения не поддерживается",
                    "Этот адрес не поддерживает способ, которым был отправлен запрос.",
                    "Вернитесь на сайт и воспользуйтесь его ссылками и формами.");
            case 409 -> new ErrorPage("Конфликт операций",
                    "Действие конфликтует с текущим состоянием данных или другой выполняющейся операцией.",
                    "Дождитесь завершения текущего действия и повторите попытку из панели.");
            case 422 -> new ErrorPage("Данные отклонены",
                    "Запрос прочитан, но его данные не соответствуют требованиям операции.",
                    "Вернитесь к форме и исправьте значения перед повторной отправкой.");
            case 500 -> new ErrorPage("Внутренняя ошибка",
                    "Во время обработки запроса произошла ошибка сервиса.",
                    "Повторите попытку позже. Если ошибка сохраняется, обратитесь к администратору.");
            case 502 -> new ErrorPage("Некорректный ответ внешнего сервиса",
                    "Внешний сервис вернул ответ, который не удалось обработать.",
                    "Подождите и повторите попытку позже.");
            case 503 -> new ErrorPage("Сервис временно недоступен",
                    "Сервис или необходимые ему данные сейчас недоступны.",
                    "Подождите немного и повторите попытку.");
            case 504 -> new ErrorPage("Истекло время ожидания",
                    "Внешний сервис не успел ответить за отведённое время.",
                    "Повторите попытку позже, когда внешний сервис восстановит работу.");
            default -> new ErrorPage("Не удалось открыть страницу",
                    "При обработке запроса возникла ошибка. Её HTTP-код указан выше.",
                    "Вернитесь на главную страницу и попробуйте продолжить работу оттуда.");
        };
    }

    private record ErrorPage(String title, String description, String recommendation) {}

    /** Записывает готовый HTML напрямую, без перенаправления и повторного вызова контроллеров. */
    private record ErrorPageView(int status, String html) implements View {
        @Override
        public String getContentType() {
            return MediaType.TEXT_HTML_VALUE;
        }

        @Override
        public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            response.setStatus(status);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(getContentType());
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write(html);
        }
    }
}
