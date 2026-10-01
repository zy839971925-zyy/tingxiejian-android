import com.example.tingxiejian.SessionState;
import com.example.tingxiejian.SessionState.State;

/** Behavioral checks use the same pure state machine shipped in the app. */
public final class SessionStateCheck {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        SessionState start = new SessionState("id", "file", "录音", State.RUNNING, "", 10, 10);
        SessionState finalizing = start.transition(State.FINALIZING, "final", 20);
        check(finalizing.state == State.FINALIZING, "RUNNING advances to FINALIZING");
        check(start.state == State.RUNNING, "snapshots stay immutable");
        SessionState polish = finalizing.transition(State.POLISHING, "polish", 30);
        check(polish.state == State.POLISHING, "FINALIZING advances to POLISHING");
        check(polish.transition(State.RUNNING, "late progress", 40) == polish,
                "late streaming cannot regress a later stage");
        check(polish.transition(State.FINALIZING, "late final", 40) == polish,
                "late finalizer cannot regress polishing");
        SessionState done = polish.transition(State.DONE, "saved", 40);
        check(done.state == State.DONE && done.isTerminal(), "DONE is terminal");
        check(done.createdAt == 10 && done.updatedAt == 40 && done.name.equals("录音"),
                "state changes preserve identity and creation time");
        for (State terminal : new State[]{State.DONE, State.CANCELLED, State.INTERRUPTED, State.FAILED}) {
            SessionState ended = start.transition(terminal, terminal.name(), 50);
            check(ended.state == terminal && ended.isTerminal(), terminal + " is reachable from RUNNING");
            for (State target : State.values())
                check(ended.transition(target, "late callback", 60) == ended,
                        terminal + " absorbs late " + target);
        }
        check(start.transition(State.CANCELLED, "stop", 20).transition(State.DONE, "late result", 30)
                .state == State.CANCELLED, "success after cancellation stays CANCELLED");
        check(start.transition(State.FINALIZING, "", 5).updatedAt == 10,
                "backward wall clock never moves timestamps backward");
        System.out.println("PASS: session state (" + checks + " assertions)");
    }
}
