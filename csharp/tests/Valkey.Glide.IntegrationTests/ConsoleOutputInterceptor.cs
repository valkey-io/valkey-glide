public class ConsoleOutputInterceptor : IDisposable
{
    private readonly TextWriter _originalOut;
    private readonly StringWriter _stringWriter;
    private readonly ITestOutputHelper _output;

    public ConsoleOutputInterceptor(ITestOutputHelper output)
    {
        _originalOut = Console.Out;
        _stringWriter = new StringWriter();
        Console.SetOut(_stringWriter);
        _output = output;
    }

    public void Dispose()
    {
        Console.SetOut(_originalOut);
        _output.WriteLine(_stringWriter.ToString());
    }
}
