using System.Runtime.CompilerServices;
using BenchmarkDotNet.Attributes;

namespace Valkey.Glide.Benchmark.Micro;

public class BuilderBenchmarks
{
    private FancyService _service = null!;

    [GlobalSetup]
    public void Setup() => _service = new FancyService();

    [Benchmark]
    public void BuilderStructToString() => _ = new BuilderStruct().A("a").B("b").C("c").ToString();

    [Benchmark]
    public void BuilderRecordToString() => _ = new BuilderRecord().A("a").B("b").C("c").ToString();

    [Benchmark]
    public void BuilderClassToString() => _ = new BuilderClass().A("a").B("b").C("c").ToString();

    [Benchmark]
    public void InterfaceIndirectionBuilderStruct() => _service.DoSomething(new BuilderStruct().A("a").B("b").C("c"));

    [Benchmark]
    public void InterfaceIndirectionBuilderRecord() => _service.DoSomething(new BuilderRecord().A("a").B("b").C("c"));

    [Benchmark]
    public void InterfaceIndirectionBuilderClass() => _service.DoSomething(new BuilderClass().A("a").B("b").C("c"));


    public class FancyService
    {
        public void DoSomething<T>(T t) where T : IBuilder => t.Execute(this);
    }

    public interface IBuilder
    {
        void Execute(FancyService service);
    }

    public record struct BuilderStruct : IBuilder
    {
        private string? _a;
        private string? _b;
        private string? _c;

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public BuilderStruct A(string a) => this with {_a = a};

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public BuilderStruct B(string b) => this with {_b = b};

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public BuilderStruct C(string c) => this with {_c = c};

        public override string ToString()
        {
            return string.Concat(_a, _b, _c);
        }

        public void Execute(FancyService service)
        {
            _ = ToString();
        }
    }

    public record BuilderRecord : IBuilder
    {
        private string? _a;
        private string? _b;
        private string? _c;

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public BuilderRecord A(string a) => this with {_a = a};

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public BuilderRecord B(string b) => this with {_b = b};

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public BuilderRecord C(string c) => this with {_c = c};

        public override string ToString()
        {
            return string.Concat(_a, _b, _c);
        }

        public void Execute(FancyService service)
        {
            _ = ToString();
        }
    }

    public class BuilderClass : IBuilder
    {
        private string? _a;
        private string? _b;
        private string? _c;

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public BuilderClass A(string a)
        {
            _a = a;
            return this;
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public BuilderClass B(string b)
        {
            _b = b;
            return this;
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public BuilderClass C(string c)
        {
            _c = c;
            return this;
        }

        public override string ToString()
        {
            return string.Concat(_a, _b, _c);
        }

        public void Execute(FancyService service)
        {
            _ = ToString();
        }
    }
}
