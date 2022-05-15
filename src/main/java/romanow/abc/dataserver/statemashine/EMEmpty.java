package romanow.abc.dataserver.statemashine;

import romanow.abc.core.entity.Entity;
import romanow.abc.dataserver.DataServer;

public class EMEmpty implements I_ServerTransition{
    @Override
    public String onTransition(DataServer db, Entity entity) {
        return "";
    }
}
