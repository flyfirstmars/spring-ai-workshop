package ge.jar.springaiworkshop.voyagermate.shell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VoyagerMateShellConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(VoyagerMateShellConfiguration.class);

    @Bean
    public ApplicationRunner shellExceptionHandler(ApplicationContext context) {
        return args -> {
            Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
                logger.error("Uncaught exception in shell thread {}: ", thread.getName(), exception);
                System.err.println("Application Error: " + exception.getMessage());
                System.err.println("Please check the logs for more details");
            });
        };
    }
}
