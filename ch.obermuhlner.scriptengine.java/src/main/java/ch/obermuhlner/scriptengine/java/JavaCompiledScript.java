package ch.obermuhlner.scriptengine.java;

import ch.obermuhlner.scriptengine.java.execution.ExecutionStrategy;

import javax.script.*;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * The compiled Java script created by a {@link JavaScriptEngine}.
 */
public class JavaCompiledScript extends CompiledScript {
    private final JavaScriptEngine engine;
    private final Class<?> instanceClass;
    private final Object instance;
    private ExecutionStrategy executionStrategy;

    /**
     * Construct a {@link JavaCompiledScript}.
     *
     * @param engine the {@link JavaScriptEngine} that compiled this script
     * @param instanceClass the compiled {@link Class}
     * @param instance the instance of the compiled {@link Class} or {@code null}
     *                 if no instance was created and only static methods will be called
     *                 by the the {@link ExecutionStrategy}.
     * @param executionStrategy the {@link ExecutionStrategy}
     */
    JavaCompiledScript(JavaScriptEngine engine, Class<?> instanceClass, Object instance, ExecutionStrategy executionStrategy) {
        this.engine = engine;
        this.instanceClass = instanceClass;
        this.instance = instance;
        this.executionStrategy = executionStrategy;
    }

    /**
     * Returns the compiled {@link Class}.
     *
     * @return the compiled {@link Class}.
     */
    public Class<?> getInstanceClass() {
        return instanceClass;
    }

    /**
     * Returns the instance of the compiled {@link Class}.
     *
     * @return the instance of the compiled {@link Class} or {@code null}
     *         if no instance was created and only static methods will be called
     *         by the the {@link ExecutionStrategy}.
     */
    public Object getInstance() {
        return instance;
    }

    /**
     * Sets the {@link ExecutionStrategy} to be used when evaluating the compiled class instance.
     *
     * @param executionStrategy the {@link ExecutionStrategy}
     */
    public void setExecutionStrategy(ExecutionStrategy executionStrategy) {
        this.executionStrategy = executionStrategy;
    }

    @Override
    public ScriptEngine getEngine() {
        return engine;
    }

    @Override
    public Object eval(ScriptContext context) throws ScriptException {
        Bindings globalBindings = context.getBindings(ScriptContext.GLOBAL_SCOPE);
        Bindings engineBindings = context.getBindings(ScriptContext.ENGINE_SCOPE);

        pushVariables(globalBindings, engineBindings);
        Object result = executionStrategy.execute(instance);
        pullVariables(globalBindings, engineBindings);

        return result;
    }

    private void pushVariables(Bindings globalBindings, Bindings engineBindings) throws ScriptException {
        Map<String, Object> mergedBindings = mergeBindings(globalBindings, engineBindings);

        for (Map.Entry<String, Object> entry : mergedBindings.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();

            try {
                Field field = instanceClass.getField(name);
                field.set(instance, value);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new ScriptException(e);
            }
        }
    }

    private void pullVariables(Bindings globalBindings, Bindings engineBindings) throws ScriptException {
        for (Field field : instanceClass.getFields()) {
            try {
                String name = field.getName();
                Object value = field.get(instance);
                setBindingsValue(globalBindings, engineBindings, name, value);
            } catch (IllegalAccessException e) {
                throw new ScriptException(e);
            }
        }
    }

    private void setBindingsValue(Bindings globalBindings, Bindings engineBindings, String name, Object value) {
        if (!engineBindings.containsKey(name) && globalBindings.containsKey(name)) {
            globalBindings.put(name, value);
        } else {
            engineBindings.put(name, value);
        }
    }

    private Map<String, Object> mergeBindings(Bindings... bindingsToMerge) {
        Map<String, Object> variables = new HashMap<>();

        for (Bindings bindings : bindingsToMerge) {
            if (bindings != null) {
                for (Map.Entry<String, Object> globalEntry : bindings.entrySet()) {
                    variables.put(globalEntry.getKey(), globalEntry.getValue());
                }
            }
        }

        return variables;
    }
}
