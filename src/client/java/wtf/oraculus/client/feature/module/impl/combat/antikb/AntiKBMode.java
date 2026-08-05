package wtf.oraculus.client.feature.module.impl.combat.antikb;

import wtf.oraculus.client.feature.module.property.impl.mode.ModuleMode;

public abstract class AntiKBMode extends ModuleMode<AntiKBModule> {
    protected AntiKBMode(AntiKBModule module) {
        super(module);
    }

    public String getSuffix() {
        return this.getEnumValue().toString();
    }
}
