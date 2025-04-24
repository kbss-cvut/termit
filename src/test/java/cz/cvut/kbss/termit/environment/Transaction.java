/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.environment;

import jakarta.annotation.Nonnull;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Utility class for executing operations in transaction.
 * <p>
 * It is required for proper JOPA functionality in tests. The tests cannot function in one transaction like regular
 * JPA-based Spring tests, since JOPA is not able to add pending changes to query results.
 */
public class Transaction {

    /**
     * Helper method for executing the specified portion of code in a transaction.
     * <p>
     * Since JOPA does not understand SPARQL queries, any method using a query will not be able to see uncommitted
     * transactional changes. So the whole test cannot run in a single transaction, as is common in regular Spring
     * testing.
     * <p>
     * Instead, we need to perform methods which change the state of the storage in transactions, so that the changes
     * are really committed into the storage.
     *
     * @param txManager Transaction manager to use to run the transactional code
     * @param procedure Code to execute
     */
    public static void execute(PlatformTransactionManager txManager, Runnable procedure) {
        new TransactionTemplate(txManager).execute(new TransactionCallbackWithoutResult() {

            @Override
            protected void doInTransactionWithoutResult(@Nonnull TransactionStatus transactionStatus) {
                procedure.run();
            }
        });
    }

    /**
     * Helper method for executing the specified portion of code in a read-only transaction.
     * <p>
     * This method allows executing transactional behavior in a read-only mode.
     *
     * @param txManager Transaction manager to use to run the transactional code
     * @param procedure Code to execute
     */
    public static void executeReadOnly(PlatformTransactionManager txManager, Runnable procedure) {
        final TransactionTemplate transaction = new TransactionTemplate(txManager);
        transaction.setReadOnly(true);
        new TransactionTemplate(txManager).execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(@Nonnull TransactionStatus transactionStatus) {
                procedure.run();
            }
        });
    }
}
