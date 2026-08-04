package nhcm.bytecodevm.cli;

final class CLIException extends RuntimeException
{
    private final int exitCode;

    CLIException(int exitCode, String message)
    {
        super(message);
        this.exitCode = exitCode;
    }

    CLIException(int exitCode, String message, Throwable cause)
    {
        super(message, cause);
        this.exitCode = exitCode;
    }

    int exitCode()
    {
        return exitCode;
    }
}
