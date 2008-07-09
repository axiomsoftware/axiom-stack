package axiom.objectmodel.dom;

import java.io.File;

import axiom.framework.core.Application;
import axiom.objectmodel.DatabaseException;

public interface EmbeddedDbConvertor {

    public void convert(Application app, File dbhome) throws Exception;
    
}