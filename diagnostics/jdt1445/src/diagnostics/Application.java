package diagnostics;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.core.runtime.Platform;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.internal.TextListener;

public class Application implements IApplication {
    @Override
    public Object start(IApplicationContext context) {
        for (String name : new String[] {"org.eclipse.jdt.core", "org.eclipse.text", "org.eclipse.core.runtime"}) {
            System.out.println("BUNDLE " + name + " " + Platform.getBundle(name).getVersion());
        }
        JUnitCore runner = new JUnitCore();
        runner.addListener(new TextListener(System.out));
        Result result = runner.run(OptionsCacheConsistencyTest.class);
        for (Failure failure : result.getFailures()) {
            System.out.println("DIAGNOSTIC_FAILURE " + failure.getTestHeader());
            System.out.println(failure.getTrace());
        }
        System.out.printf("DIAGNOSTIC_RESULT tests=%d failures=%d ignored=%d%n",
                result.getRunCount(), result.getFailureCount(), result.getIgnoreCount());
        return result.wasSuccessful() ? EXIT_OK : Integer.valueOf(1);
    }

    @Override
    public void stop() {}
}
