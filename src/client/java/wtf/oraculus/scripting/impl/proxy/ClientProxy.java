package wtf.oraculus.scripting.impl.proxy;

import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.UnknownModuleException;
import wtf.oraculus.client.feature.module.repository.ModuleRepository;
import wtf.oraculus.utility.misc.chat.ChatUtility;

public class ClientProxy {

    public void print(final Object o) {
        ChatUtility.print(o);
    }

    public Module getModule(final String ID) {
        try {
            return OraculusClient.getInstance().getModuleRepository().getModule(ID);
        } catch (UnknownModuleException e) {
            throw new RuntimeException(e);
        }
    }

}
