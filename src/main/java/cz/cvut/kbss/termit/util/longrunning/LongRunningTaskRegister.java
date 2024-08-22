package cz.cvut.kbss.termit.util.longrunning;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface LongRunningTaskRegister {
    @NotNull
    Collection<LongRunningTask> getTasks();
}
