import com.sun.jdi.*;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** External debugger: stops only the initializer's thread, never the entire VM. */
public class TraceInitializer {
    static final String INITIALIZER = "org.eclipse.pde.internal.core.RequiredPluginsInitializer";
    static PrintWriter out;
    static Path directory;
    static long start = System.nanoTime();
    static int hits;
    static void log(String text) { out.println(Instant.now() + " " + ((System.nanoTime()-start)/1_000_000) + "ms " + text); out.flush(); }
    static String read(String name) {
        try { return Files.readString(directory.resolve(name)); } catch (IOException e) { return "absent"; }
    }
    static void stack(ThreadReference thread) {
        try {
            log("THREAD name=" + thread.name() + " status=" + thread.status() + " suspendCount=" + thread.suspendCount());
            for (StackFrame frame : thread.frames()) log("  at " + frame.location());
            if (thread.virtualMachine().canGetCurrentContendedMonitor()) {
                ObjectReference monitor = thread.currentContendedMonitor();
                if (monitor != null) log("  WAITING_MONITOR=" + monitor.referenceType().name() + " id=" + monitor.uniqueID());
            }
        } catch (Exception e) { log("STACK_ERROR " + e); }
    }
    static void sampleMain(VirtualMachine vm, ThreadReference hitThread) {
        log("SAMPLE ready=" + read("early-startup") + " heartbeat=" + read("heartbeat"));
        for (ThreadReference thread : vm.allThreads()) if (thread.name().equals("main")) {
            if (thread.equals(hitThread)) stack(thread);
            else {
                thread.suspend();
                try { stack(thread); } finally { thread.resume(); }
            }
        }
    }
    static void install(ReferenceType type, EventRequestManager manager) {
        if (!type.name().equals(INITIALIZER)) return;
        for (Method method : type.methodsByName("initialize")) {
            BreakpointRequest request = manager.createBreakpointRequest(method.location());
            request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            request.enable();
            log("BREAKPOINT_INSTALLED " + method + " " + method.location());
        }
    }
    public static void main(String[] args) throws Exception {
        directory = Path.of(args[1]); Files.createDirectories(directory);
        out = new PrintWriter(Files.newBufferedWriter(directory.resolve("debugger.log")));
        AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        Map<String, com.sun.jdi.connect.Connector.Argument> options = connector.defaultArguments();
        options.get("hostname").setValue("127.0.0.1"); options.get("port").setValue(args[0]);
        VirtualMachine vm = null;
        for (int i=0;i<100 && vm==null;i++) {
            try { vm = connector.attach(options); } catch (IOException e) { Thread.sleep(100); }
        }
        if (vm==null) throw new IOException("Could not attach to runtime");
        log("ATTACHED " + vm.version());
        EventRequestManager manager = vm.eventRequestManager();
        ClassPrepareRequest prepare = manager.createClassPrepareRequest();
        prepare.addClassFilter(INITIALIZER); prepare.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); prepare.enable();
        for (ReferenceType type : vm.classesByName(INITIALIZER)) install(type, manager);
        vm.resume();
        try {
            while (System.nanoTime()-start < 150_000_000_000L) {
                EventSet events = vm.eventQueue().remove(1000);
                if (events == null) continue;
                boolean ended = false;
                for (Event event : events) {
                    if (event instanceof ClassPrepareEvent cp) install(cp.referenceType(), manager);
                    if (event instanceof BreakpointEvent bp) {
                        ++hits;
                        log("INITIALIZER_HIT number=" + hits + " thread=" + bp.thread().name());
                        stack(bp.thread());
                        sampleMain(vm, bp.thread());
                        // The first initializer is held briefly to expose callers waiting on it.
                        // Later calls are only traced to avoid artificially serializing all work.
                        if (hits==1) {
                            for (int sample=0;sample<3;sample++) { Thread.sleep(1000); sampleMain(vm, bp.thread()); }
                        }
                        log("RELEASE_INITIALIZER " + hits);
                    }
                    if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) ended = true;
                }
                if (ended) break;
                events.resume();
            }
        } catch (VMDisconnectedException e) { log("VM_DISCONNECTED"); }
        finally {
            log("TOTAL_INITIALIZER_HITS=" + hits);
            Files.writeString(directory.resolve("initializer-hits"), Integer.toString(hits));
            try { vm.dispose(); } catch (VMDisconnectedException ignored) { }
            out.close();
        }
    }
}
