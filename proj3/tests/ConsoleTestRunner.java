import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.PrintWriter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/** CLI runner for JUnit 5 tests (the repo has no Maven/Gradle and no
 *  junit-platform-console jar). Usage: ConsoleTestRunner <ClassName>... */
public class ConsoleTestRunner {
    public static void main(String[] args) {
        LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request();
        for (String cn : args) {
            builder.selectors(selectClass(cn));
        }
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(builder.build());
        TestExecutionSummary s = listener.getSummary();
        PrintWriter pw = new PrintWriter(System.out, true);
        s.printTo(pw);
        pw.flush();
        if (s.getTotalFailureCount() > 0) {
            System.exit(1);
        }
    }
}
