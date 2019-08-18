package ch.obermuhlner.scriptengine.java;

import ch.obermuhlner.scriptengine.java.constructor.ConstructorStrategy;
import ch.obermuhlner.scriptengine.java.constructor.DefaultConstructorStrategy;
import ch.obermuhlner.scriptengine.java.execution.AutoExecutionStrategy;
import ch.obermuhlner.scriptengine.java.execution.ExecutionStrategy;
import ch.obermuhlner.scriptengine.java.execution.ExecutionStrategyFactory;

import javax.script.*;
import javax.tools.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JavaScriptEngine implements ScriptEngine, Compilable {

    private ConstructorStrategy constructorStrategy = new DefaultConstructorStrategy();
    private ExecutionStrategyFactory executionStrategyFactory = (clazz) -> new AutoExecutionStrategy(clazz);

    private ScriptContext context = new SimpleScriptContext();

    public void setConstructorStrategy(ConstructorStrategy constructorStrategy) {
        this.constructorStrategy = constructorStrategy;
    }

    public void setExecutionStrategyFactory(ExecutionStrategyFactory executionStrategyFactory) {
        this.executionStrategyFactory = executionStrategyFactory;
    }

    @Override
    public ScriptContext getContext() {
        return context;
    }

    @Override
    public void setContext(ScriptContext context) {
        Objects.requireNonNull(context);
        this.context = context;
    }

    @Override
    public Bindings createBindings() {
        return new SimpleBindings();
    }

    @Override
    public Bindings getBindings(int scope) {
        return context.getBindings(scope);
    }

    @Override
    public void setBindings(Bindings bindings, int scope) {
        context.setBindings(bindings, scope);
    }

    @Override
    public void put(String key, Object value) {
        getBindings(ScriptContext.ENGINE_SCOPE).put(key, value);
    }

    @Override
    public Object get(String key) {
        return getBindings(ScriptContext.ENGINE_SCOPE).get(key);
    }

    @Override
    public Object eval(Reader reader) throws ScriptException {
        return eval(readScript(reader));
    }

    @Override
    public Object eval(String script) throws ScriptException {
        return eval(script, context);
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        return eval(readScript(reader), context);
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
        return eval(script, context.getBindings(ScriptContext.ENGINE_SCOPE));
    }

    @Override
    public Object eval(Reader reader, Bindings bindings) throws ScriptException {
        return eval(readScript(reader), bindings);
    }

    @Override
    public Object eval(String script, Bindings bindings) throws ScriptException {
        CompiledScript compile = compile(script);

        return compile.eval(bindings);
    }

    @Override
    public CompiledScript compile(Reader reader) throws ScriptException {
        return compile(readScript(reader));
    }

    @Override
    public CompiledScript compile(String script) throws ScriptException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

        String simpleClassName = "Script";
        String fullClassName = simpleClassName; // example.Script
        String fileName = simpleClassName + ".class";

        JavaStringObject testSource = new JavaStringObject(simpleClassName, script);

        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, null, null, Arrays.asList(testSource));
        if (!task.call()) {
            String message = diagnostics.getDiagnostics().stream()
                    .map(d -> d.toString())
                    .collect(Collectors.joining("\n"));
            throw new ScriptException(message);
        }

        DynamicClassLoader classLoader = new DynamicClassLoader(fullClassName, Path.of(fileName), JavaScriptEngine.class.getClassLoader());
        try {
            Class<?> clazz = classLoader.loadClass(fullClassName);
            Object instance = constructorStrategy.construct(clazz);
            ExecutionStrategy executionStrategy = executionStrategyFactory.create(clazz);
            return new JavaCompiledScript(this, clazz, instance, executionStrategy);
        } catch (ClassNotFoundException e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return new JavaScriptEngineFactory();
    }

    private String readScript(Reader reader) throws ScriptException {
        try {
            StringBuilder s = new StringBuilder();
            BufferedReader bufferedReader = new BufferedReader(reader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                s.append(line);
                s.append("\n");
            }
            return s.toString();
        } catch (IOException e) {
            throw new ScriptException(e);
        }
    }
}