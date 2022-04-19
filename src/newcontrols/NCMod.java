package newcontrols;

import arc.Events;
import io.mnemotechnician.autoupdater.Updater;
import mindustry.game.EventType;
import mindustry.mod.Mod;

public class NCMod extends Mod {
	
	public NCMod() {
		NCVars.init();
		//NCSpying.init();
		
		Events.on(EventType.ClientLoadEvent.class, a -> {
			Updater.checkUpdates(this);
		});
	}
	
	@Override
	public void loadContent(){
		
	}
}
