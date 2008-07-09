package axiom.objectmodel.dom;

import axiom.framework.core.Application;

public abstract class DbMigrator {
    public abstract void migrateDb(Application app) throws Exception;
}