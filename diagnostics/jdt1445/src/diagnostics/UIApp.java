package diagnostics;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.IWorkbenchConfigurer;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class UIApp implements IApplication {
    private int exitCode = 2;

    @Override
    public Object start(IApplicationContext context) {
        Display display = PlatformUI.createDisplay();
        try {
            PlatformUI.createAndRunWorkbench(display, new WorkbenchAdvisor() {
                @Override
                public String getInitialWindowPerspectiveId() {
                    return "org.eclipse.jdt.ui.JavaPerspective";
                }
                @Override
                public void initialize(IWorkbenchConfigurer configurer) {
                    super.initialize(configurer);
                    configurer.setSaveAndRestore(false);
                }
                @Override
                public void postStartup() {
                    display.asyncExec(() -> {
                        try {
                            JUnitCore runner = new JUnitCore();
                            runner.addListener(new TextListener(System.out));
                            Result result = runner.run(SaveParticipantIntegrationTest.class);
                            for (Failure failure : result.getFailures()) {
                                System.out.println("UI_DIAGNOSTIC_FAILURE " + failure.getTestHeader());
                                System.out.println(failure.getTrace());
                            }
                            System.out.printf("UI_DIAGNOSTIC_RESULT tests=%d failures=%d ignored=%d%n",
                                    result.getRunCount(), result.getFailureCount(), result.getIgnoreCount());
                            exitCode = result.wasSuccessful() ? 0 : 1;
                        } finally {
                            PlatformUI.getWorkbench().close();
                        }
                    });
                }
            });
            return Integer.valueOf(exitCode);
        } finally {
            display.dispose();
        }
    }
    @Override
    public void stop() {}
}
