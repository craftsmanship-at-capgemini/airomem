/* Copyright (c) Jarek Ratajski, Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0 */
package pl.setblack.airomem.core.builders;

import com.google.common.base.Optional;
import java.util.Date;
import org.prevayler.TransactionWithQuery;
import pl.setblack.airomem.core.ContextCommand;

import pl.setblack.airomem.core.PrevalanceContext;
import pl.setblack.airomem.core.Storable;
import pl.setblack.airomem.core.WriteChecker;

/**
 * Class used internally to wrap user Command.
 *
 * @author jarekr
 */
class InternalTransaction<T extends Storable, R> implements TransactionWithQuery<Optional<T>, R> {

    private static final long serialVersionUID = 1l;

    private final ContextCommand<T, R> cmd;

    InternalTransaction(ContextCommand<T, R> cmd) {
        this.cmd = cmd;
    }

    @Override
    public R executeAndQuery(Optional<T> p, Date date) {
        final PrevalanceContext ctx = createContext(date);
        WriteChecker.setContext(ctx);
        try {
            return cmd.execute(p.get(), ctx);
        } finally {
            WriteChecker.clearContext();
        }
    }

    PrevalanceContext createContext(Date date) {
        return new PrevalanceContext(date);
    }

}
