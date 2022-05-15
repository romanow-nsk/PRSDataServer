package romanow.abc.dataserver.statemashine;

import romanow.abc.core.entity.Entity;
import romanow.abc.core.entity.StateEntity;
import romanow.abc.dataserver.DataServer;

public interface I_ServerTransition {
    public String onTransition(DataServer db, StateEntity entity);
}
