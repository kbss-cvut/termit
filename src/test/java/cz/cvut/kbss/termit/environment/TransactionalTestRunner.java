/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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

import cz.cvut.kbss.jopa.model.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

public abstract class TransactionalTestRunner {

    @Autowired
    protected PlatformTransactionManager txManager;

    protected void transactional(Runnable procedure) {
        Transaction.execute(txManager, procedure);
    }

    protected void readOnlyTransactional(Runnable procedure) {
        Transaction.executeReadOnly(txManager, procedure);
    }

    protected void enableRdfsInference(EntityManager em) {
        transactional(() -> Environment.addModelStructureForRdfsInference(em));
    }
}
