package nhcm.bytecodevm.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class CLIRuntime
{
    private CLIRuntime()
    {
    }

    static void configure(boolean verbose, boolean quiet, Path requestedLogFile)
    {
        if (verbose && quiet)
        {
            throw new CLIException(CLIExitCodes.USAGE, "--verbose and --quiet cannot be used together");
        }
        Path logFile = requestedLogFile;
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context)
        {
            ch.qos.logback.classic.Logger root =
                    context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            var oldFileAppender = root.getAppender("CLI_FILE");
            if (oldFileAppender != null)
            {
                root.detachAppender(oldFileAppender);
                oldFileAppender.stop();
            }
            root.setLevel(quiet ? Level.ERROR : verbose ? Level.DEBUG : Level.INFO);
            if (logFile == null)
            {
                return;
            }
            Path absolute = logFile.toAbsolutePath().normalize();
            try
            {
                Path directory = absolute.getParent();
                if (directory != null)
                {
                    Files.createDirectories(directory);
                }
            }
            catch (IOException exception)
            {
                throw new CLIException(
                        CLIExitCodes.GENERATION,
                        "Cannot create log directory: " + absolute,
                        exception);
            }

            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern("[%d{yyyy-MM-dd HH:mm:ss}] [%thread/%level] [%logger{0}]: %msg%n");
            encoder.start();

            FileAppender<ILoggingEvent> appender = new FileAppender<>();
            appender.setContext(context);
            appender.setName("CLI_FILE");
            appender.setFile(absolute.toString());
            appender.setAppend(false);
            appender.setEncoder(encoder);
            appender.start();

            root.addAppender(appender);
        }
    }
}
